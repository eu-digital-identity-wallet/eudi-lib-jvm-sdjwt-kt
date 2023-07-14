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

import eu.europa.ec.eudi.sdjwt.SdElement.*
import kotlinx.serialization.json.*

val DefaultDisclosureCreator: DisclosuresCreator = DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, 0)

/**
 * A class for [disclosing][discloseObj] a set of [SD-JWT elements][SdElement].
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

    private fun discloseClaim(claimName: String, claimValue: SdElement): DisclosedClaims {
        fun plain(plainJsonElement: PlainJsonElement): DisclosedClaims =
            DisclosedClaims(emptySet(), JsonObject(mapOf(claimName to plainJsonElement.content)))

        fun selectiveDisclosed(
            sdJsonElement: SdJsonElement,
            allowNestedHashClaim: Boolean = false,
        ): DisclosedClaims {
            val claims = mapOf(claimName to sdJsonElement.content)
            val (disclosures, digests) = disclosuresAndDigests(claims, allowNestedHashClaim)
            return DisclosedClaims(disclosures, digestsClaim(digests))
        }

        fun sdArray(sdArr: SdArray): DisclosedClaims {
            val disclosures = mutableSetOf<Disclosure>()
            fun handle(item: SdOrPlainJsonElement): List<JsonElement> = when (item) {
                is PlainJsonElement -> listOf(item.content)
                is SdJsonElement -> {
                    val (ds, digests) = disclosureAndDigestArrayElement(item.content)
                    disclosures += ds
                    digests.map { dig -> JsonObject(mapOf("..." to JsonPrimitive(dig.value))) }
                }
            }
            val claims = JsonObject(mapOf(claimName to JsonArray(sdArr.flatMap { handle(it) })))
            return DisclosedClaims(disclosures, claims)
        }

        fun structuredObj(structuredSdObject: StructuredSdObject): DisclosedClaims {
            fun nest(cs: JsonObject): JsonObject = JsonObject(mapOf(claimName to cs))
            val (ds, cs) = discloseObj(structuredSdObject.content)
            return DisclosedClaims(ds, nest(cs))
        }

        fun recursiveArr(recursiveSdArray: RecursiveSdArray): DisclosedClaims {
            val (ds1, cs1) = sdArray(recursiveSdArray.content)
            val nested = SdJsonElement(cs1[claimName]!!)
            val (ds2, cs2) = selectiveDisclosed(nested)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        fun recursiveObj(recursiveSdObject: RecursiveSdObject): DisclosedClaims {
            val (ds1, cs1) = discloseObj(recursiveSdObject.content)
            val nested = SdJsonElement(cs1)
            val (ds2, cs2) = selectiveDisclosed(nested, allowNestedHashClaim = true)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        return when (claimValue) {
            is PlainJsonElement -> plain(claimValue)
            is SdJsonElement -> selectiveDisclosed(claimValue)
            is SdArray -> sdArray(claimValue)
            is SdObject -> discloseObj(claimValue)
            is StructuredSdObject -> structuredObj(claimValue)
            is RecursiveSdArray -> recursiveArr(claimValue)
            is RecursiveSdObject -> recursiveObj(claimValue)
        }
    }

    /**
     * Discloses a set of  [SD-JWT element][sdObject]
     * @param sdObject the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun discloseObj(sdObject: SdObject): DisclosedClaims {
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

        for ((claimName, claimValue) in sdObject) {
            val (disclosures, json) = discloseClaim(claimName, claimValue)
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
    fun discloseSdJwt(sdJwtElements: SdObject): Result<DisclosedClaims> = runCatching {
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
