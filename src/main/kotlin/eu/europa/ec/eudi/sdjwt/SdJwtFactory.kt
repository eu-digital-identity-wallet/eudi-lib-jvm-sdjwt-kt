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

import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.values.*
import kotlinx.serialization.json.*

@JvmInline
value class MinimumDigests(val value: Int) {
    init {
        require(value > 0) { "value must be greater than zero." }
    }

    operator fun plus(that: MinimumDigests) = MinimumDigests(this.value + that.value)
}

fun Int?.atLeastDigests(): MinimumDigests? = this?.let { MinimumDigests(it) }

private typealias Disclosed = Folded<String, Disclosures, JsonElement>

/**
 * Metadata class to track disclosures during fold operation.
 * This class is used as the metadata type in the EnhancedFoldContext.
 *
 * @property disclosures The list of disclosures generated during the fold operation
 * @property minimumDigests The minimum number of digests required for this object
 */
private data class Disclosures(
    val disclosures: List<Disclosure> = emptyList(),
    val minimumDigests: MinimumDigests? = null,
) {
    companion object {

        fun combineObjectDisclosures(thisDisclosures: Disclosures, thatDisclosures: Disclosures): Disclosures {
            // Merge disclosures
            val mergedDisclosures = thisDisclosures.disclosures + thatDisclosures.disclosures
            // Combine minimumDigests - use the non-null one, or if both are non-null, use the larger one
            val mergedMinimumDigests = when {
                thisDisclosures.minimumDigests != null && thatDisclosures.minimumDigests != null ->
                    if (thisDisclosures.minimumDigests.value >= thatDisclosures.minimumDigests.value)
                        thisDisclosures.minimumDigests
                    else
                        thatDisclosures.minimumDigests

                thisDisclosures.minimumDigests != null -> thisDisclosures.minimumDigests
                else -> thatDisclosures.minimumDigests
            }
            return Disclosures(mergedDisclosures, mergedMinimumDigests)
        }

        fun combineArrayDisclosures(arrayDisclosures: List<Disclosures>): Disclosures {
            val combinedDisclosures = arrayDisclosures.flatMap { it.disclosures }

            // Find the maximum minimumDigests value from all elements
            val combinedMinimumDigests = arrayDisclosures
                .mapNotNull { it.minimumDigests }
                .maxByOrNull { it.value }

            return Disclosures(combinedDisclosures, combinedMinimumDigests)
        }
    }
}

private operator fun Disclosed.plus(that: Disclosed): Disclosed {
    // Merge JSON objects
    val mergedJson = JsonObject(this.result.jsonObject + that.result.jsonObject)
    // Merge SD claims if present in both objects
    val accSdClaims = this.result.jsonObject[RFC9901.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())
    val currentSdClaims = that.result.jsonObject[RFC9901.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())
    val mergedResult = if (accSdClaims.isNotEmpty() || currentSdClaims.isNotEmpty()) {
        val mergedSdClaims = JsonArray(accSdClaims + currentSdClaims)
        JsonObject(mergedJson + (RFC9901.CLAIM_SD to mergedSdClaims))
    } else {
        mergedJson
    }
    val disclosures = Disclosures.combineObjectDisclosures(this.metadata, that.metadata)

    return Folded(this.path, disclosures, mergedResult)
}

data class UnsignedSdJwt(val jwtPayload: JsonObject, val disclosures: List<Disclosure>)

/**
 * Factory for creating an unsigned JWT using the enhanced fold API.
 *
 * This is a drop-in replacement for [SdJwtFactory] that uses the enhanced fold API
 * for improved maintainability and flexibility.
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param decoyGen generates decoy digests
 * @param fallbackMinimumDigests This is an optional hint, that expresses the number of digests on the immediate level
 * of every [SdJwtObject]. It will be taken into account if there is not an explicitly
 * defined [hint][SdJwtObject.minimumDigests] for
 * this [SdJwtObject]. If not provided, decoys will be added only if there is a hint at [SdJwtObject] level.
 */
