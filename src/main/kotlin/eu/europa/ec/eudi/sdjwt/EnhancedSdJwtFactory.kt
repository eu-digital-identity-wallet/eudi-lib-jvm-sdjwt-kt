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

import eu.europa.ec.eudi.sdjwt.dsl.EnhancedFoldContext
import eu.europa.ec.eudi.sdjwt.dsl.PathAwareArrayFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.PathAwareObjectFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.foldWithContext
import eu.europa.ec.eudi.sdjwt.dsl.json.JsonElementDisclosableObject
import kotlinx.serialization.json.*

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
     *         and the associated disclosure elements, or an exception if the operation fails
     */
    fun createSdJwt(sdJwtSpec: JsonElementDisclosableObject): Result<SdJwt<JsonObject>> = runCatching {
        val result = sdJwtSpec.foldWithContext(
            objectHandlers = objectHandlers,
            arrayHandlers = arrayHandlers,
            initialContext = EnhancedFoldContext(
                path = emptyList(),
                result = JsonObject(emptyMap()),
                metadata = SdJwtMetadata(emptyList()),
            ),
            combine = ::combineResults,
            postProcess = ::postProcess,
        )

        val (jwtClaimSet, disclosures) = result.result to result.metadata.disclosures
        val finalJwtClaimSet = addHashAlgClaim(jwtClaimSet, disclosures)

        SdJwt(finalJwtClaimSet, disclosures)
    }

    /**
     * Metadata class to track disclosures during fold operation.
     * This class is used as the metadata type in the EnhancedFoldContext.
     *
     * @property disclosures The list of disclosures generated during the fold operation
     */
    private data class SdJwtMetadata(
        val disclosures: List<Disclosure>,
    )

    /**
     * Combines results from different branches of the fold operation.
     * This function is used as the combine function in the fold operation.
     *
     * @param acc The accumulated result so far
     * @param current The result of the current element being processed
     * @return A new context that combines the accumulated and current results
     */
    private fun combineResults(
        acc: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        current: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
    ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
        // Merge JSON objects
        val mergedJson = JsonObject(acc.result + current.result)

        // Merge SD claims if present in both objects
        val accSdClaims = acc.result[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())
        val currentSdClaims = current.result[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())

        val mergedResult = if (accSdClaims.isNotEmpty() || currentSdClaims.isNotEmpty()) {
            val mergedSdClaims = JsonArray(accSdClaims + currentSdClaims)
            JsonObject(mergedJson + (SdJwtSpec.CLAIM_SD to mergedSdClaims))
        } else {
            mergedJson
        }

        // Merge disclosures
        val mergedDisclosures = acc.metadata.disclosures + current.metadata.disclosures

        return EnhancedFoldContext(
            path = acc.path,
            result = mergedResult,
            metadata = SdJwtMetadata(mergedDisclosures),
        )
    }

    /**
     * Post-processes the fold result to add decoy digests.
     * This function is used as the post-process function in the fold operation.
     * It adds decoy digests to the SD claims based on the minimum digest requirements.
     *
     * @param context The context resulting from the fold operation
     * @return A new context with decoy digests added to the result
     */
    private fun postProcess(
        context: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
    ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
        val result = context.result
        val sdClaims = result[SdJwtSpec.CLAIM_SD]?.jsonArray ?: JsonArray(emptyList())

        // No need to add decoys if there are no SD claims
        if (sdClaims.isEmpty()) {
            return context
        }

        // Add decoys if needed based on the minimum digest requirements
        val digests = sdClaims.map { it.jsonPrimitive.content }
        val decoys = genDecoys(digests.size, fallbackMinimumDigests).map { JsonPrimitive(it.value) }

        // Sort the combined list of digests and decoys to make the order unpredictable
        val digestsAndDecoys = (sdClaims + decoys).sortedBy { it.jsonPrimitive.contentOrNull }
        val resultWithDecoys = if (digestsAndDecoys.isNotEmpty()) {
            JsonObject(result + (SdJwtSpec.CLAIM_SD to JsonArray(digestsAndDecoys)))
        } else {
            result
        }

        return EnhancedFoldContext(
            path = context.path,
            result = resultWithDecoys,
            metadata = context.metadata,
        )
    }

    // Object handlers for the fold operation
    private val objectHandlers = object : PathAwareObjectFoldHandlers<String, JsonElement, JsonObject, SdJwtMetadata> {
        override fun ifAlwaysSelectivelyDisclosableId(
            path: List<String>,
            key: String,
            value: JsonElement,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // Generate disclosure for selectively disclosed primitive
            val (disclosure, digest) = objectPropertyDisclosure(key to value)
            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return EnhancedFoldContext(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(listOf(disclosure)),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableArr(
            path: List<String>,
            key: String,
            foldedArrayResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The array has already been processed, now we need to make the whole array selectively disclosable
            val arrayJson = foldedArrayResult.result
            val (disclosure, digest) = objectPropertyDisclosure(key to arrayJson)

            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return EnhancedFoldContext(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableObj(
            path: List<String>,
            key: String,
            foldedObjectResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The object has already been processed, now we need to make the whole object selectively disclosable
            val objJson = foldedObjectResult.result
            val (disclosure, digest) = objectPropertyDisclosure(key to objJson)

            val sdClaim = JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(listOf(JsonPrimitive(digest.value)))))

            return EnhancedFoldContext(
                path = path,
                result = sdClaim,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifNeverSelectivelyDisclosableId(
            path: List<String>,
            key: String,
            value: JsonElement,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // Simply include the claim directly in the JWT
            val plainClaim = JsonObject(mapOf(key to value))

            return EnhancedFoldContext(
                path = path,
                result = plainClaim,
                metadata = SdJwtMetadata(emptyList()),
            )
        }

        override fun ifNeverSelectivelyDisclosableArr(
            path: List<String>,
            key: String,
            foldedArrayResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The array has already been processed, now we need to include it directly in the JWT
            val arrayClaim = JsonObject(mapOf(key to foldedArrayResult.result))

            return EnhancedFoldContext(
                path = path,
                result = arrayClaim,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures),
            )
        }

        override fun ifNeverSelectivelyDisclosableObj(
            path: List<String>,
            key: String,
            foldedObjectResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The object has already been processed, now we need to include it directly in the JWT
            val objClaim = JsonObject(mapOf(key to foldedObjectResult.result))

            return EnhancedFoldContext(
                path = path,
                result = objClaim,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures),
            )
        }
    }

    // Array handlers for the fold operation
    private val arrayHandlers = object : PathAwareArrayFoldHandlers<String, JsonElement, JsonObject, SdJwtMetadata> {
        override fun ifAlwaysSelectivelyDisclosableId(
            path: List<String>,
            index: Int,
            value: JsonElement,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // Generate disclosure for selectively disclosed array element
            val (disclosure, digest) = arrayElementDisclosure(value)

            // For array elements, we return a JsonObject with the _sd_alg claim
            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return EnhancedFoldContext(
                path = path,
                result = digestObj,
                metadata = SdJwtMetadata(listOf(disclosure)),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableArr(
            path: List<String>,
            index: Int,
            foldedArrayResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The nested array has already been processed, now we need to make it selectively disclosable
            val arrayJson = foldedArrayResult.result
            val (disclosure, digest) = arrayElementDisclosure(arrayJson)

            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return EnhancedFoldContext(
                path = path,
                result = digestObj,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifAlwaysSelectivelyDisclosableObj(
            path: List<String>,
            index: Int,
            foldedObjectResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The nested object has already been processed, now we need to make it selectively disclosable
            val objJson = foldedObjectResult.result
            val (disclosure, digest) = arrayElementDisclosure(objJson)

            val digestObj = JsonObject(mapOf("..." to JsonPrimitive(digest.value)))

            return EnhancedFoldContext(
                path = path,
                result = digestObj,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures + disclosure),
            )
        }

        override fun ifNeverSelectivelyDisclosableId(
            path: List<String>,
            index: Int,
            value: JsonElement,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // Simply include the value directly in the array
            return EnhancedFoldContext(
                path = path,
                result = value.asJsonObject(),
                metadata = SdJwtMetadata(emptyList()),
            )
        }

        override fun ifNeverSelectivelyDisclosableArr(
            path: List<String>,
            index: Int,
            foldedArrayResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The nested array has already been processed, now we need to include it directly
            return EnhancedFoldContext(
                path = path,
                result = foldedArrayResult.result,
                metadata = SdJwtMetadata(foldedArrayResult.metadata.disclosures),
            )
        }

        override fun ifNeverSelectivelyDisclosableObj(
            path: List<String>,
            index: Int,
            foldedObjectResult: EnhancedFoldContext<String, JsonObject, SdJwtMetadata>,
        ): EnhancedFoldContext<String, JsonObject, SdJwtMetadata> {
            // The nested object has already been processed, now we need to include it directly
            return EnhancedFoldContext(
                path = path,
                result = foldedObjectResult.result,
                metadata = SdJwtMetadata(foldedObjectResult.metadata.disclosures),
            )
        }

        /**
         * Helper function to convert a JsonElement to a JsonObject.
         * This is used when handling array elements that need to be represented as objects.
         *
         * @return A JsonObject representation of the element:
         *         - If the element is already a JsonObject, it is returned as is
         *         - If the element is a JsonPrimitive, it is wrapped in an empty JsonObject
         *         - If the element is a JsonArray, it is wrapped in an empty JsonObject
         *         - For JsonNull, an empty JsonObject is returned
         */
        private fun JsonElement.asJsonObject(): JsonObject {
            return when (this) {
                is JsonObject -> this
                is JsonPrimitive -> JsonObject(mapOf("value" to this))
                is JsonArray -> JsonObject(mapOf("items" to this))
                is JsonNull -> JsonObject(emptyMap())
            }
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
        val numOfDecoys = min - disclosureDigests
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
