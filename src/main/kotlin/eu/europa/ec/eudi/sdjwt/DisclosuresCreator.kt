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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

val DefaultDisclosureCreator: DisclosuresCreator =
    DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, DecoyGen.Default, 0)

/**
 * A class for [disclosing][encodeObj] a set of [SD-JWT elements][SdElement].
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
    private val decoyGen: DecoyGen = DecoyGen.Default,
    private val numOfDecoysLimit: Int = 0,
) {

    /**
     * Produces the disclosures and the JWT claims (which include digests)
     * for the given claim
     *
     * @param claimName the name of the claim
     * @param claimValue the value of the claim
     *
     * @return the disclosures and the JWT claims (which include digests)
     *  for the given claim
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun encodeClaim(claimName: String, claimValue: SdElement): DisclosedClaims {
        fun encodePlain(plain: Plain): DisclosedClaims =
            DisclosedClaims(emptySet(), JsonObject(mapOf(claimName to plain.content)))

        fun encodeSd(sd: Sd, allowNestedDigests: Boolean = false): DisclosedClaims {
            val claim = claimName to sd.content
            val (disclosure, digest) = objectPropertyDisclosure(claim, allowNestedDigests)
            val digestAndDecoys = (decoys() + digest).sorted()
            return DisclosedClaims(setOf(disclosure), digestAndDecoys.asDigestsClaim())
        }

        fun encodeSdArray(sdArray: SdArray): DisclosedClaims {
            val disclosures = mutableSetOf<Disclosure>()
            val digests = mutableSetOf<DisclosureDigest>()
            val plainElements = mutableListOf<JsonElement>()
            for (element in sdArray) {
                when (element) {
                    is Plain -> plainElements += element.content
                    is Sd -> {
                        val (disclosure, digest) = arrayElementDisclosure(element.content)
                        disclosures += disclosure
                        digests += digest
                    }
                }
            }

            val claim = buildJsonObject {
                putJsonArray(claimName) {
                    addAll(plainElements)
                    addAll((digests + decoys()).sorted().map { it.asDigestClaim() })
                }
            }
            return DisclosedClaims(disclosures, claim)
        }

        fun encodeStructuredSdObject(structuredSdObject: StructuredSdObject): DisclosedClaims {
            fun nest(cs: JsonObject): JsonObject = JsonObject(mapOf(claimName to cs))
            val (ds, cs) = encodeObj(structuredSdObject.content)
            return DisclosedClaims(ds, nest(cs))
        }

        fun encodeRecursiveSdArray(recursiveSdArray: RecursiveSdArray): DisclosedClaims {
            val (ds1, cs1) = encodeSdArray(recursiveSdArray.content)
            val nested = Sd(cs1[claimName]!!)
            val (ds2, cs2) = encodeSd(nested)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        fun encodeRecursiveSdObject(recursiveSdObject: RecursiveSdObject): DisclosedClaims {
            val (ds1, cs1) = encodeObj(recursiveSdObject.content)
            val nested = Sd(cs1)
            val (ds2, cs2) = encodeSd(nested, allowNestedDigests = true)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        return when (claimValue) {
            is Plain -> encodePlain(claimValue)
            is Sd -> encodeSd(claimValue)
            is SdArray -> encodeSdArray(claimValue)
            is SdObject -> encodeObj(claimValue)
            is StructuredSdObject -> encodeStructuredSdObject(claimValue)
            is RecursiveSdArray -> encodeRecursiveSdArray(claimValue)
            is RecursiveSdObject -> encodeRecursiveSdObject(claimValue)
        }
    }

    /**
     * Encodes a set of  [SD-JWT element][sdObject]
     * @param sdObject the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun encodeObj(sdObject: SdObject): DisclosedClaims {
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
            val (disclosures, json) = encodeClaim(claimName, claimValue)
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
        encodeObj(sdJwtElements).addHashAlgClaim(hashAlgorithm)
    }

    private fun decoys() = decoyGen.genUpTo(hashAlgorithm, numOfDecoysLimit)
    private fun Iterable<DisclosureDigest>.sorted(): Set<DisclosureDigest> =
        toSortedSet(Comparator.comparing { it.value })

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims =
        if (disclosures.isEmpty()) this
        else mapClaims { claims ->
            val hashAlgorithmClaim = "_sd_alg" to JsonPrimitive(h.alias)
            JsonObject(claims + hashAlgorithmClaim)
        }

    private fun Set<DisclosureDigest>.asDigestsClaim(): JsonObject =
        if (isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf("_sd" to JsonArray(map { JsonPrimitive(it.value) })))

    private fun DisclosureDigest.asDigestClaim(): JsonObject {
        return JsonObject(mapOf("..." to JsonPrimitive(value)))
    }

    private fun objectPropertyDisclosure(
        claim: Claim,
        allowNestedDigests: Boolean,
    ): Pair<Disclosure.ObjectProperty, DisclosureDigest> {
        val disclosure = Disclosure.objectProperty(saltProvider, claim, allowNestedDigests).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    private fun arrayElementDisclosure(jsonElement: JsonElement): Pair<Disclosure.ArrayElement, DisclosureDigest> {
        val disclosure = Disclosure.arrayElement(saltProvider, jsonElement).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }
}
