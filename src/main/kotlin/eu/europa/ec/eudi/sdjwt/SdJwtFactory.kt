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

import kotlinx.serialization.json.*

private typealias EncodedSdElement = Pair<JsonObject, List<Disclosure>>

@JvmInline
value class MinimumDigests(val value: Int) {
    init {
        require(value > 0) { "value must be greater than zero." }
    }

    operator fun plus(that: MinimumDigests) = MinimumDigests(this.value + that.value)
}

fun Int?.atLeastDigests(): MinimumDigests? = this?.let { MinimumDigests(it) }

/**
 * Factory for creating an [UnsignedSdJwt]
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param fallbackMinimumDigests This is an optional hint, that expresses the number of digests on the immediate level
 * of every [DisclosableObject]. It will be taken into account if there is not an explicit [hint][DisclosableObject.minimumDigests] for
 * this [DisclosableObject]. If not provided, decoys will be added only if there is a hint at [DisclosableObject] level.
 */
class SdJwtFactory(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val decoyGen: DecoyGen = DecoyGen.Default,
    private val fallbackMinimumDigests: MinimumDigests? = null,
) {

    /**
     * Calculates a [UnsignedSdJwt] for a given [SD-JWT element][sdJwtSpec].
     *
     * @param sdJwtSpec the contents of the SD-JWT
     * @return the [UnsignedSdJwt] for the given [SD-JWT element][sdJwtSpec]
     */
    fun createSdJwt(sdJwtSpec: DisclosableObject): Result<SdJwt<JsonObject>> = runCatching {
        val (jwtClaimSet, disclosures) = encodeObj(sdJwtSpec).addHashAlgClaim(hashAlgorithm)
        SdJwt(jwtClaimSet, disclosures)
    }

    /**
     * Encodes a set of  [SD-JWT element][disclosableObject]
     * @param disclosableObject the set of elements to disclose
     * @return the [UnsignedSdJwt]
     */
    private fun encodeObj(disclosableObject: DisclosableObject): EncodedSdElement {
        val disclosures = mutableListOf<Disclosure>()
        val encodedClaims = mutableMapOf<String, JsonElement>()

        // Add the given claim to encodedClaims
        fun add(encodedClaim: JsonObject) {
            val mergedSdClaim = JsonArray(encodedClaims.sdClaim() + encodedClaim.sdClaim())
            encodedClaims += encodedClaim
            if (mergedSdClaim.isNotEmpty()) {
                encodedClaims[SdJwtSpec.CLAIM_SD] = mergedSdClaim
            }
        }

        // Adds decoys if needed
        fun addDecoysIfNeeded() {
            val digests = encodedClaims.sdClaim()
            val decoys = genDecoys(digests.size, disclosableObject.minimumDigests).map { JsonPrimitive(it.value) }
            val digestAndDecoys = (digests + decoys).sortedBy { it.jsonPrimitive.contentOrNull }
            if (digestAndDecoys.isNotEmpty()) {
                encodedClaims[SdJwtSpec.CLAIM_SD] = JsonArray(digestAndDecoys)
            }
        }

        for ((subClaimName, subClaimValue) in disclosableObject) {
            val (encodedSubClaim, subClaimDisclosures) = encodeClaim(subClaimName, subClaimValue)
            disclosures += subClaimDisclosures
            add(encodedSubClaim)
        }

        addDecoysIfNeeded()
        val sdObjectClaims = JsonObject(encodedClaims)
        return sdObjectClaims to disclosures
    }

    /**
     * Produces the disclosures and the JWT claims (which include digests)
     * for the given claim
     *
     * @param claimName the name of the claim
     * @param disclosableElement the value of the claim
     *
     * @return the disclosures and the JWT claims (which include digests)
     *  for the given claim
     */
    private fun encodeClaim(claimName: String, disclosableElement: DisclosableElement): EncodedSdElement {
        fun encodeAlwaysDisclosable(disclosable: JsonElement): EncodedSdElement {
            val plainClaim = JsonObject(mapOf(claimName to disclosable))
            return plainClaim to emptyList()
        }

        fun encodeSelectivelyDisclosable(disclosable: JsonElement): EncodedSdElement {
            val claim = claimName to disclosable
            val (disclosure, digest) = objectPropertyDisclosure(claim)
            val digestAndDecoys = setOf(digest)
            val sdClaim = digestAndDecoys.sdClaim()
            return sdClaim to listOf(disclosure)
        }

        fun encodeAlwaysDisclosable(disclosable: DisclosableObject): EncodedSdElement {
            val (encodedSubClaims, disclosures) = encodeObj(disclosable)
            val structuredSdClaim = JsonObject(mapOf(claimName to encodedSubClaims))
            return structuredSdClaim to disclosures
        }

        fun encodeSelectivelyDisclosable(disclosable: DisclosableObject): EncodedSdElement {
            val (contentClaims, contentDisclosures) = encodeObj(disclosable)
            val wrapper = contentClaims
            val (wrapperClaim, wrapperDisclosures) = encodeSelectivelyDisclosable(wrapper)
            val disclosures = contentDisclosures + wrapperDisclosures
            return wrapperClaim to disclosures
        }

        fun encodeAlwaysDisclosable(disclosable: DisclosableArray): EncodedSdElement {
            val (disclosures, plainOrDigestElements) = arrayElementsDisclosure(disclosable)
            val actualDisclosureDigests = plainOrDigestElements.filterIsInstance<PlainOrDigest.Dig>().size
            val decoys = genDecoys(actualDisclosureDigests, disclosable.minimumDigests).map { PlainOrDigest.Dig(it) }
            val allElements = JsonArray(
                (plainOrDigestElements + decoys).map {
                    when (it) {
                        is PlainOrDigest.Dig -> it.value.asDigestClaim()
                        is PlainOrDigest.Plain -> it.value
                    }
                },
            )
            val arrayClaim = JsonObject(mapOf(claimName to allElements))
            return arrayClaim to disclosures
        }

        fun encodeSelectivelyDisclosable(disclosable: DisclosableArray): EncodedSdElement {
            val (contentClaims, contentDisclosures) = encodeAlwaysDisclosable(disclosable)
            val wrapper = checkNotNull(contentClaims[claimName])
            val (wrapperClaim, wrapperDisclosures) = encodeSelectivelyDisclosable(wrapper)
            val disclosures = contentDisclosures + wrapperDisclosures
            return wrapperClaim to disclosures
        }

        val (disclosable, element) = disclosableElement
        return when (element) {
            is DisclosableValue.Json -> when (disclosable) {
                Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
            }

            is DisclosableValue.Arr -> when (disclosable) {
                Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
            }

            is DisclosableValue.Obj -> when (disclosable) {
                Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
            }
        }
    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [EncodedSdElement] with an updated claims to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun EncodedSdElement.addHashAlgClaim(h: HashAlgorithm): EncodedSdElement {
        val (jwtClaimSet, disclosures) = this
        return if (disclosures.isEmpty()) this
        else {
            val newClaimSet = JsonObject(jwtClaimSet + (SdJwtSpec.CLAIM_SD_ALG to JsonPrimitive(h.alias)))
            newClaimSet to disclosures
        }
    }

    /**
     * Generates decoys, if needed.
     *
     */
    private fun genDecoys(disclosureDigests: Int, minimumDigests: MinimumDigests?): Set<DisclosureDigest> {
        val min = (minimumDigests ?: fallbackMinimumDigests)?.value ?: 0
        val numOfDecoys = min - disclosureDigests
        return decoyGen.gen(hashAlgorithm, numOfDecoys)
    }

    private fun Set<DisclosureDigest>.sdClaim(): JsonObject =
        if (isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(map { JsonPrimitive(it.value) })))

    private fun Map<String, JsonElement>.sdClaim(): List<JsonElement> =
        this[SdJwtSpec.CLAIM_SD]?.jsonArray ?: emptyList()

    private fun DisclosureDigest.asDigestClaim(): JsonObject {
        return JsonObject(mapOf(SdJwtSpec.CLAIM_ARRAY_ELEMENT_DIGEST to JsonPrimitive(value)))
    }

    private fun objectPropertyDisclosure(claim: Claim): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.objectProperty(saltProvider, claim).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    private fun arrayElementsDisclosure(array: DisclosableArray): Pair<List<Disclosure>, List<PlainOrDigest>> {
        fun disclosureOf(jsonElement: JsonElement): Pair<Disclosure, DisclosureDigest> {
            val disclosure = Disclosure.arrayElement(saltProvider, jsonElement).getOrThrow()
            val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
            return disclosure to digest
        }

        val disclosures = mutableListOf<Disclosure>()
        val plainOrDigestElements = mutableListOf<PlainOrDigest>()

        fun encodeAlwaysDisclosable(disclosable: JsonElement) {
            plainOrDigestElements += PlainOrDigest.Plain(disclosable)
        }

        fun encodeSelectivelyDisclosable(disclosable: JsonElement) {
            val (disclosure, digest) = disclosureOf(disclosable)
            disclosures += disclosure
            plainOrDigestElements += PlainOrDigest.Dig(digest)
        }

        fun encodeAlwaysDisclosable(disclosable: DisclosableObject) {
            val (json, ds) = encodeObj(disclosable)
            disclosures += ds
            plainOrDigestElements += PlainOrDigest.Plain(json)
        }

        fun encodeSelectivelyDisclosable(disclosable: DisclosableObject) {
            val (json, ds) = encodeObj(disclosable)
            val (ds2, dig) = disclosureOf(json)
            disclosures += (ds + ds2)
            plainOrDigestElements += PlainOrDigest.Dig(dig)
        }

        fun PlainOrDigest.toJsonElement(): JsonElement = when (this) {
            is PlainOrDigest.Plain -> value
            is PlainOrDigest.Dig -> value.asDigestClaim()
        }

        fun encodeAlwaysDisclosable(disclosable: DisclosableArray) {
            val (ds, elems) = arrayElementsDisclosure(disclosable)
            val json = JsonArray(elems.map { it.toJsonElement() })
            disclosures += ds
            plainOrDigestElements += PlainOrDigest.Plain(json)
        }

        fun encodeSelectivelyDisclosable(disclosable: DisclosableArray) {
            val (ds, elems) = arrayElementsDisclosure(disclosable)
            val json = JsonArray(elems.map { it.toJsonElement() })
            val (ds2, dig) = disclosureOf(json)
            disclosures += (ds + ds2)
            plainOrDigestElements += PlainOrDigest.Dig(dig)
        }

        fun DisclosableElement.encode() {
            val (disclosable, element) = this
            when (element) {
                is DisclosableValue.Json -> when (disclosable) {
                    Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                    Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
                }

                is DisclosableValue.Obj -> when (disclosable) {
                    Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                    Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
                }

                is DisclosableValue.Arr -> when (disclosable) {
                    Disclosable.Always -> encodeAlwaysDisclosable(element.value)
                    Disclosable.Selectively -> encodeSelectivelyDisclosable(element.value)
                }
            }
        }
        array.forEach { it.encode() }
        return disclosures to plainOrDigestElements
    }

    companion object {

        /**
         * A default [SdJwtFactory] with the following options set
         * - SHA_256 hash algorithm
         * - [SaltProvider.Default]
         * - [DecoyGen.Default]
         * - No hint for [SdJwtFactory.fallbackMinimumDigests]
         */
        val Default: SdJwtFactory =
            SdJwtFactory(HashAlgorithm.SHA_256, SaltProvider.Default, DecoyGen.Default, null)
    }
}

private sealed interface PlainOrDigest {
    data class Plain(val value: JsonElement) : PlainOrDigest
    data class Dig(val value: DisclosureDigest) : PlainOrDigest
}
