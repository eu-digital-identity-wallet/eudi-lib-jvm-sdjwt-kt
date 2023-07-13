package eu.europa.ec.eudi.sdjwt



import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*




sealed interface SdJsonElement {

    data class SdAsAWhole(val sd: Boolean, val content: JsonElement) : SdJsonElement {
        init {
            require(content != JsonNull || !sd) { "Json Null cannot be selectively disclosable" }
        }
    }

    data class Structured(val content: Obj) : SdJsonElement
    data class Recursive(val content: Obj) : SdJsonElement


    class Obj(private val content: Map<String, SdJsonElement>) : SdJsonElement,
        Map<String, SdJsonElement> by content

    class Arr(private val content: List<SdAsAWhole>) : SdJsonElement, List<SdAsAWhole> by content

}
typealias SdClaim = Pair<String, SdJsonElement>


fun MutableList<SdJsonElement.SdAsAWhole>.sd(value: String) {
    sd(JsonPrimitive(value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.sd(value: Boolean) {
    sd(JsonPrimitive(value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.sd(value: Number) {
    sd(JsonPrimitive(value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.plain(value: String) {
    plain(JsonPrimitive(value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.plain(value: Boolean) {
    plain(JsonPrimitive(value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.plain(value: Number) {
    plain(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.SdAsAWhole>.sd(value: JsonElement) {
    add(SdJsonElement.SdAsAWhole(true, value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.plain(value: JsonElement) {
    add(SdJsonElement.SdAsAWhole(false, value))
}
fun MutableList<SdJsonElement.SdAsAWhole>.sd(action: (@SdJwtElementDsl JsonObjectBuilder).()->Unit) {
    sd(buildJsonObject(action))
}
fun MutableList<SdJsonElement.SdAsAWhole>.plain(action: (@SdJwtElementDsl JsonObjectBuilder).()->Unit) {
    plain(buildJsonObject(action))
}


fun example(): SdJsonElement.Obj {
    return buildObj {
        sub("user_42")
        iss("https://example.com/issuer")
        iat(1516239022)
        exp(1735689661)

        sd {
            put("given_name", "John")
            put("family_name", "Doe")
            put("email", "johndoe@example.com")
            put("phone_number", "+1-202-555-0101")
            put("phone_number_verified", true)
            putJsonObject("address") {
                put("street_address", "123 Main St")
                put("locality", "Anytown")
                put("region", "Anystate")
                put("country", "US")
            }
            put("birthdate", "1940-01-01")
            put("updated_at", 1570000000)
        }

        sdArrayClaim("nationalities") {
            sd("US")
            sd("DE")
        }

        plain {
            putJsonObject("cnf") {
                putJsonObject("jwk") {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                    put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
                }
            }
        }
    }
}

fun option1(): SdJsonElement.Obj {
    return buildObj {
        sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
        iss("https://example.com/issuer")
        iat(1516239022)
        exp(1735689661)
        sdObjectClaim("address") {
            put("street_address", "Schulstr. 12")
            put("locality", "Schulpforta")
            put("region", "Sachsen-Anhalt")
            put("country", "DE")
        }

    }
}

fun option2(): SdJsonElement.Obj {
    return buildObj {
        sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
        iss("https://example.com/issuer")
        iat(1516239022)
        exp(1735689661)
        structured("address") {
            sd {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
                putJsonObject("foo") {
                    put("bar", "barValue")
                }
            }

        }
    }
}

fun option3(): SdJsonElement.Obj {
    return buildObj {
        sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
        iss("https://example.com/issuer")
        iat(1516239022)
        exp(1735689661)
        recursive("address") {
            sd("street_address", "Schulstr. 12")
            sd("locality", "Schulpforta")
            sd("region", "Sachsen-Anhalt")
            sd("country", "DE")
        }
    }
}

fun example3(): SdJsonElement.Obj {
    return buildObj {
        iss("https://example.com/issuer")
        iat(1516239022)
        exp(1735689661)

        sd {
            put("birth_middle_name", "Timotheus")
            put("salutation", "Dr.")
            put("msisdn", "49123456789")
        }
        structured("verified_claims") {
            structured("verification") {
                plain {
                    put("trust_framework", "de_aml")
                }
                sd {
                    put("time", "2012-04-23T18:25Z")
                    put("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                }
                sdArrayClaim("evidence") {
                    sd {
                        put("type", "document")
                        put("method", "pipp")
                        put("time", "2012-04-22T11:30Z")
                        putJsonObject("document") {
                            put("type", "idcard")
                            putJsonObject("issuer") {
                                put("name", "Stadt Augsburg")
                                put("country", "DE")
                            }
                            put("number", "53554554")
                            put("date_of_issuance", "2010-03-23")
                            put("date_of_expiry", "2020-03-22")
                        }
                    }

                }
            }
            structured("claim") {
                sd {
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

    val sdJwt: SdJsonElement.Obj = example3()
    DisclosuresCreator(numOfDecoysLimit = 0).create(sdJwt).also { it.print() }
}

@OptIn(ExperimentalSerializationApi::class)
fun DisclosuresCreator.work(sdClaim: SdClaim): DisclosedClaims {
    val (claimName, sdJsonElement) = sdClaim
    return when (sdJsonElement) {
        is SdJsonElement.Arr -> {
            val ds = mutableSetOf<Disclosure>()
            val es = sdJsonElement.flatMap {
                if (!it.sd) listOf(it.content)
                else {
                    val (d, digs) = disclosureAndDigestArrayElement(it.content)
                    ds += d
                    digs.map { dig -> JsonObject(mapOf("..." to JsonPrimitive(dig.value))) }
                }
            }

            return DisclosedClaims(ds, JsonObject(mapOf(claimName to JsonArray(es))))
        }

        is SdJsonElement.Obj -> TODO()
        is SdJsonElement.SdAsAWhole -> {
            val claims = mapOf(claimName to sdJsonElement.content)
            if (!sdJsonElement.sd) DisclosedClaims(emptySet(), JsonObject(claims))
            else {
                val (disclosures, digests) = disclosuresAndDigests(claims, false)
                val hashClaims =
                    if (digests.isNotEmpty()) JsonObject(mapOf("_sd" to buildJsonArray { addAll(digests.map { it.value }) }))
                    else JsonObject(emptyMap())
                return DisclosedClaims(disclosures, hashClaims)
            }
        }

        is SdJsonElement.Structured -> {
            val (ds, cs) = create(sdJsonElement.content)
            val cs1 = JsonObject(mapOf(claimName to cs))
            return DisclosedClaims(ds, cs1)
        }

        is SdJsonElement.Recursive -> {

            TODO()
        }
    }
}

fun DisclosuresCreator.create(sdJwt: SdJsonElement.Obj): DisclosedClaims {
    val disclosures = mutableSetOf<Disclosure>()
    val claims = mutableMapOf<String, JsonElement>()

    for (i in sdJwt) {
        val sdClaim: SdClaim = i.toPair()
        val (ds, cs) = work(sdClaim)
        disclosures += ds
        claims += cs
    }
    return DisclosedClaims(disclosures, JsonObject(claims))
}


inline fun buildArr(action: MutableList<SdJsonElement.SdAsAWhole>.() -> Unit): SdJsonElement.Arr {
    val content = mutableListOf<SdJsonElement.SdAsAWhole>()
    content.action()
    return SdJsonElement.Arr(content.toList())
}


inline fun buildObj(action: ObjBuilder.() -> Unit): SdJsonElement.Obj {
    val b = ObjBuilder()
    b.action()
    return b.build()
}

@SdJwtElementDsl
class ObjBuilder @PublishedApi internal constructor() {
    private val content = mutableMapOf<String, SdJsonElement>()


    fun plain(name: String, value: JsonElement) {
        content += (name to SdJsonElement.SdAsAWhole(false, value))
    }


    fun sd(name: String, value: JsonElement) {
        content += (name to SdJsonElement.SdAsAWhole(true, value))
    }

    fun sd(name: String, value: SdJsonElement) {
        content += (name to value)
    }


    fun build(): SdJsonElement.Obj = SdJsonElement.Obj(content)

}

fun ObjBuilder.sd(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    sd(buildJsonObject(action))
}

fun ObjBuilder.sd(obj: JsonObject) {
    obj.forEach { (k, v) -> sd(k, v) }
}

fun ObjBuilder.sd(name: String, value: String) {
    sd(name, JsonPrimitive(value))
}

fun ObjBuilder.sd(name: String, value: Number) {
    sd(name, JsonPrimitive(value))
}

fun ObjBuilder.sd(name: String, value: Boolean) {
    sd(name, JsonPrimitive(value))
}


fun ObjBuilder.plain(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    plain(buildJsonObject(action))
}

fun ObjBuilder.plain(obj: JsonObject) {
    obj.forEach { (k, v) -> plain(k, v) }
}

fun ObjBuilder.plain(name: String, value: String) {
    plain(name, JsonPrimitive(value))
}

fun ObjBuilder.plain(name: String, value: Number) {
    plain(name, JsonPrimitive(value))
}

fun ObjBuilder.plain(name: String, value: Boolean) {
    plain(name, JsonPrimitive(value))
}

fun ObjBuilder.sub(value: String) {
    plain("sub", value)
}

fun ObjBuilder.iss(value: String) {
    plain("iss", value)
}

fun ObjBuilder.iat(value: Long) {
    plain("iat", value)
}

fun ObjBuilder.exp(value: Long) {
    plain("exp", value)
}

fun ObjBuilder.sdArrayClaim(name: String, action: (@SdJwtElementDsl MutableList<SdJsonElement.SdAsAWhole>).() -> Unit) {
    sd(name, buildArr(action))
}

fun ObjBuilder.sdObjectClaim(name: String, action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    sd(name, buildJsonObject(action))
}


fun ObjBuilder.structured(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, SdJsonElement.Structured(obj))
}

fun ObjBuilder.recursive(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, SdJsonElement.Recursive(obj))
}





