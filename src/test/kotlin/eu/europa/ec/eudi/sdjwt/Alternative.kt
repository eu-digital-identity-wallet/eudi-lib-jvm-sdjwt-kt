package eu.europa.ec.eudi.sdjwt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*


typealias Claims<JE> = Map<String, JE>

sealed interface SdJwtElement<JE> {
    data class Plain<JE>(val claims: Claims<JE>) : SdJwtElement<JE>
    data class FlatDisclosed<JE>(val claims: Claims<JE>) : SdJwtElement<JE>
    data class StructuredDisclosed<JE>(val claimName: String, val elements: Set<SdJwtElement<JE>>) : SdJwtElement<JE>
}

data class DisclosedClaims<JO>(val ds: Set<Disclosure>, val claims: JO) {

    fun <JO2> mapClaims(f: (JO) -> JO2): DisclosedClaims<JO2> = DisclosedClaims(ds, f(claims))

    companion object {

        fun <JO> monoid(claimsMonoid: Monoid<JO>): Monoid<DisclosedClaims<JO>> =
            object : Monoid<DisclosedClaims<JO>> {
                override val empty: DisclosedClaims<JO> = DisclosedClaims(emptySet(), claimsMonoid.empty)

                override fun invoke(
                    a: DisclosedClaims<JO>,
                    b: DisclosedClaims<JO>
                ): DisclosedClaims<JO> =
                    DisclosedClaims(a.ds + b.ds, claimsMonoid(a.claims, b.claims))

            }
    }
}

interface Monoid<V> : (V, V) -> V {
    val empty: V
    override operator fun invoke(a: V, b: V): V
}

class SdJwtElementDicloser<JE, JO>(
    additionOfClaims: Monoid<JO>,
    private val createObjectFromClaims: (Claims<JE>) -> JO,
    private val nestClaims: (String, JO) -> JO,
    private val hashClaims: (Claims<JE>) -> Pair<Set<Disclosure>, Set<HashedDisclosure>>,
    private val createObjectsFromHashes: (Set<HashedDisclosure>) -> JO
) {

    private val additionOfDisclosedClaims: Monoid<DisclosedClaims<JO>> = DisclosedClaims.monoid(additionOfClaims)
    private operator fun DisclosedClaims<JO>.plus(that: DisclosedClaims<JO>) =
        additionOfDisclosedClaims(this, that)



    fun discloseElement(element: SdJwtElement<JE>): Result<DisclosedClaims<JO>> = runCatching {
        when (element) {
            is SdJwtElement.Plain ->
                additionOfDisclosedClaims.empty
                    .copy(claims = createObjectFromClaims(element.claims))

            is SdJwtElement.FlatDisclosed ->
                if (element.claims.isEmpty()) additionOfDisclosedClaims.empty
                else {
                    val (ds, hs) = hashClaims(element.claims)
                    DisclosedClaims(ds, createObjectsFromHashes(hs))
                }

            is SdJwtElement.StructuredDisclosed ->
                disclose(element.elements)
                    .getOrThrow()
                    .mapClaims { nestClaims(element.claimName, it) }
        }
    }

    fun disclose(es: Set<SdJwtElement<JE>>): Result<DisclosedClaims<JO>> = runCatching {

        tailrec fun discloseAccumulating(xs: Set<SdJwtElement<JE>>, accumulated: DisclosedClaims<JO>): DisclosedClaims<JO> =
            if (xs.isEmpty()) accumulated
            else {
                val x = xs.first()
                val disclosedClaims = discloseElement(x).getOrThrow()
                discloseAccumulating(xs - x, accumulated + disclosedClaims)
            }

        discloseAccumulating(es, additionOfDisclosedClaims.empty)
    }


}

object KotlinxSupport {

