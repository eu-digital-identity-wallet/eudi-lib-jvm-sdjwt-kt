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

import eu.europa.ec.eudi.sdjwt.SdJwtElement.*
import kotlinx.serialization.json.*

val DefaultDisclosureCreator: DisclosuresCreator = DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, 0)

/**
 * A class for [disclosing][discloseObj] a set of [SD-JWT elements][SdJwtElement].
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
    private fun disclosuresAndDigests(
        claims: Claims,
        allowNestedDigests: Boolean,
    ): Pair<Set<Disclosure.ObjectProperty>, Set<DisclosureDigest>> {
        val digestPerDisclosure = mutableMapOf<Disclosure.ObjectProperty, DisclosureDigest>()

        for (claim in claims) {
            val disclosure = Disclosure.objectProperty(saltProvider, claim.toPair(), allowNestedDigests).getOrThrow()
            val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
            digestPerDisclosure[disclosure] = digest
        }

        val decoys = DecoyGen.Default.genUpTo(hashAlgorithm, numOfDecoysLimit)
        val digests = digestPerDisclosure.values + decoys

        return digestPerDisclosure.keys to digests.toSortedSet(Comparator.comparing { it.value })
    }

    private fun disclosureAndDigestArrayElement(e: JsonElement): Pair<Disclosure.ArrayElement, Set<DisclosureDigest>> {
        val disclosure = Disclosure.arrayElement(saltProvider, e).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        val decoys = DecoyGen.Default.gen(hashAlgorithm, numOfDecoysLimit)
        val digests = (decoys + digest)
        return disclosure to digests.toSortedSet(Comparator.comparing { it.value })
    }

    private fun digestsClaim(digests: Set<DisclosureDigest>): JsonObject =
        if (digests.isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf("_sd" to JsonArray(digests.map { JsonPrimitive(it.value) })))

    private fun discloseElement(sdClaim: SdClaim): DisclosedClaims {
        val (claimName, sdJsonElement) = sdClaim

        fun flat(element: SdOrPlain, allowNestedHashClaim: Boolean = false): DisclosedClaims {
            val claims = mapOf(claimName to element.content)
            return if (!element.sd) DisclosedClaims(emptySet(), JsonObject(claims))
            else {
                val (disclosures, digests) = disclosuresAndDigests(claims, allowNestedHashClaim)
                return DisclosedClaims(disclosures, digestsClaim(digests))
            }
        }

        fun arr(element: Arr): DisclosedClaims {
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

        fun structuredObj(element: StructuredObj): DisclosedClaims {
            fun nest(cs: JsonObject): JsonObject = JsonObject(mapOf(claimName to cs))
            val (ds, cs) = discloseObj(element.content)
            return DisclosedClaims(ds, nest(cs))
        }

        fun recursiveArr(element: RecursiveArr): DisclosedClaims {
            val (ds1, cs1) = arr(element.content)
            val nested = SdOrPlain(true, cs1[claimName]!!)
            val (ds2, cs2) = flat(nested)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        fun recursiveObj(element: RecursiveObj): DisclosedClaims {
            val (ds1, cs1) = discloseObj(element.content)
            val nested = SdOrPlain(true, cs1)
            val (ds2, cs2) = flat(nested, allowNestedHashClaim = true)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        return when (sdJsonElement) {
            is SdOrPlain -> flat(sdJsonElement)
            is Arr -> arr(sdJsonElement)
            is Obj -> discloseObj(sdJsonElement)
            is StructuredObj -> structuredObj(sdJsonElement)
            is RecursiveArr -> recursiveArr(sdJsonElement)
            is RecursiveObj -> recursiveObj(sdJsonElement)
        }
    }

    /**
     * Discloses a set of  [SD-JWT element][obj]
     * @param obj the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun discloseObj(obj: Obj): DisclosedClaims {
        val accumulatedDisclosures = mutableSetOf<Disclosure>()
        val accumulatedJson = mutableMapOf<String, JsonElement>()

        fun addToClaimsMergeSds(that: Claims) {
            fun Claims.sd(): List<JsonElement> = this["_sd"]?.jsonArray ?: emptyList()
            val mergedSd = JsonArray(accumulatedJson.sd() + that.sd())
            accumulatedJson += that
            if (mergedSd.isNotEmpty()) {
                accumulatedJson["_sd"] = mergedSd
            }
        }

        for (element in obj) {
            val (disclosures, json) = discloseElement(element.toPair())
            accumulatedDisclosures += disclosures
            addToClaimsMergeSds(json)
        }

        return DisclosedClaims(accumulatedDisclosures, JsonObject(accumulatedJson))
    }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: Obj): Result<DisclosedClaims> = runCatching {
        discloseObj(sdJwtElements).addHashAlgClaim(hashAlgorithm)
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
}
