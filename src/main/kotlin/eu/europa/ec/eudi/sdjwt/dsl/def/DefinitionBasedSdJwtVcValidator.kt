/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.sdjwt.dsl.def

import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.Vct
import kotlinx.serialization.json.*

sealed interface DefinitionViolation {

    /**
     * Represents inconsistencies found in the provided disclosures that prevent
     * the successful reconstruction of claims. This includes issues like
     * non-unique disclosures, disclosures without matching digests, etc.
     *
     * @param cause the underlying exception that occurred during claim reconstruction.
     */
    data class DisclosureInconsistencies(
        val cause: Throwable,
    ) : DefinitionViolation

    /**
     * The SD-JWT contains in the payload a 'vct' that does not correspond to the 'vct' of the definition it is being
     * validated against.
     *
     * @param expected the expected 'vct', i.e. the 'vct' in of the definition
     * @param actual the 'vct' contained in the SD-JWT payload, if any
     */
    data class InvalidVct(
        val expected: Vct,
        val actual: String,
    ) : DefinitionViolation

    /**
     * The SD-JWT is missing from its payload a required claim.
     *
     * @param claimPath the claim path of the missing claim
     */
    data class MissingRequiredClaim(
        val claimPath: ClaimPath,
    ) : DefinitionViolation

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * a claim which is not included in the container definition
     *
     * @param claimPath the claim path of the unknown claim
     */
    data class UnknownClaim(
        val claimPath: ClaimPath,
    ) : DefinitionViolation

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * a claim that is defined as an object, yet it was presented as an array
     * and vice versa
     *
     * @param claimPath the claim path of the claim that has incorrect type
     */
    data class WrongClaimType(
        val claimPath: ClaimPath,
    ) : DefinitionViolation

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * a claim which according to the container definition is not
     * correctly disclosed.
     *
     * For instance, a claim is expected to be always selectively disclosable,
     * yet it was found to be disclosed as never selectively disclosed, or vise versa
     *
     * @param claimPath the claim path of the incorrectly disclosed claim
     */
    data class IncorrectlyDisclosedClaim(
        val claimPath: ClaimPath,
    ) : DefinitionViolation
}

/**
 * The validation result of an SD-JWT VC against an SdJwtDefinition.
 */
sealed interface DefinitionBasedValidationResult {

    /**
     * The SD-JWT VC was successfully validated.
     *
     * @property recreatedCredential the processed/recreated payload of the successfully validated SD-JWT VC
     * @property disclosuresPerClaimPath disclosures per claim path
     */
    data class Valid(
        val recreatedCredential: JsonObject,
        val disclosuresPerClaimPath: DisclosuresPerClaimPath,
    ) : DefinitionBasedValidationResult

    /**
     * The SD-JWT VC could not be successfully validated.
     *
     * @property errors the definition violations that were detected
     */
    data class Invalid(
        val errors: List<DefinitionViolation>,
    ) : DefinitionBasedValidationResult {
        constructor(
            head: DefinitionViolation,
            vararg tail: DefinitionViolation,
        ) : this(listOf(head, *tail))

        init {
            require(errors.isNotEmpty()) { "errors must not be empty" }
        }
    }
}

interface DefinitionBasedSdJwtVcValidator {

