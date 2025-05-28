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
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.Disclosure
import eu.europa.ec.eudi.sdjwt.SdJwtPresentationOps.Companion.disclosuresPerClaimVisitor
import eu.europa.ec.eudi.sdjwt.UnsignedSdJwt
import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.recreateClaims
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface SdJwtDefinitionCredentialValidationError {

    /**
     * Represents inconsistencies found in the provided disclosures that prevent
     * the successful reconstruction of claims. This includes issues like
     * non-unique disclosures, disclosures without matching digests, etc.
     *
     * @param cause The underlying exception that occurred during claim reconstruction.
     */
    data class DisclosureInconsistencies(val cause: Throwable) : SdJwtDefinitionCredentialValidationError

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * an attribute which is not included in the [containerDefinition].
     *
     * @param containerDefinition the definition of the object where the unknown attribute was found
     * @param attributeName The name of the unknown attribute
     */
    data class UnknownObjectAttribute(
        val claimPath: ClaimPath,
    ) : SdJwtDefinitionCredentialValidationError

    data class WrongAttributeType(
        val claimPath: ClaimPath,
    ) : SdJwtDefinitionCredentialValidationError

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * an attribute which according to the [containerDefinition] is not
     * correctly disclosed.
     *
     * For instance, an attribute is expected to be always selectively disclosable,
     * yet it was found to be disclosed as never selectively disclosed, or vise versa
     *
     * @param containerDefinition the definition of the object where the attribute was found
     * @param attributeName The name of the attribute
     */
    data class IncorrectlyDisclosedAttribute(
        val claimPath: ClaimPath,
    ) : SdJwtDefinitionCredentialValidationError
}

sealed interface SdJwtDefinitionValidationResult {
    data object Valid : SdJwtDefinitionValidationResult

    @JvmInline
    value class Invalid(val errors: List<SdJwtDefinitionCredentialValidationError>) : SdJwtDefinitionValidationResult {
        init {
            require(errors.isNotEmpty()) { "errors must not be empty" }
        }
    }
}

/**
 * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
 *
 * The validation can be performed by a wallet, right after issued the credential. In this case,
 * the full list of [disclosures] it is assumed, as provided by the SD-JWT-VC issuer.
 *
 * In addition, the validation can be performed by a verifier, right after received a presentation
 * of the SD-JWT-VC from the wallet. In this case, [disclosures] can be even empty
 *
 * @param jwtPayload The JWT payload of a presented SD-JWT-VC
 * @param disclosures The list of disclosures related to the SD-JWT-VC.
 * @receiver The definition of the SD-JWT-VC credential against which the given [jwtPayload] and [disclosures]
 * will be validated
 */
fun SdJwtDefinition.validateCredential(
    jwtPayload: JsonObject,
    disclosures: List<Disclosure>,
): SdJwtDefinitionValidationResult {
    val (reconstructedCredential, disclosuresPerClaim) = try {
        val disclosuresPerClaim = mutableMapOf<ClaimPath, List<Disclosure>>()
        val visitor = disclosuresPerClaimVisitor(disclosuresPerClaim)
        UnsignedSdJwt(jwtPayload, disclosures).recreateClaims(visitor) to disclosuresPerClaim.toMap()
    } catch (e: Exception) {
        return SdJwtDefinitionValidationResult.Invalid(
            listOf(
                SdJwtDefinitionCredentialValidationError.DisclosureInconsistencies(
                    e,
                ),
            ),
        )
    }
    return validateCredential2(jwtPayload, disclosures, reconstructedCredential, disclosuresPerClaim)
}

private data class ValidationContext(
    val jwtPayload: JsonObject,
    val disclosures: List<Disclosure>,
    val reconstructedCredential: JsonObject,
    val disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
)
private typealias Validated = Folded<String, Unit, List<SdJwtDefinitionCredentialValidationError>>

private fun DisclosableObject<String, *>.validateCredential2(
    jwtPayload: JsonObject,
    disclosures: List<Disclosure>,
    reconstructedCredential: JsonObject,
    disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
): SdJwtDefinitionValidationResult {
    val ctx = ValidationContext(jwtPayload, disclosures, reconstructedCredential, disclosuresPerClaim)
    val initialValidated = Validated(
        path = emptyList(),
        metadata = Unit,
        result = emptyList(),
    )
    val validated: Validated = fold(
        objectHandlers = ObjectDefinitionHandler(ctx),
        arrayHandlers = ArrayDefinitionHandler,
        initial = initialValidated,
        combine = { acc, current -> acc.copy(result = acc.result + current.result) },
        arrayResultWrapper = { it.flatten() },
        arrayMetadataCombiner = { it.first() },
    )
    val errors = validated.result
    return when (errors.size) {
        0 -> SdJwtDefinitionValidationResult.Valid
        else -> SdJwtDefinitionValidationResult.Invalid(errors)
    }
}

