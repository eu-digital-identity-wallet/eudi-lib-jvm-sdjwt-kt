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

import eu.europa.ec.eudi.sdjwt.dsl.ArrayFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.Folded
import eu.europa.ec.eudi.sdjwt.dsl.ObjectFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.fold
import eu.europa.ec.eudi.sdjwt.dsl.json.JsonElementDisclosableObject
import kotlinx.serialization.json.*

/**
 * Metadata class to track disclosures during fold operation.
 * This class is used as the metadata type in the EnhancedFoldContext.
 *
 * @property disclosures The list of disclosures generated during the fold operation
 * @property minimumDigests The minimum number of digests required for this object
 */
private data class SdJwtMetadata(
    val disclosures: List<Disclosure>,
    val minimumDigests: MinimumDigests? = null,
)

/**
 * Combines results from different branches of the fold operation.
 * This function is used as the combine function in the fold operation for OBJECT PROPERTIES.
 *
 * @param acc The accumulated result so far
 * @param current The result of the current element being processed
 * @return A new context that combines the accumulated and current results
 */
private fun combineProcessed(
    acc: Folded<String, JsonElement, SdJwtMetadata>,
    current: Folded<String, JsonElement, SdJwtMetadata>,
): Folded<String, JsonElement, SdJwtMetadata> {
    // Merge JSON objects
    val mergedJson = JsonObject(acc.result.jsonObjectOrEmpty() + current.result.jsonObjectOrEmpty())

    // Merge SD claims if present in both objects
    val accSdClaims = acc.result.jsonObject[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())
    val currentSdClaims = current.result.jsonObject[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())

    val mergedResult = if (accSdClaims.isNotEmpty() || currentSdClaims.isNotEmpty()) {
        val mergedSdClaims = JsonArray(accSdClaims + currentSdClaims)
        JsonObject(mergedJson + (SdJwtSpec.CLAIM_SD to mergedSdClaims))
    } else {
        mergedJson
    }

    // Merge disclosures
    val mergedDisclosures = acc.metadata.disclosures + current.metadata.disclosures

    // Combine minimumDigests - use the non-null one, or if both are non-null, use the larger one
    val mergedMinimumDigests = when {
        acc.metadata.minimumDigests != null && current.metadata.minimumDigests != null ->
            if (acc.metadata.minimumDigests.value >= current.metadata.minimumDigests.value)
                acc.metadata.minimumDigests
            else
                current.metadata.minimumDigests
        acc.metadata.minimumDigests != null -> acc.metadata.minimumDigests
        else -> current.metadata.minimumDigests
    }

    return Folded(
        path = acc.path,
        result = mergedResult,
        metadata = SdJwtMetadata(mergedDisclosures, mergedMinimumDigests),
    )
}

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
 * of every [DisclosableObject]. It will be taken into account if there is not an explicitly
 * defined [hint][DisclosableObject.minimumDigests] for
 * this [DisclosableObject]. If not provided, decoys will be added only if there is a hint at [DisclosableObject] level.
 */
