/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.sdjwt


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

val DefaultDisclosureCreator: DisclosuresCreator = DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, 0)

/**
 * A class for [disclosing][disclose] a set of [SD-JWT elements][SdJwtElement].
 * In this context, [outcome][DisclosedClaims] of the disclosure is the calculation
 * of a set of [disclosures][DisclosedClaims.disclosures] and a [set of claims][DisclosedClaims.claimSet]
 * to be included in the payload of the SD-JWT.
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param numOfDecoysLimit the upper limit of the decoys to generate
 */
class DisclosuresCreator(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val numOfDecoysLimit: Int = 0,
) {

    /**
     * Creates [disclosures][Disclosure] & [hashes][Disclosure], possibly including decoys,
     * depending on the [numOfDecoysLimit] provided
     *
     * @param claims the claims for which to calculate [DisclosedClaims] and [DisclosureDigest]
     *
     * @return disclosures and hashes, possibly including decoys
     * */
    internal fun disclosuresAndDigests(
        claims: Claims,
        allowNestedHashClaim: Boolean,
    ): Pair<Set<Disclosure.ObjectProperty>, Set<DisclosureDigest>> {
        val digestPerDisclosure = mutableMapOf<Disclosure.ObjectProperty, DisclosureDigest>()

        for (claim in claims) {
            val disclosure = Disclosure.objectProperty(saltProvider, claim.toPair(), allowNestedHashClaim).getOrThrow()
            val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
            digestPerDisclosure[disclosure] = digest
        }

        val decoys = DecoyGen.Default.genUpTo(hashAlgorithm, numOfDecoysLimit)
        val digests = digestPerDisclosure.values + decoys

        return digestPerDisclosure.keys to digests.toSortedSet(Comparator.comparing { it.value })
    }

    internal fun disclosureAndDigestArrayElement(e: JsonElement): Pair<Disclosure.ArrayElement, Set<DisclosureDigest>> {
        val disclosure = Disclosure.arrayElement(saltProvider, e).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        val decoys = DecoyGen.Default.gen(hashAlgorithm, numOfDecoysLimit)
        val digests = (decoys + digest)
        return disclosure to digests.toSortedSet(Comparator.comparing { it.value })
    }

    private fun sdClaim(digests: Set<DisclosureDigest>): JsonObject =
        if (digests.isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf("_sd" to JsonArray(digests.map { JsonPrimitive(it.value) })))

    private fun discloseElement(sdClaim: SdClaim): DisclosedClaims {
        val (claimName, sdJsonElement) = sdClaim

        fun arr(element: SdJsonElement.Arr): DisclosedClaims {
            val ds = mutableSetOf<Disclosure>()
            val es = element.flatMap {
                if (!it.sd) listOf(it.content)
                else {
                    val (d, digs) = disclosureAndDigestArrayElement(it.content)
                    ds += d
                    digs.map { dig -> JsonObject(mapOf("..." to JsonPrimitive(dig.value))) }
                }
            }
            return DisclosedClaims(ds, JsonObject(mapOf(claimName to JsonArray(es))))
        }

        fun sdAsAWhole(element: SdJsonElement.SdAsAWhole): DisclosedClaims {
            val claims = mapOf(claimName to element.content)
            return if (!element.sd) DisclosedClaims(emptySet(), JsonObject(claims))
            else {
                val (disclosures, digests) = disclosuresAndDigests(claims, false)
                return DisclosedClaims(disclosures, sdClaim(digests))
            }
        }


        fun structured(element: SdJsonElement.Structured): DisclosedClaims {



            fun nest(cs: JsonObject): JsonObject {
                return JsonObject( mapOf(claimName to cs))
            }

            val tmp = disclose(SdJsonElement.Obj(element.content))

            return  tmp.mapClaims { nest(it) }


        }
        fun structuredArr(element: SdJsonElement.StructuredArr): DisclosedClaims {
            val (ds1, cs1) = arr(element.arr)
            val (ds2, cs2) = sdAsAWhole(SdJsonElement.SdAsAWhole(true, cs1[claimName]!!))
            return DisclosedClaims(ds1+ds2, cs2)

        }

        fun recursive(e: SdJsonElement.Recursive): DisclosedClaims {
            TODO()
        }

        return when (sdJsonElement) {
            is SdJsonElement.Arr -> arr(sdJsonElement)
            is SdJsonElement.Obj -> disclose(sdJsonElement)
            is SdJsonElement.SdAsAWhole -> sdAsAWhole(sdJsonElement)
            is SdJsonElement.Structured -> structured(sdJsonElement)
            is SdJsonElement.StructuredArr -> structuredArr(sdJsonElement)
            is SdJsonElement.Recursive -> recursive(sdJsonElement)
        }
    }

    /**
     * Discloses a set of  [SD-JWT element][sdJwtElements]
     * @param sdJwtElements the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun disclose(sdJwtElements: SdJsonElement.Obj): DisclosedClaims {
        val accumulatedDisclosures = mutableSetOf<Disclosure>()
        val accumulatedJson = mutableMapOf<String, JsonElement>()

        for (element in sdJwtElements) {
            val (disclosures, json) = discloseElement(element.toPair())
            accumulatedDisclosures += disclosures
            add(accumulatedJson, json)
        }

        return DisclosedClaims(accumulatedDisclosures, JsonObject(accumulatedJson))
    }

    private fun add(a: MutableMap<String, JsonElement>, b: Claims) {
        val aSd: List<JsonElement> = a["_sd"]?.jsonArray ?: emptyList()
        val bSd: List<JsonElement> = b["_sd"]?.jsonArray ?: emptyList()
        val sd = aSd + bSd
        a += b
        if (sd.isNotEmpty()) {
            a["_sd"] = JsonArray(sd)
        }

    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims =
        if (disclosures.isEmpty()) this
        else mapClaims { claims ->
            val hashAlgClaim = "_sd_alg" to JsonPrimitive(h.alias)
            JsonObject(claims + hashAlgClaim)
        }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: SdJsonElement.Obj): Result<DisclosedClaims> = runCatching {
        disclose(sdJwtElements).addHashAlgClaim(hashAlgorithm)
    }
}