private class ObjectDefinitionHandler(
    private val ctx: ValidationContext,
) : ObjectFoldHandlers<String, Any?, Unit, List<SdJwtDefinitionCredentialValidationError>> {

    /**
     * Calculates the claim path of an attribute
     */
    private fun attributeClaimPath(path: List<String?>, key: String): ClaimPath = (path + key).toClaimPath()
    private val ClaimPath.attributeClaim: String
        get() {
            val lastClaim = value.last()
            check(lastClaim is ClaimPathElement.Claim) {
                "Not an attribute claim path: $this"
            }
            return lastClaim.name
        }

    private fun presented(path: ClaimPath): Result<JsonElement?> =
        with(SelectPath.Default) {
            ctx.reconstructedCredential.select(path)
        }

    private fun checkIncorrectlyDisclosedAttribute(
        attributeClaimPath: ClaimPath,
        expectedAlwaysSD: Boolean,
    ): SdJwtDefinitionCredentialValidationError? =
        ctx.disclosuresPerClaim[attributeClaimPath]?.let { attributeDisclosures ->
            // attribute has been presented
            val isSelectivelyDisclosed =
                attributeDisclosures.lastOrNull()?.claim()?.first == attributeClaimPath.attributeClaim
            val incorrectlyDisclosed =
                (expectedAlwaysSD && !isSelectivelyDisclosed) || (!expectedAlwaysSD && isSelectivelyDisclosed)
            if (incorrectlyDisclosed) {
                SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(attributeClaimPath)
            } else null
        }

    private inline fun <reified T> checkIs(attributeClaimPath: ClaimPath): SdJwtDefinitionCredentialValidationError.WrongAttributeType? =
        presented(attributeClaimPath).getOrThrow()
            ?.takeIf { it !is T }
            ?.let { SdJwtDefinitionCredentialValidationError.WrongAttributeType(attributeClaimPath) }

    private fun ifDisclosedId(
        path: List<String?>,
        key: String,
        value: Any?,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key)
        val errors =
            buildList {
                checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            }
        return Validated(path, Unit, errors)
    }

    private fun ifDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key)
        val errors = buildList {
            checkIs<JsonArray>(attributeClaimPath)?.let(::add)
            checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            addAll(foldedArray.result)
        }
        return Validated(path, Unit, errors)
    }

    private fun ifDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key)
        val errors = buildList {
            checkIs<JsonObject>(attributeClaimPath)?.let(::add)
            checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            addAll(foldedObject.result)
        }
        return Validated(path, Unit, errors)
    }

    override fun ifAlwaysSelectivelyDisclosableId(
        path: List<String?>,
        key: String,
        value: Any?,
    ): Validated = ifDisclosedId(path, key, value, expectedAlwaysSD = true)

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Validated,
    ): Validated = ifDisclosableArr(path, key, foldedArray, expectedAlwaysSD = true)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Validated,
    ): Validated = ifDisclosableObj(path, key, foldedObject, true)

    override fun ifNeverSelectivelyDisclosableId(
        path: List<String?>,
        key: String,
        value: Any?,
    ): Validated = ifDisclosedId(path, key, value, expectedAlwaysSD = false)

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Validated,
    ): Validated = ifDisclosableArr(path, key, foldedArray, expectedAlwaysSD = false)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Validated,
    ): Validated = ifDisclosableObj(path, key, foldedObject, expectedAlwaysSD = false)
}

private object ArrayDefinitionHandler :
    ArrayFoldHandlers<String, Any?, Unit, List<SdJwtDefinitionCredentialValidationError>> {

    private fun claimPath(parentPath: List<String?>): ClaimPath = parentPath.toClaimPath()

    override fun ifAlwaysSelectivelyDisclosableId(
        path: List<String?>,
        index: Int,
        value: Any?,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath  $value")
        return Validated(path, Unit, emptyList())
    }

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Validated,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath ")
        return Validated(path, Unit, emptyList())
    }

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Validated,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath ")
        return Validated(path, Unit, emptyList())
    }

    override fun ifNeverSelectivelyDisclosableId(
        path: List<String?>,
        index: Int,
        value: Any?,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath  $value")
        return Validated(path, Unit, emptyList())
    }

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Validated,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath  ")
        return Validated(path, Unit, emptyList())
    }

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Validated,
    ): Validated {
        val parentPath = claimPath(path)
        println("$parentPath ")
        return Validated(path, Unit, emptyList())
    }
}