@Suppress("ktlint:standard:max-line-length")
class EnhancedSdJwtFactory(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val decoyGen: DecoyGen = DecoyGen.Default,
    private val fallbackMinimumDigests: MinimumDigests? = null,
) {
    /**
     * Creates an SD-JWT from the provided disclosable object specification.
     *
     * @param sdJwtSpec the specification of the SD-JWT, including claims and their associated disclosures
     * @return a [Result] containing the generated [SdJwt] with a [JsonObject] representing the JWT part
     * and the associated disclosure elements, or an exception if the operation fails
     */
    fun createSdJwt(sdJwtSpec: JsonElementDisclosableObject): Result<SdJwt<JsonObject>> = runCatching {
        val result: Folded<String, JsonElement, SdJwtMetadata> = sdJwtSpec.fold(
            objectHandlers = objectHandlers,
            arrayHandlers = arrayHandlers,
            initial = Folded(
                path = emptyList(),
                result = JsonObject(emptyMap()),
                metadata = SdJwtMetadata(disclosures = emptyList(), minimumDigests = sdJwtSpec.minimumDigests),
            ),
            combine = ::combineProcessed,
            arrayResultWrapper = ::arrayResultWrapper,
            arrayMetadataCombiner = ::arrayMetadataCombiner,
            postProcess = ::addDecoyDigests,
        )

        val (jwtClaimSet, disclosures) = result.result.jsonObjectOrEmpty() to result.metadata.disclosures
        val finalJwtClaimSet = addHashAlgClaim(jwtClaimSet, disclosures)

        SdJwt(finalJwtClaimSet, disclosures)
    }

    /**
     * Converts a list of individual element results (JsonElement) into the final result type for an array (JsonArray).
     * This is used by `foldArray`.
     */
    private fun arrayResultWrapper(elements: List<JsonElement>): JsonElement = JsonArray(elements)

    /**
     * Combines metadata from individual array elements into a single metadata for the array.
     * This is used by `foldArray`.
     */
    private fun arrayMetadataCombiner(metadataList: List<SdJwtMetadata>): SdJwtMetadata {
        val combinedDisclosures = metadataList.flatMap { it.disclosures }

        // Find the maximum minimumDigests value from all elements
        val combinedMinimumDigests = metadataList
            .mapNotNull { it.minimumDigests }
            .maxByOrNull { it.value }

        return SdJwtMetadata(combinedDisclosures, combinedMinimumDigests)
    }

    /**
     * Post-processes the fold result to add decoy digests.
     * This function is used as the post-process function in the fold operation.
     * It adds decoy digests to the SD claims based on the minimum digest requirements.
     *
     * @param folded The context resulting from the fold operation
     * @return A new context with decoy digests added to the result
     */
    private fun addDecoyDigests(
        folded: Folded<String, JsonElement, SdJwtMetadata>,
    ): Folded<String, JsonElement, SdJwtMetadata> {
        val result = folded.result.jsonObjectOrEmpty() // Ensure it's treated as an object for post-processing
        val sdClaims = result[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())

        // No need to add decoys if there are no SD claims
        if (sdClaims.isEmpty()) {
            return folded
        }

        // Add decoys if needed based on the minimum digest requirements
        val digests = sdClaims.map { it.jsonPrimitive.content }
        val decoys = genDecoys(digests.size, folded.metadata.minimumDigests).map { JsonPrimitive(it.value) }

        // Sort the combined list of digests and decoys to make the order unpredictable
        val digestsAndDecoys = (sdClaims + decoys).sortedBy { it.jsonPrimitive.contentOrNull }
        val resultWithDecoys = if (digestsAndDecoys.isNotEmpty()) {
            JsonObject(result + (SdJwtSpec.CLAIM_SD to JsonArray(digestsAndDecoys)))
        } else {
            result
        }

        return Folded(
            path = folded.path,
            result = resultWithDecoys,
            metadata = folded.metadata,
        )
    }

    private val objectHandlers = object : ObjectFoldHandlers<String, JsonElement, JsonElement, SdJwtMetadata> {
        override fun ifAlwaysSelectivelyDisclosableId(
            path: List<String?>,
            key: String,
            value: JsonElement,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // Generate disclosure for selectively disclosed primitive
            val (disclosure, digest) = objectPropertyDisclosure(key to value)
            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return Folded(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(listOf(disclosure)),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableArr(
            path: List<String?>,
            key: String,
            foldedArrayResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The array has already been processed, now we need to make the whole array selectively disclosable
            val arrayJson = foldedArrayResult.result.jsonArrayOrEmpty() // Expect the array wrapper to return JsonArray
            val (disclosure, digest) = objectPropertyDisclosure(key to arrayJson)

            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return Folded(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableObj(
            path: List<String?>,
            key: String,
            foldedObjectResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The object has already been processed, now we need to make the whole object selectively disclosable
            val objJson = foldedObjectResult.result.jsonObjectOrEmpty()
            val (disclosure, digest) = objectPropertyDisclosure(key to objJson)

            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return Folded(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifNeverSelectivelyDisclosableId(
            path: List<String?>,
            key: String,
            value: JsonElement,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // Simply include the claim directly in the JWT
            val plainClaim = JsonObject(mapOf(key to value))

            return Folded(
                path = path,
                result = plainClaim,
                metadata = SdJwtMetadata(emptyList()),
            )
        }

        override fun ifNeverSelectivelyDisclosableArr(
            path: List<String?>,
            key: String,
            foldedArrayResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The array has already been processed, now we need to include it directly in the JWT
            val arrayClaim = JsonObject(mapOf(key to foldedArrayResult.result.jsonArrayOrEmpty())) // Expect JsonArray
            return Folded(
                path = path,
                result = arrayClaim,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures),
            )
        }

        override fun ifNeverSelectivelyDisclosableObj(
            path: List<String?>,
            key: String,
            foldedObjectResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The object has already been processed, now we need to include it directly in the JWT
            val objClaim = JsonObject(mapOf(key to foldedObjectResult.result.jsonObjectOrEmpty()))
            return Folded(
                path = path,
                result = objClaim,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures),
            )
        }
    }

    // Array handlers for the fold operation
    // K is String, A is JsonElement, R is JsonElement, M is SdJwtMetadata
    private val arrayHandlers = object : ArrayFoldHandlers<String, JsonElement, JsonElement, SdJwtMetadata> {
        override fun ifAlwaysSelectivelyDisclosableId(
            path: List<String?>,
            index: Int,
            value: JsonElement,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // Generate disclosure for selectively disclosed array element
            val (disclosure, digest) = arrayElementDisclosure(value)
            // For array elements, we return a JsonObject with the _sd_alg claim
            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return Folded(
                path = path,
                result = digestObj, // Return the JsonObject representing the digest
                metadata = SdJwtMetadata(listOf(disclosure)),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableArr(
            path: List<String?>,
            index: Int,
            foldedArrayResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The nested array has already been processed, now we need to make it selectively disclosable
            val arrayJson = foldedArrayResult.result.jsonArrayOrEmpty() // Expect the array wrapper to return JsonArray
            val (disclosure, digest) = arrayElementDisclosure(arrayJson)

            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return Folded(
                path = path,
                result = digestObj, // Return the JsonObject representing the digest
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableObj(
            path: List<String?>,
            index: Int,
            foldedObjectResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The nested object has already been processed, now we need to make it selectively disclosable
            val objJson = foldedObjectResult.result.jsonObjectOrEmpty()
            val (disclosure, digest) = arrayElementDisclosure(objJson)

            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return Folded(
                path = path,
                result = digestObj, // Return the JsonObject representing the digest
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures + disclosure), // Add the new disclosure
            )
        }

        override fun ifNeverSelectivelyDisclosableId(
            path: List<String?>,
            index: Int,
            value: JsonElement,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // Crucial fix: Return the JsonPrimitive directly, not wrapped as a JsonObject
            return Folded(
                path = path,
                result = value, // THIS IS THE KEY CHANGE. Value is a JsonElement, not forced to JsonObject
                metadata = SdJwtMetadata(emptyList()),
            )
        }

        override fun ifNeverSelectivelyDisclosableArr(
            path: List<String?>,
            index: Int,
            foldedArrayResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The nested array has already been processed, now we need to include it directly
            return Folded(
                path = path,
                result = foldedArrayResult.result.jsonArrayOrEmpty(), // Expect JsonArray
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures),
            )
        }

        override fun ifNeverSelectivelyDisclosableObj(
            path: List<String?>,
            index: Int,
            foldedObjectResult: Folded<String, JsonElement, SdJwtMetadata>,
        ): Folded<String, JsonElement, SdJwtMetadata> {
            // The nested object has already been processed, now we need to include it directly
            return Folded(
                path = path,
                result = foldedObjectResult.result.jsonObjectOrEmpty(),
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures),
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
        else JsonObject(jwtClaimSet + (SdJwtSpec.CLAIM_SD_ALG to JsonPrimitive(hashAlgorithm.alias)))
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
         * A default [EnhancedSdJwtFactory] with the following options set
         * - SHA_256 hash algorithm
         * - [SaltProvider.Default]
         * - [DecoyGen.Default]
         * - No hint for [EnhancedSdJwtFactory.fallbackMinimumDigests]
         */
        val Default: EnhancedSdJwtFactory =
            EnhancedSdJwtFactory(HashAlgorithm.SHA_256, SaltProvider.Default, DecoyGen.Default, null)
    }
}

// Extension functions for safe casting
private fun JsonElement.jsonObjectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
private fun JsonElement.jsonArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
