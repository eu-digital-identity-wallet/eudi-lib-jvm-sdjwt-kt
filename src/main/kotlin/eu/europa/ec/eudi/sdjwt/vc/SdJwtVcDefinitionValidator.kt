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
     * an attribute which is not included in the container definition
     *
     * @param claimPath The claim path of the unknown attribute
     */
    data class UnknownObjectAttribute(
        val claimPath: ClaimPath,
    ) : SdJwtDefinitionCredentialValidationError

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * an attribute that is defined as an object, yet it was presented as an array
     * and vice versa
     *
     * @param claimPath The claim path of the unknown attribute
     */
    data class WrongAttributeType(
        val claimPath: ClaimPath,
    ) : SdJwtDefinitionCredentialValidationError

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * an attribute which according to the container definition is not
     * correctly disclosed.
     *
     * For instance, an attribute is expected to be always selectively disclosable,
     * yet it was found to be disclosed as never selectively disclosed, or vise versa
     *
     * @param claimPath The claim path of the unknown attribute
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

    companion object {
        operator fun invoke(errors: List<SdJwtDefinitionCredentialValidationError>): SdJwtDefinitionValidationResult =
            when (errors.size) {
                0 -> Valid
                else -> Invalid(errors)
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
): SdJwtDefinitionValidationResult =
    credential(jwtPayload, disclosures).fold(
        onSuccess = { (reconstructedCredential, disclosuresPerClaim) ->
            val errors = validateCredential(reconstructedCredential, disclosuresPerClaim)
            SdJwtDefinitionValidationResult(errors)
        },
        onFailure = { disclosureError ->
            SdJwtDefinitionValidationResult(
                listOf(SdJwtDefinitionCredentialValidationError.DisclosureInconsistencies(disclosureError)),
            )
        },
    )

private fun credential(
    jwtPayload: JsonObject,
    disclosures: List<Disclosure>,
): Result<Pair<JsonObject, Map<ClaimPath, List<Disclosure>>>> = runCatching {
    val disclosuresPerClaim = mutableMapOf<ClaimPath, List<Disclosure>>()
    val visitor = disclosuresPerClaimVisitor(disclosuresPerClaim)
    UnsignedSdJwt(jwtPayload, disclosures).recreateClaims(visitor) to disclosuresPerClaim.toMap()
}

/**
 * K: The attribute key
 * M: The set of defined claims
 * R: List of errors
 */
private typealias Validated = Folded<String, Set<ClaimPath>, List<SdJwtDefinitionCredentialValidationError>>

private val Valid: Validated get() = Validated(emptyList(), emptySet(), emptyList())

/**
 * Add two Validated
 * This is called to combine the validation of two attribute definitions that belong
 * to the same object definition
 */
private operator fun Validated.plus(that: Validated): Validated =
    this.copy(result = this.result + that.result, metadata = this.metadata + that.metadata)

/**
 * Helper function.
 */
private fun JsonObject.unknownKeys(definedKeys: Set<String>) = keys - definedKeys

private fun DisclosableObject<String, *>.validateCredential(
    reconstructedCredential: JsonObject,
    disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
): List<SdJwtDefinitionCredentialValidationError> {
    // Traverse the definition of attributes
    // and identify errors of the presented credential
    val definedAttributesErrors = fold(
        objectHandlers = ObjectDefinitionHandler(reconstructedCredential, disclosuresPerClaim),
        arrayHandlers = ArrayDefinitionHandler(reconstructedCredential, disclosuresPerClaim),
        initial = Valid,
        combine = { v1, v2 -> v1 + v2 },
        arrayResultWrapper = { it.flatten() },
        arrayMetadataCombiner = { it.first() },
    ).result

    // Check if there are attributes  that do not have a definition
    val unknownAttributes =
        reconstructedCredential.unknownKeys(definedKeys = content.keys).map {
            SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(ClaimPath.claim(it))
        }

    return definedAttributesErrors + unknownAttributes
}

private class ObjectDefinitionHandler(
    private val reconstructedCredential: JsonObject,
    private val disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
) : ObjectFoldHandlers<String, Any?, Set<ClaimPath>, List<SdJwtDefinitionCredentialValidationError>> {

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
        with(SelectPath.Default) { reconstructedCredential.select(path) }

    private fun checkIncorrectlyDisclosedAttribute(
        attributeClaimPath: ClaimPath,
        expectedAlwaysSD: Boolean,
    ): SdJwtDefinitionCredentialValidationError? =
        disclosuresPerClaim[attributeClaimPath]?.let { attributeDisclosures ->
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

    private fun checkIsObject(
        attributeClaimPath: ClaimPath,
        definedAttributes: Set<ClaimPath>,
    ): List<SdJwtDefinitionCredentialValidationError> =
        presented(attributeClaimPath).getOrThrow()?.let { json ->
            when (json) {
                is JsonObject -> {
                    val presentedAttributes = json.keys.map { attributeClaimPath + ClaimPathElement.Claim(it) }
                    val unknownAttributes = presentedAttributes - definedAttributes
                    unknownAttributes.map { SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(it) }
                }

                else -> {
                    listOf(SdJwtDefinitionCredentialValidationError.WrongAttributeType(attributeClaimPath))
                }
            }
        }.orEmpty()

    private fun ifDisclosedId(
        path: List<String?>,
        key: String,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key).also { println(it) }
        val errors =
            buildList {
                checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            }
        return Validated(path, setOf(attributeClaimPath), errors)
    }

    private fun ifDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key).also { println(it) }
        val errors = buildList {
            checkIs<JsonArray>(attributeClaimPath)?.let(::add)
            checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            addAll(foldedArray.result)
        }
        return Validated(path, setOf(attributeClaimPath), errors)
    }

    private fun ifDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val attributeClaimPath = attributeClaimPath(path, key).also { println(it) }
        val errors = buildList {
            addAll(checkIsObject(attributeClaimPath, foldedObject.metadata))
            checkIncorrectlyDisclosedAttribute(attributeClaimPath, expectedAlwaysSD)?.let(::add)
            addAll(foldedObject.result)
        }
        return Validated(path, setOf(attributeClaimPath), errors)
    }

    override fun ifAlwaysSelectivelyDisclosableId(
        path: List<String?>,
        key: String,
        value: Any?,
    ): Validated = ifDisclosedId(path, key, expectedAlwaysSD = true)

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
    ): Validated = ifDisclosedId(path, key, expectedAlwaysSD = false)

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