private fun List<String?>.toClaimPath(): ClaimPath {
    require(isNotEmpty()) { "Path segments cannot be empty" }
    val head = requireNotNull(first()) { "First path segment must be an object key" }
    return drop(1).fold(ClaimPath.claim(head)) { path, claim ->
        when (claim) {
            null -> path.allArrayElements()
            else -> path.claim(claim)
        }
    }
}
//
// private fun SdJwtDefinition.validateCredential(
//    jwtPayload: JsonObject,
//    reconstructedCredential: JsonObject,
// ): SdJwtDefinitionValidationResult {
//    val allErrors = mutableListOf<SdJwtDefinitionCredentialValidationError>()
//
//    // Check for unknown attributes in the reconstructed credential
//    findUnknownAttributes(reconstructedCredential, this, null, allErrors)
//
//    // Check for incorrectly disclosed attributes
//    checkDisclosureConsistency(jwtPayload, this, allErrors)
//
//    return when {
//        allErrors.isEmpty() -> SdJwtDefinitionValidationResult.Valid
//        else -> SdJwtDefinitionValidationResult.Invalid(allErrors)
//    }
// }
//
// /**
// * Recursively finds unknown attributes in the reconstructed credential.
// *
// * @param currentData The current JSON object being checked
// * @param currentDefinition The definition of the current object
// * @param currentClaimPathPrefix The ClaimPath prefix to the current object
// * @param errors The list to add errors to
// */
// private fun findUnknownAttributes(
//    currentData: JsonObject,
//    currentDefinition: DisclosableObject<String, AttributeMetadata>,
//    currentClaimPathPrefix: ClaimPath?,
//    errors: MutableList<SdJwtDefinitionCredentialValidationError>,
// ) {
//    val knownAttributeNames = currentDefinition.content.keys
//
//    // Get all attribute names
//    val attributeNames = currentData.keys.toList()
//    for (attributeName in attributeNames) {
//        val attributeValue = currentData[attributeName]
//
//        // Skip SD-JWT internal claims
//        if (attributeName == SdJwtSpec.CLAIM_SD_ALG || attributeName == SdJwtSpec.CLAIM_SD) {
//            continue
//        }
//
//        // Construct the ClaimPath for the current attribute
//        val fullClaimPathForAttribute = currentClaimPathPrefix
//            ?.claim(attributeName)
//            ?: ClaimPath.claim(attributeName)
//
//        // If the attribute is not in the definition, it's unknown
//        if (attributeName !in knownAttributeNames) {
//            errors.add(
//                SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(
//                    currentDefinition,
//                    attributeName,
//                ),
//            )
//        } else if (attributeValue != null) {
//            // If it's a known attribute and it's an object, recurse
//            val definitionElement = currentDefinition.content[attributeName]
//            checkNotNull(definitionElement)
//            when (val dv = definitionElement.value) {
//                is DisclosableValue.Obj -> {
//                    if (attributeValue is JsonObject) {
//                        findUnknownAttributes(
//                            attributeValue,
//                            dv.value,
//                            fullClaimPathForAttribute,
//                            errors,
//                        )
//                    } else {
//                        errors.add(
//                            SdJwtDefinitionCredentialValidationError.WrongAttributeType(currentDefinition, attributeName),
//                        )
//                    }
//                }
//                is DisclosableValue.Arr -> {
//                    if (attributeValue is JsonArray) {
//                        // Handle arrays
//                    } else {
//                        errors.add(
//                            SdJwtDefinitionCredentialValidationError.WrongAttributeType(currentDefinition, attributeName),
//                        )
//                    }
//                }
//                is DisclosableValue.Id -> Unit // Leaf node, no further recursion needed
//            }
//        }
//    }
// }
//
// /**
// * Checks if attributes are correctly disclosed according to the definition.
// *
// * @param jwtPayload The original JWT payload
// * @param definition The definition to check against
// * @param errors The list to add errors to
// */
// private fun checkDisclosureConsistency(
//    jwtPayload: JsonObject,
//    definition: DisclosableObject<String, AttributeMetadata>,
//    errors: MutableList<SdJwtDefinitionCredentialValidationError>,
// ) {
//    for ((key, disclosable) in definition.content) {
//        val isAlwaysSelectively = disclosable is Disclosable.AlwaysSelectively<*>
//        val isNeverSelectively = disclosable is Disclosable.NeverSelectively<*>
//
//        // Check if the attribute is present in the JWT payload
//        val isInPayload = key in jwtPayload
//        val payloadValue = jwtPayload[key]
//        val isDigestInPayload = isInPayload &&
//            (payloadValue is JsonObject && payloadValue.get(SdJwtSpec.CLAIM_SD) != null)
//
//        if (isAlwaysSelectively) {
//            // If always selectively disclosable, it should not be directly in the payload
//            // or it should be a digest
//            if (isInPayload && !isDigestInPayload) {
//                errors.add(
//                    SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(
//                        definition,
//                        key,
//                    ),
//                )
//            }
//        } else if (isNeverSelectively) {
//            // If never selectively disclosable, it should be directly in the payload
//            // and not a digest
//            if (!isInPayload || isDigestInPayload) {
//                errors.add(
//                    SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(
//                        definition,
//                        key,
//                    ),
//                )
//            }
//        }
//
//        // Recurse into nested objects
//        when (val value = disclosable.value) {
//            is DisclosableValue.Obj -> {
//                if (isInPayload && !isDigestInPayload && payloadValue != null) {
//                    val nestedObject = payloadValue.jsonObject
//                    checkDisclosureConsistency(nestedObject, value.value, errors)
//                }
//            }
//            is DisclosableValue.Arr -> {
//                // Handle arrays if needed
//                // This is a simplified implementation
//            }
//            is DisclosableValue.Id -> {
//                // Leaf node, no further recursion needed
//            }
//        }
//    }
// }
