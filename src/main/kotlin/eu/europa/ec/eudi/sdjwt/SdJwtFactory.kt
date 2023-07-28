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

/**
 * Factory for creating an [UnsignedSdJwt]
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
     * Calculates a [UnsignedSdJwt] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [UnsignedSdJwt] for the given [SD-JWT element][sdJwtElements]
     */
    fun createSdJwt(sdJwtElements: SdObject): Result<UnsignedSdJwt> = runCatching {
        encodeObj(sdJwtElements).addHashAlgClaim(hashAlgorithm)
    }

    /**
     * Encodes a set of  [SD-JWT element][sdObject]
     * @param sdObject the set of elements to disclose
     * @return the [UnsignedSdJwt]
     */
    private fun encodeObj(sdObject: SdObject): UnsignedSdJwt {
        val disclosures = mutableSetOf<Disclosure>()
        val encodedClaims = mutableMapOf<String, JsonElement>()

        fun add(encodedClaim: Claims) {
            val mergedSdClaim = JsonArray(encodedClaims.sdClaim() + encodedClaim.sdClaim())
            encodedClaims += encodedClaim
            if (mergedSdClaim.isNotEmpty()) {
                encodedClaims["_sd"] = mergedSdClaim
            }
        }

        for ((subClaimName, subClaimValue) in sdObject) {
            val (encodedSubClaim, subClaimDisclosures) = encodeClaim(subClaimName, subClaimValue)
            disclosures += subClaimDisclosures
            add(encodedSubClaim)
        }
        val sdObjectClaims = JsonObject(encodedClaims)

        return UnsignedSdJwt(sdObjectClaims, disclosures)
    }

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
            val sdClaim = digestAndDecoys.sdClaim()
            return UnsignedSdJwt(sdClaim, setOf(disclosure))
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

            val arrayClaim = buildJsonObject {
                putJsonArray(claimName) {
                    addAll(plainElements)
                    addAll((digests + decoys()).sorted().map { it.asDigestClaim() })
                }
            }
            return UnsignedSdJwt(arrayClaim, disclosures)
        }

        fun encodeStructuredSdObject(structuredSdObject: StructuredSdObject): UnsignedSdJwt {
            val (encodedSubClaims, disclosures) = encodeObj(structuredSdObject.content)
            val structuredSdClaim = JsonObject(mapOf(claimName to encodedSubClaims))
            return UnsignedSdJwt(structuredSdClaim, disclosures)
        }

        fun encodeRecursiveSdArray(recursiveSdArray: RecursiveSdArray): UnsignedSdJwt {
            val (contentClaims, contentDisclosures) = encodeSdArray(recursiveSdArray.content)
            val wrapper = Sd(checkNotNull(contentClaims[claimName]))
            val (wrapperClaim, wrapperDisclosures) = encodeSd(wrapper)
            val disclosures = contentDisclosures + wrapperDisclosures
            return UnsignedSdJwt(wrapperClaim, disclosures)
        }

        fun encodeRecursiveSdObject(recursiveSdObject: RecursiveSdObject): UnsignedSdJwt {
            val (contentClaims, contentDisclosures) = encodeObj(recursiveSdObject.content)
            val wrapper = Sd(contentClaims)
            val (wrapperClaim, wrapperDisclosures) = encodeSd(wrapper, allowNestedDigests = true)
            val disclosures = contentDisclosures + wrapperDisclosures
            return UnsignedSdJwt(wrapperClaim, disclosures)
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

    private fun Set<DisclosureDigest>.sdClaim(): JsonObject =
        if (isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf("_sd" to JsonArray(map { JsonPrimitive(it.value) })))

    private fun Claims.sdClaim(): List<JsonElement> = this["_sd"]?.jsonArray ?: emptyList()

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