private class ArrayDefinitionHandler(
    private val reconstructedCredential: JsonObject,
    private val disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
) : ArrayFoldHandlers<String, Any?, Set<ClaimPath>, List<SdJwtDefinitionCredentialValidationError>> {

    private fun claimPath(parentPath: List<String?>): ClaimPath = parentPath.toClaimPath()

    private fun ifDisclosableId(
        path: List<String?>,
        index: Int,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val parentPath = claimPath(path).also { println(it) }
        return Validated(path, emptySet(), emptyList())
    }

    private fun ifDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val parentPath = claimPath(path).also { println(it) }
        println("$parentPath ")
        return Validated(path, emptySet(), emptyList())
    }

    private fun ifDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Validated,
        expectedAlwaysSD: Boolean,
    ): Validated {
        val parentPath = claimPath(path).also { println(it) }
        println("$parentPath ")
        return Validated(path, emptySet(), emptyList())
    }

    override fun ifAlwaysSelectivelyDisclosableId(
        path: List<String?>,
        index: Int,
        value: Any?,
    ): Validated = ifDisclosableId(path, index, expectedAlwaysSD = true)

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Validated,
    ): Validated = ifDisclosableArr(path, index, foldedArray, expectedAlwaysSD = true)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Validated,
    ): Validated = ifDisclosableObj(path, index, foldedObject, expectedAlwaysSD = true)

    override fun ifNeverSelectivelyDisclosableId(
        path: List<String?>,
        index: Int,
        value: Any?,
    ): Validated = ifDisclosableId(path, index, expectedAlwaysSD = false)

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Validated,
    ): Validated = ifDisclosableArr(path, index, foldedArray, expectedAlwaysSD = false)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Validated,
    ): Validated = ifDisclosableObj(path, index, foldedObject, expectedAlwaysSD = false)
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
