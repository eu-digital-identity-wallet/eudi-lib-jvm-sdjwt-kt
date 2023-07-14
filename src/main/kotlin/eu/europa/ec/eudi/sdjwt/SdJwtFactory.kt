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

typealias UnsignedSdJwt = SdJwt.Issuance<JsonObject>

/**
 * A class for [disclosing][encodeObj] a set of [SD-JWT elements][SdElement].
 * In this context, [outcome][UnsignedSdJwt] of the disclosure is the calculation
 * of a set of [disclosures][Disclosure] and a [set of claims][Claims]
 * to be included in the payload of the SD-JWT.
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param numOfDecoysLimit the upper limit of the decoys to generate
 */
class SdJwtFactory(
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
    private fun encodeClaim(claimName: String, claimValue: SdElement): UnsignedSdJwt {
        fun encodePlain(plain: Plain): UnsignedSdJwt =
            UnsignedSdJwt(JsonObject(mapOf(claimName to plain.content)), emptySet())

        fun encodeSd(sd: Sd, allowNestedDigests: Boolean = false): UnsignedSdJwt {
            val claim = claimName to sd.content
            val (disclosure, digest) = objectPropertyDisclosure(claim, allowNestedDigests)
            val digestAndDecoys = (decoys() + digest).sorted()
            return UnsignedSdJwt(digestAndDecoys.asDigestsClaim(), setOf(disclosure))
        }

        fun encodeSdArray(sdArray: SdArray): UnsignedSdJwt {
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
            return UnsignedSdJwt(claim, disclosures)
        }

        fun encodeStructuredSdObject(structuredSdObject: StructuredSdObject): UnsignedSdJwt {
            fun nest(cs: JsonObject): JsonObject = JsonObject(mapOf(claimName to cs))
            val (cs, ds) = encodeObj(structuredSdObject.content)
            return UnsignedSdJwt(nest(cs), ds)
        }

        fun encodeRecursiveSdArray(recursiveSdArray: RecursiveSdArray): UnsignedSdJwt {
            val (cs1, ds1) = encodeSdArray(recursiveSdArray.content)
            val nested = Sd(cs1[claimName]!!)
            val (cs2, ds2) = encodeSd(nested)
            return UnsignedSdJwt(cs2, ds1 + ds2)
        }

        fun encodeRecursiveSdObject(recursiveSdObject: RecursiveSdObject): UnsignedSdJwt {
            val (cs1, ds1) = encodeObj(recursiveSdObject.content)
            val nested = Sd(cs1)
            val (cs2, ds2) = encodeSd(nested, allowNestedDigests = true)
            return UnsignedSdJwt(cs2, ds1 + ds2)
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
     * @return the [UnsignedSdJwt]
     */
    private fun encodeObj(sdObject: SdObject): UnsignedSdJwt {
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
            val (json, disclosures) = encodeClaim(claimName, claimValue)
            accumulatedDisclosures += disclosures
            addToClaimsMergeSds(json)
        }

        return UnsignedSdJwt(JsonObject(accumulatedJson), accumulatedDisclosures)
    }

    /**
     * Calculates a [UnsignedSdJwt] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [UnsignedSdJwt] for the given [SD-JWT element][sdJwtElements]
     */
    fun createSdJwt(sdJwtElements: SdObject): Result<UnsignedSdJwt> = runCatching {
        encodeObj(sdJwtElements).addHashAlgClaim(hashAlgorithm)
    }

    private fun decoys() = decoyGen.genUpTo(hashAlgorithm, numOfDecoysLimit)
    private fun Iterable<DisclosureDigest>.sorted(): Set<DisclosureDigest> =
        toSortedSet(Comparator.comparing { it.value })

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [UnsignedSdJwt] with an updated [Claims] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun UnsignedSdJwt.addHashAlgClaim(h: HashAlgorithm): UnsignedSdJwt {
        return if (disclosures.isEmpty()) this
        else copy(jwt = JsonObject(jwt + ("_sd_alg" to JsonPrimitive(h.alias))))
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
    ): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.objectProperty(saltProvider, claim, allowNestedDigests).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    private fun arrayElementDisclosure(jsonElement: JsonElement): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.arrayElement(saltProvider, jsonElement).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    companion object {
        val Default: SdJwtFactory =
            SdJwtFactory(HashAlgorithm.SHA_256, SaltProvider.Default, DecoyGen.Default, 0)

        fun of(hashAlgorithm: HashAlgorithm, numOfDecoysLimit: Int): SdJwtFactory =
            SdJwtFactory(hashAlgorithm, numOfDecoysLimit = numOfDecoysLimit)
    }
}