@Suppress("ktlint:standard:max-line-length")
class SdJwtFactory(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val decoyGen: DecoyGen = DecoyGen.Default,
    private val fallbackMinimumDigests: MinimumDigests? = null,
) {

    /**
     * Creates an SD-JWT from the provided disclosable object specification.
     *
     * @param sdJwtObject the specification of the SD-JWT, including claims and their associated disclosures
     * @return a [Result] containing the generated [SdJwt] with a [JsonObject] representing the JWT part
     * and the associated disclosure elements, or an exception if the operation fails
     */
    fun createSdJwt(sdJwtObject: SdJwtObject): Result<UnsignedSdJwt> = runCatchingCancellable {
        val disclosed = sdJwtObject.fold(
            objectHandlers = objectHandlers,
            arrayHandlers = arrayHandlers,
            combine = Disclosed::plus,
            arrayResultWrapper = ::JsonArray,
            arrayMetadataCombiner = Disclosures::combineArrayDisclosures,
            postProcess = ::addDecoyDigests,
        )

        val (jwtPayload, disclosures) = disclosed.result.jsonObject to disclosed.metadata.disclosures
        val finaJwtPayload = addHashAlgClaim(jwtPayload, disclosures)

        UnsignedSdJwt(finaJwtPayload, disclosures)
    }

    /**
     * Post-processes the fold result to add decoy digests.
     * This function is used as the post-process function in the fold operation.
     * It adds decoy digests to the SD claims based on the minimum digest requirements.
     *
     * @param folded The context resulting from the fold operation
     * @return A new context with decoy digests added to the result
     */
    private fun addDecoyDigests(folded: Disclosed): Disclosed {
        val minDigests = folded.metadata.minimumDigests ?: return folded

        return when (val result = folded.result) {
            is JsonObject -> {
                val sdClaims = result[RFC9901.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())

                // Add decoys if needed based on the minimum digest requirements
                val digests = sdClaims.map { it.jsonPrimitive.content }
                val decoys = genDecoys(digests.size, minDigests).map { JsonPrimitive(it.value) }

                if (decoys.isEmpty()) {
                    folded
                } else {
                    // Sort the combined list of digests and decoys to make the order unpredictable
                    val digestsAndDecoys = (sdClaims + decoys).sortedBy { it.jsonPrimitive.contentOrNull }
                    val foldedObjWithDecoys = JsonObject(result + (RFC9901.CLAIM_SD to JsonArray(digestsAndDecoys)))
                    folded.copy(result = foldedObjWithDecoys)
                }
            }

            is JsonArray -> {
                // For arrays, decoys are added as "..." elements
                val sdElements =
                    result.filter { it is JsonObject && it.containsKey(RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST) }

                val decoys = genDecoys(sdElements.size, minDigests).map { digest ->
                    buildJsonObject { put(RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST, digest.value) }
                }

                if (decoys.isEmpty()) {
                    folded
                } else {
                    val digestsAndDecoys = (result + decoys).sortedBy {
                        if (it is JsonObject) it[RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST]?.jsonPrimitive?.contentOrNull else null
                    }
                    folded.copy(result = JsonArray(digestsAndDecoys))
                }
            }

            else -> folded
        }
    }

    private val objectHandlers = object : ObjectFoldHandlers<String, JsonElement, Disclosures, JsonElement> {
        private fun disclosureDigestObj(digest: DisclosureDigest): JsonObject =
            buildJsonObject { putJsonArray(RFC9901.CLAIM_SD) { add(digest.value) } }

        override fun empty(obj: DisclosableObject<String, JsonElement>, path: List<String?>): Disclosed {
            val minDigests = (obj as? SdJwtObject)?.minimumDigests ?: fallbackMinimumDigests
            return Disclosed(
                path = path,
                result = JsonObject(emptyMap()),
                metadata = Disclosures(disclosures = emptyList(), minimumDigests = minDigests),
            )
        }

        override fun ifId(
            path: List<String?>,
            key: String,
            id: Disclosable<DisclosableValue.Id<String, JsonElement>>,
        ): Disclosed {
            val value = id.value.value
            return when (id) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Id<String, JsonElement>> -> {
                    // Generate disclosure for selectively disclosed primitive
                    val (disclosure, digest) = objectPropertyDisclosure(key to value)
                    Disclosed(
                        path = path,
                        metadata = Disclosures(listOf(disclosure)),
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Id<String, JsonElement>> -> {
                    Disclosed(
                        path = path,
                        metadata = Disclosures(),
                        result = buildJsonObject { put(key, value) },
                    )
                }
            }
        }

        override fun ifArray(
            path: List<String?>,
            key: String,
            array: Disclosable<DisclosableValue.Arr<String, JsonElement>>,
            foldedArray: Disclosed,
        ): Disclosed {
            return when (array) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Arr<String, JsonElement>> -> {
                    // The array has already been processed, now we need to make the whole array selectively disclosable
                    val arrayJson = foldedArray.result.jsonArray
                    val (disclosure, digest) = objectPropertyDisclosure(key to arrayJson)

                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedArray.metadata.disclosures + disclosure),
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Arr<String, JsonElement>> -> {
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedArray.metadata.disclosures),
                        result = buildJsonObject { put(key, foldedArray.result.jsonArray) },
                    )
                }
            }
        }

        override fun ifObject(
            path: List<String?>,
            key: String,
            obj: Disclosable<DisclosableValue.Obj<String, JsonElement>>,
            foldedObject: Disclosed,
        ): Disclosed {
            return when (obj) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Obj<String, JsonElement>> -> {
                    val (disclosure, digest) = run {
                        val objJson = foldedObject.result.jsonObject
                        objectPropertyDisclosure(key to objJson)
                    }

                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedObject.metadata.disclosures + disclosure),
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Obj<String, JsonElement>> -> {
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedObject.metadata.disclosures),
                        result = buildJsonObject { put(key, foldedObject.result.jsonObject) },
                    )
                }
            }
        }
    }

    private val arrayHandlers = object : ArrayFoldHandlers<String, JsonElement, Disclosures, JsonElement> {
        private fun disclosureDigestObj(digest: DisclosureDigest): JsonObject =
            buildJsonObject { put(RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST, digest.value) }

        override fun empty(arr: DisclosableArray<String, JsonElement>, path: List<String?>): Disclosed {
            val minDigests = (arr as? SdJwtArray)?.minimumDigests ?: fallbackMinimumDigests
            return Disclosed(
                path = path,
                result = JsonArray(emptyList()),
                metadata = Disclosures(disclosures = emptyList(), minimumDigests = minDigests),
            )
        }

        override fun ifId(
            path: List<String?>,
            index: Int,
            id: Disclosable<DisclosableValue.Id<String, JsonElement>>,
        ): Disclosed {
            val value = id.value.value
            return when (id) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Id<String, JsonElement>> -> {
                    // Generate disclosure for selectively disclosed array element
                    val (disclosure, digest) = arrayElementDisclosure(value)
                    Disclosed(
                        path = path,
                        metadata = Disclosures(listOf(disclosure)),
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Id<String, JsonElement>> ->
                    Disclosed(
                        path = path,
                        metadata = Disclosures(emptyList()),
                        result = value,
                    )
            }
        }

        override fun ifArray(
            path: List<String?>,
            index: Int,
            array: Disclosable<DisclosableValue.Arr<String, JsonElement>>,
            foldedArray: Disclosed,
        ): Disclosed =
            when (array) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Arr<String, JsonElement>> -> {
                    // The nested array has already been processed, now we need to make it selectively disclosable
                    val (disclosure, digest) = this.run {
                        val arrayJson = foldedArray.result.jsonArray
                        arrayElementDisclosure(arrayJson)
                    }
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedArray.metadata.disclosures + disclosure),
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Arr<String, JsonElement>> ->
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedArray.metadata.disclosures),
                        result = foldedArray.result.jsonArray,
                    )
            }

        override fun ifObject(
            path: List<String?>,
            index: Int,
            obj: Disclosable<DisclosableValue.Obj<String, JsonElement>>,
            foldedObject: Disclosed,
        ): Disclosed =
            when (obj) {
                is Disclosable.AlwaysSelectively<DisclosableValue.Obj<String, JsonElement>> -> {
                    // The nested object has already been processed, now we need to make it selectively disclosable
                    val (disclosure, digest) = this.run {
                        val objJson = foldedObject.result.jsonObject
                        arrayElementDisclosure(objJson)
                    }
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedObject.metadata.disclosures + disclosure), // Add the new disclosure
                        result = disclosureDigestObj(digest),
                    )
                }

                is Disclosable.NeverSelectively<DisclosableValue.Obj<String, JsonElement>> ->
                    Disclosed(
                        path = path,
                        metadata = Disclosures(foldedObject.metadata.disclosures),
                        result = foldedObject.result.jsonObject,
                    )
            }
    }

    // Helper functions

    /**
     * Adds the hash algorithm claim to the JWT claim set if disclosures are present.
     * This is required by the SD-JWT specification to indicate which hash algorithm
     * was used to create the disclosure digests.
     *
     * @param jwtClaimSet The JWT claim set to add the hash algorithm claim to
     * @param disclosures The list of disclosures
     * @return A new JWT claim set with the hash algorithm claim added if disclosures are present
     */
    private fun addHashAlgClaim(jwtClaimSet: JsonObject, disclosures: List<Disclosure>): JsonObject {
        return if (disclosures.isEmpty()) jwtClaimSet
        else JsonObject(jwtClaimSet + (RFC9901.CLAIM_SD_ALG to JsonPrimitive(hashAlgorithm.alias)))
    }

    /**
     * Generates decoy digests if needed based on the minimum digest requirements.
     * Decoys are used to prevent correlation of selectively disclosed claims by
     * ensuring a minimum number of digests are present in the SD-JWT.
     *
     * @param disclosureDigests The number of actual disclosure digests
     * @param minimumDigests The minimum number of digests required
     * @return A set of decoy digests to add to the SD-JWT
     */
    private fun genDecoys(disclosureDigests: Int, minimumDigests: MinimumDigests?): Set<DisclosureDigest> {
        val min = (minimumDigests ?: fallbackMinimumDigests)?.value ?: 0
        val numOfDecoys = (min - disclosureDigests).coerceAtLeast(0)
        return decoyGen.gen(hashAlgorithm, numOfDecoys)
    }

    /**
     * Creates a disclosure for an object property and calculates its digest.
     *
     * @param claim The claim to be disclosed, as a pair of claim name and value
     * @return A pair of the created disclosure and its digest
     */
    private fun objectPropertyDisclosure(claim: Pair<String, JsonElement>): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.objectProperty(saltProvider, claim).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    /**
     * Creates a disclosure for an array element and calculates its digest.
     *
     * @param element The array element to be disclosed
     * @return A pair of the created disclosure and its digest
     */
    private fun arrayElementDisclosure(element: JsonElement): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.arrayElement(saltProvider, element).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
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