    private val AdditionOfClams: Monoid<JsonObject> = object : Monoid<JsonObject> {
        override val empty: JsonObject = JsonObject(emptyMap())
        override fun invoke(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a + b)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun interpreter(hashClaims: (Claims<JsonElement>) -> Pair<Set<Disclosure>, Set<HashedDisclosure>>): SdJwtElementDicloser<JsonElement, JsonObject> =
        SdJwtElementDicloser(
            additionOfClaims = AdditionOfClams,
            createObjectFromClaims = { cs -> JsonObject(cs) },
            nestClaims = { cn, jo -> buildJsonObject { put(cn, jo) } },
            hashClaims = hashClaims,
            createObjectsFromHashes = { hs -> buildJsonObject { putJsonArray("_sd") { addAll(hs.map { it.value }) } } }

        )

    private fun DisclosedClaims<JsonObject>.addHashAlg(h: HashAlgorithm) =
        if (ds.isEmpty()) this
        else copy(claims = JsonObject(claims + ("_sd_alg" to JsonPrimitive(h.alias))))

    fun disclose(
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        numOfDecoys: Int = 0,
        sdJwt: Set<SdJwtElement<JsonElement>>
    ): Result<DisclosedClaims<JsonObject>> {

        val hashClaims: (Claims<JsonElement>) -> Pair<Set<Disclosure>, Set<HashedDisclosure>> = { cs ->
            DisclosuresAndHashes.make(hashAlgorithm, saltProvider, cs, numOfDecoys).run {
                disclosures to hashes
            }
        }

        return interpreter(hashClaims).disclose(sdJwt).map { it.addHashAlg(hashAlgorithm) }
    }


    fun sdJwt(action: SdJwtElementsBuilder.() -> Unit): Set<SdJwtElement<JsonElement>> {
        val v = SdJwtElementsBuilder()
        v.action()
        return v.build()
    }

    class SdJwtElementsBuilder internal constructor() {
        private var plainClaims = mutableMapOf<String, JsonElement>()
        private var flatClaims = mutableMapOf<String, JsonElement>()
        private var structuredClaims = mutableSetOf<SdJwtElement.StructuredDisclosed<JsonElement>>()

        fun plain(cs: Claims<JsonElement>) {
            plainClaims.putAll(cs)
        }

        fun plain(usage: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(usage))
        }

        fun flat(cs: Claims<JsonElement>) {
            flatClaims.putAll(cs)
        }

        fun flat(usage: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(usage))
        }


        fun structured(claimName: String, action: SdJwtElementsBuilder.() -> Unit) {
            val builder = SdJwtElementsBuilder()
            builder.action();
            val element = SdJwtElement.StructuredDisclosed(claimName, builder.claims())
            structuredClaims.add(element)
        }

        private fun claims(): Set<SdJwtElement<JsonElement>> =
            buildSet {
                addAll(structuredClaims)
                add(SdJwtElement.Plain(plainClaims))
                add(SdJwtElement.FlatDisclosed(flatClaims))
            }

        fun build(): Set<SdJwtElement<JsonElement>> = claims()
    }


    fun option1FlatSdJwt() =
        sdJwt {
            plain {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "https://example.com/issuer")
                put("iat", 1516239022)
                put("exp", 1735689661)
            }
            flat {
                putJsonObject("address") {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }

    fun option2StructuredSdJwt() =
        sdJwt {
            plain {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "https://example.com/issuer")
                put("iat", 1516239022)
                put("exp", 1735689661)
            }
            structured("address") {
                flat {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }

    fun reallyComplex() =
        sdJwt {
            plain {
                put("iss", "https://example.com/issuer")
                put("iat", 1516239022)
                put("exp", 1735689661)
            }
            flat {
                put("birth_middle_name", "Timotheus")
                put("salutation", "Dr.")
                put("msisdn", "49123456789")
            }
            structured("verified_claims") {
                structured("verification") {
                    plain {
                        put("trust_framework", "de_aml")
                    }
                    flat {
                        put("time", "2012-04-23T18:25Z")
                        put("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                    }
                }
                structured("claim") {
                    flat {
                        put("given_name", "Max")
                        put("family_name", "Müller")
                        putJsonArray("nationalities") {
                            add("DE")
                        }
                        put("birthdate", "1956-01-28")
                        putJsonObject("place_of_birth") {
                            put("country", "IS")
                            put("locality", "Þykkvabæjarklaustur")
                        }
                        putJsonObject("address") {
                            put("locality", "Maxstadt")
                            put("postal_code", "12344")
                            put("country", "DE")
                            put("street_address", "Weidenstraße 22")
                        }
                    }
                }
            }
        }
}


fun main() {
    println("Let's do it")
    with(KotlinxSupport) {
        disclose(sdJwt = reallyComplex()).getOrThrow().also { it.print() }
    }

}

fun DisclosedClaims<JsonObject>.print() {

    ds.forEach { println(it.claim()) }
    claims.also { println(json.encodeToString(it)) }
}