    /**
     * Validates an issued SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
     *
     * This validation can be performed by a wallet, right after credential issuance. In this case,
     * the full list of [disclosures] is assumed, as provided by the SD-JWT-VC issuer.
     *
     * @param jwtPayload The JWT payload of an SD-JWT-VC
     * @param disclosures The list of disclosures related to the SD-JWT-VC.
     * @receiver The definition of the SD-JWT-VC credential against which the given [jwtPayload] and [disclosures]
     * will be validated
     */
    fun DisclosableDefObject<String, AttributeMetadata>.validateIssuance(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult

    /**
     * Validates a presented SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
     *
     * This validation can be performed by a verifier, right after receiving a presentation
     * of the SD-JWT-VC from the wallet. In this case, [disclosures] can be even empty.
     *
     * @param jwtPayload The JWT payload of an SD-JWT-VC
     * @param disclosures The list of disclosures related to the SD-JWT-VC.
     * @receiver The definition of the SD-JWT-VC credential against which the given [jwtPayload] and [disclosures]
     * will be validated
     */
    fun DisclosableDefObject<String, AttributeMetadata>.validatePresentation(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult

    /**
     * Validates an issued SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
     *
     * The method performs validation by verifying the required claims in the JWT payload and
     * ensuring that they conform to the definition. In this case, the full list of [disclosures] is assumed,
     * as provided by the SD-JWT-VC issuer.
     *
     * @param jwtPayload The JSON object representing the JWT payload of the issued SD-JWT-VC credential to validate.
     * @param disclosures A list of disclosures associated with the SD-JWT-VC credential. The list may vary depending
     * on whether the validation is performed by a wallet or a verifier.
     * @return A [DefinitionBasedValidationResult] object representing the result of the validation. It specifies
     * whether the validation was successful or not, and if not, includes a list of violations.
     */
    fun SdJwtDefinition.validateIssuanceSdJwtVc(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult =
        validate(jwtPayload, disclosures) { jwtPayload, disclosures ->
            validateIssuance(jwtPayload, disclosures)
        }

    /**
     * Validates a presented SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
     *
     * The method performs validation by verifying the required claims in the JWT payload and
     * ensuring that they conform to the definition. In this case, [disclosures] can be even empty.
     *
     * @param jwtPayload The JSON object representing the JWT payload of the presented SD-JWT-VC credential to validate.
     * @param disclosures A list of disclosures associated with the SD-JWT-VC credential. The list may vary depending
     * on whether the validation is performed by a wallet or a verifier.
     * @return A [DefinitionBasedValidationResult] object representing the result of the validation. It specifies
     * whether the validation was successful or not, and if not, includes a list of violations.
     */
    fun SdJwtDefinition.validatePresentationSdJwtVc(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult =
        validate(jwtPayload, disclosures) { jwtPayload, disclosures ->
            validatePresentation(jwtPayload, disclosures)
        }

    companion object : DefinitionBasedSdJwtVcValidator by SdJwtVcDefinitionValidator
}

//
// Implementation
//

private fun SdJwtDefinition.validate(
    jwtPayload: JsonObject,
    disclosures: List<Disclosure>,
    validate: DisclosableDefObject<String, AttributeMetadata>.(JsonObject, List<Disclosure>) -> DefinitionBasedValidationResult,
): DefinitionBasedValidationResult {
    val sdJwtVcViolations =
        buildList {
            fun requiredStringClaimAndThen(
                claimName: String,
                andThen: (String) -> Unit,
            ) {
                when (val claimValue = jwtPayload[claimName]) {
                    null, JsonNull -> {
                        add(DefinitionViolation.MissingRequiredClaim(ClaimPath.claim(claimName)))
                    }

                    else -> {
                        if (claimValue !is JsonPrimitive || !claimValue.isString) {
                            add(DefinitionViolation.WrongClaimType(ClaimPath.claim(claimName)))
                        } else {
                            andThen(claimValue.content)
                        }
                    }
                }
            }

            requiredStringClaimAndThen(SdJwtVcSpec.VCT) {
                if (it != metadata.vct.value) {
                    add(DefinitionViolation.InvalidVct(metadata.vct, it))
                }
            }

            val issuer = jwtPayload[RFC7519.ISSUER]
            if (null != issuer && issuer !is JsonNull && (issuer !is JsonPrimitive || !issuer.isString)) {
                add(DefinitionViolation.WrongClaimType(ClaimPath.claim(RFC7519.ISSUER)))
            }
        }

    val result = plusSdJwtVcNeverSelectivelyDisclosableClaims().validate(jwtPayload, disclosures)
    return if (sdJwtVcViolations.isNotEmpty()) {
        when (result) {
            is DefinitionBasedValidationResult.Invalid -> result.copy(errors = sdJwtVcViolations + result.errors)
            is DefinitionBasedValidationResult.Valid -> DefinitionBasedValidationResult.Invalid(sdJwtVcViolations)
        }
    } else {
        result
    }
}

/**
 * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
 *
 * The validation can be performed by a wallet, right after credential issuance, in which case
 * the full list of [Disclosure] it is assumed, as provided by the SD-JWT-VC issuer, or by a verifier
 * in which case the list of [Disclosure] can be even empty.
 */
@Suppress("ktlint:standard:max-line-length")
private class SdJwtVcDefinitionValidator private constructor(
    private val disclosuresPerClaimPath: DisclosuresPerClaimPath,
    private val definition: DisclosableDefObject<String, AttributeMetadata>,
    private val isIssuance: Boolean,
) {
    private val validateObject:
        DeepRecursiveFunction<Triple<ClaimPath?, JsonObject, DisclosableDefObject<String, AttributeMetadata>>, List<DefinitionViolation>> =
        DeepRecursiveFunction { (parent, current, definition) ->
            val objErrors = mutableListOf<DefinitionViolation>()

            val unknownClaims = current.keys - definition.content.keys
            objErrors.addAll(
                unknownClaims.map {
                    val unknownClaimPath = parent?.claim(it) ?: ClaimPath.claim(it)
                    DefinitionViolation.UnknownClaim(unknownClaimPath)
                },
            )

            val mandatoryClaims = definition.content.filter { (_, childDefinition) -> childDefinition.isMandatory(isIssuance) }.keys
            val missingClaims = mandatoryClaims - current.keys
            objErrors.addAll(
                missingClaims.map {
                    val missingClaimPath = parent?.claim(it) ?: ClaimPath.claim(it)
                    DefinitionViolation.MissingRequiredClaim(missingClaimPath)
                },
            )

            // iterate through the known claims and validate them
            current.filterKeys { it !in unknownClaims }.forEach { (claimName, claimValue) ->
                val claimPath = parent?.claim(claimName) ?: ClaimPath.claim(claimName)
                val claimDefinition =
                    checkNotNull(definition.content[claimName]) { "cannot find definition for $claimPath" }
                objErrors.addAll(validateDef.callRecursive(Triple(claimPath, claimValue, claimDefinition)))
            }
            objErrors
        }

    private val validateArray:
        DeepRecursiveFunction<Triple<ClaimPath, JsonArray, DisclosableDefArray<String, AttributeMetadata>>, List<DefinitionViolation>> =
        DeepRecursiveFunction { (parent, current, definition) ->
            val arrayErrors = mutableListOf<DefinitionViolation>()
            val elementDefinition = definition.content

            val mandatory = elementDefinition.isMandatory(isIssuance)
            if (mandatory && current.isEmpty()) {
                val missingClaimPath = parent.allArrayElements()
                arrayErrors.add(DefinitionViolation.MissingRequiredClaim(missingClaimPath))
            }

            current.withIndex().forEach { (claimIndex, claimValue) ->
                val claimPath = parent.arrayElement(claimIndex)
                arrayErrors.addAll(validateDef.callRecursive(Triple(claimPath, claimValue, elementDefinition)))
            }
            arrayErrors
        }

    private val validateDef:
        DeepRecursiveFunction<Triple<ClaimPath, JsonElement, DisclosableElementDefinition<String, AttributeMetadata>>, List<DefinitionViolation>> =
        DeepRecursiveFunction { (claimPath, claimValue, definition) ->
            val allErrors = mutableListOf<DefinitionViolation>()
            // check disclosability
            if (!definition.isProperlyDisclosed(claimPath)) {
                allErrors.add(DefinitionViolation.IncorrectlyDisclosedClaim(claimPath))
            }

            // check type and recurse as needed
            if (JsonNull != claimValue) {
                val claimErrors =
                    when (val claimDefinition = definition.value) {
                        is DisclosableDef.Id<String, AttributeMetadata> -> {
                            emptyList()
                        }

                        is DisclosableDef.Arr<String, AttributeMetadata> -> {
                            if (claimValue is JsonArray) {
                                validateArray.callRecursive(Triple(claimPath, claimValue, claimDefinition.value))
                            } else {
                                listOf(DefinitionViolation.WrongClaimType(claimPath))
                            }
                        }

                        is DisclosableDef.Obj<String, AttributeMetadata> -> {
                            if (claimValue is JsonObject) {
                                validateObject.callRecursive(Triple(claimPath, claimValue, claimDefinition.value))
                            } else {
                                listOf(DefinitionViolation.WrongClaimType(claimPath))
                            }
                        }
                    }
                allErrors.addAll(claimErrors)
            }
            allErrors
        }

    private fun validate(processedPayload: JsonObject): List<DefinitionViolation> {
        val processedPayloadWithoutWellKnown = JsonObject(processedPayload)
        return validateObject(Triple(null, processedPayloadWithoutWellKnown, definition))
    }

    private fun DisclosableElementDefinition<String, *>.isProperlyDisclosed(claim: ClaimPath): Boolean {
        val requiresDisclosures =
            run {
                val parentDisclosures =
                    claim
                        .parent()
                        ?.let {
                            checkNotNull(disclosuresPerClaimPath[it]) { "cannot find disclosures for $it" }
                        }.orEmpty()
                val claimDisclosures =
                    checkNotNull(disclosuresPerClaimPath[claim]) {
                        "cannot find disclosures for $claim"
                    }

                // if claim requires more disclosures than its parent, it is selectively disclosed
                claimDisclosures.size > parentDisclosures.size
            }

        return (this is Disclosable.AlwaysSelectively<*> && requiresDisclosures) ||
            (this is Disclosable.NeverSelectively<*> && !requiresDisclosures)
    }

    @Suppress("kotlin:S6516")
    companion object : DefinitionBasedSdJwtVcValidator {
        override fun DisclosableDefObject<String, AttributeMetadata>.validateIssuance(
            jwtPayload: JsonObject,
            disclosures: List<Disclosure>,
        ): DefinitionBasedValidationResult = validate(jwtPayload, disclosures, isIssuance = true)

        override fun DisclosableDefObject<String, AttributeMetadata>.validatePresentation(
            jwtPayload: JsonObject,
            disclosures: List<Disclosure>,
        ): DefinitionBasedValidationResult = validate(jwtPayload, disclosures, isIssuance = false)

        private fun DisclosableDefObject<String, AttributeMetadata>.validate(
            jwtPayload: JsonObject,
            disclosures: List<Disclosure>,
            isIssuance: Boolean,
        ): DefinitionBasedValidationResult =
            SdJwtRecreateClaimsOps.recreateClaimsAndDisclosuresPerClaim(jwtPayload, disclosures).fold(
                onFailure = { cause ->
                    val disclosureInconsistencies = DefinitionViolation.DisclosureInconsistencies(cause)
                    DefinitionBasedValidationResult.Invalid(disclosureInconsistencies)
                },
                onSuccess = { (recreatedCredential, disclosuresPerClaimPath) ->
                    val sdJwtVcDefinitionValidator = SdJwtVcDefinitionValidator(disclosuresPerClaimPath, this, isIssuance)
                    val violations = sdJwtVcDefinitionValidator.validate(recreatedCredential)
                    if (violations.isEmpty()) {
                        DefinitionBasedValidationResult.Valid(recreatedCredential, disclosuresPerClaimPath)
                    } else {
                        DefinitionBasedValidationResult.Invalid(violations)
                    }
                },
            )
    }
}

/**
 * Checks whether the claim to which this [DisclosableElementDefinition] corresponds to, is mandatory or not.
 *
 * This function considers whether the check is performed for an Issued or Presented SD-JWT/SD-JWT VC.
 *
 * During Issuance, a claim is mandatory if [AttributeMetadata.mandatory] is `true`.
 * During Presentation, a claim is mandatory if it is **Never Selectively Disclosable** and [AttributeMetadata.mandatory] is `true`.
 */
private fun DisclosableElementDefinition<*, AttributeMetadata>.isMandatory(isIssuance: Boolean): Boolean =
    if (isIssuance) {
        value.attributeMetadata().mandatory
    } else {
        (this is Disclosable.NeverSelectively) && value.attributeMetadata().mandatory
    }
