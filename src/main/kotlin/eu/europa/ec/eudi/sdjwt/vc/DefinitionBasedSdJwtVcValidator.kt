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

import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.SdJwtPresentationOps.Companion.disclosuresPerClaimVisitor
import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.AttributeMetadata
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import kotlinx.serialization.json.*

sealed interface DefinitionViolation {

    /**
     * Represents inconsistencies found in the provided disclosures that prevent
     * the successful reconstruction of claims. This includes issues like
     * non-unique disclosures, disclosures without matching digests, etc.
     *
     * @param cause the underlying exception that occurred during claim reconstruction.
     */
    data class DisclosureInconsistencies(val cause: Throwable) : DefinitionViolation

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
        val claimPath: ClaimPath?,
    ) : DefinitionViolation

    /**
     * The SD-JWT contains in the payload or in the given disclosures,
     * a claim which according to the container definition is not
     * correctly disclosed.
     *
     * For instance, a claim is expected to be always selectively disclosable,
     * yet it was found to be disclosed as never selectively disclosed, or vise versa
     *
     * @param claimPath the claim path of incorrectly disclosed claim
     */
    data class IncorrectlyDisclosedClaim(
        val claimPath: ClaimPath,
    ) : DefinitionViolation
}

sealed interface DefinitionBasedValidationResult {
    data object Valid : DefinitionBasedValidationResult

    @JvmInline
    value class Invalid(val errors: List<DefinitionViolation>) : DefinitionBasedValidationResult {
        constructor(
            head: DefinitionViolation,
            vararg tail: DefinitionViolation,
        ) : this(listOf(head, *tail))

        init {
            require(errors.isNotEmpty()) { "errors must not be empty" }
        }
    }

    companion object {
        operator fun invoke(errors: List<DefinitionViolation>): DefinitionBasedValidationResult =
            when (errors.size) {
                0 -> Valid
                else -> Invalid(errors)
            }

        operator fun invoke(vararg errors: DefinitionViolation): DefinitionBasedValidationResult =
            if (errors.isEmpty()) {
                Valid
            } else {
                Invalid(errors.toList())
            }
    }
}

fun interface DefinitionBasedSdJwtVcValidator {

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
    fun SdJwtDefinition.validate(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult

    companion object {
        val Default: DefinitionBasedSdJwtVcValidator = SdJwtVcDefinitionValidator

        val UsingFold: DefinitionBasedSdJwtVcValidator = DefinitionBasedSdJwtVcValidatorUsingFold
    }
}

/**
 * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
 *
 * The validation can be performed by a wallet, right after issued the credential. In this case,
 * the full list of [Disclosure] it is assumed, as provided by the SD-JWT-VC issuer.
 *
 * In addition, the validation can be performed by a verifier, right after receiving a presentation
 * of the SD-JWT-VC from the wallet. In this case, the list of [Disclosure] can be even empty
 */
private class SdJwtVcDefinitionValidator private constructor(
    private val disclosuresPerClaimPath: DisclosuresPerClaimPath,
    private val definition: SdJwtDefinition,
) {
    private val allErrors = mutableListOf<DefinitionViolation>()

    private val validateObject: DeepRecursiveFunction<Triple<ClaimPath?, JsonObject, DisclosableObject<String, AttributeMetadata>>, Unit> =
        DeepRecursiveFunction { (parent, current, definition) ->
            val unknownClaims = current.keys - definition.content.keys
            allErrors.addAll(
                unknownClaims.map {
                    val unknownAttributeClaimPath = parent?.claim(it) ?: ClaimPath.claim(it)
                    DefinitionViolation.UnknownClaim(unknownAttributeClaimPath)
                },
            )

            // iterate through the known claims and validate them
            current.filterKeys { it !in unknownClaims }.forEach { (claimName, claimValue) ->
                val claimPath = parent?.claim(claimName) ?: ClaimPath.claim(claimName)
                val claimDefinition =
                    checkNotNull(definition.content[claimName]) { "can find definition for $claimPath" }
                validate(claimPath, claimValue, claimDefinition)
            }
        }

    private val validateArray: DeepRecursiveFunction<Triple<ClaimPath, JsonArray, DisclosableArray<String, AttributeMetadata>>, Unit> =
        DeepRecursiveFunction { (parent, current, definition) ->
            // proceed only when array is uniform
            if (definition.content.size == 1) {
                val elementDefinition = definition.content.first()
                current.withIndex().forEach { (claimIndex, claimValue) ->
                    val claimPath = parent.arrayElement(claimIndex)
                    validate(claimPath, claimValue, elementDefinition)
                }
            }
        }

    private suspend fun DeepRecursiveScope<*, *>.validate(
        claimPath: ClaimPath,
        claimValue: JsonElement,
        definition: DisclosableElement<String, AttributeMetadata>,
    ) {
        // check disclosability
        if (!definition.isProperlyDisclosed(claimPath)) {
            allErrors.add(DefinitionViolation.IncorrectlyDisclosedClaim(claimPath))
        }

        // check type and recurse as needed
        if (!definition.isOfCorrectType(claimValue)) {
            allErrors.add(DefinitionViolation.WrongClaimType(claimPath))
        } else {
            when (val disclosableValue = definition.value) {
                is DisclosableValue.Obj -> validateObject.callRecursive(
                    Triple(
                        claimPath,
                        claimValue.jsonObject,
                        disclosableValue.value,
                    ),
                )

                is DisclosableValue.Arr -> validateArray.callRecursive(
                    Triple(
                        claimPath,
                        claimValue.jsonArray,
                        disclosableValue.value,
                    ),
                )

                is DisclosableValue.Id -> {
                    // nothing more to do
                }
            }
        }
    }

    private fun DisclosableElement<*, *>.isProperlyDisclosed(claim: ClaimPath): Boolean {
        val requiresDisclosures = run {
            val parentDisclosures = claim.parent()?.let {
                checkNotNull(disclosuresPerClaimPath[it]) { "can find disclosures for $it" }
            }.orEmpty()
            val claimDisclosures = checkNotNull(disclosuresPerClaimPath[claim]) {
                "can find disclosures for $claim"
            }

            // if claim requires more disclosures than its parent, it is selectively disclosed
            claimDisclosures.size > parentDisclosures.size
        }

        return (this is Disclosable.AlwaysSelectively<*> && requiresDisclosures) ||
            (this is Disclosable.NeverSelectively<*> && !requiresDisclosures)
    }

    private fun DisclosableElement<*, *>.isOfCorrectType(claimValue: JsonElement): Boolean =
        when (value) {
            is DisclosableValue.Obj -> claimValue is JsonObject
            is DisclosableValue.Arr -> claimValue is JsonArray
            is DisclosableValue.Id -> true
        }

    private fun validate(processedPayload: JsonObject): List<DefinitionViolation> {
        val processedPayloadWithoutWellKnown = JsonObject(processedPayload - wellKnownClaims)
        validateObject(Triple(null, processedPayloadWithoutWellKnown, definition))
        return allErrors
    }

    companion object : DefinitionBasedSdJwtVcValidator {

        // SdJwtSpec.CLAIM_SD, SdJwtSpec.CLAIM_SD_ALG, are not included because we work with processed payloads
        // TODO: check whether other well known claims must be added
        // TODO: verify that when present, the well known claims are never selectively disclosed
        private val wellKnownClaims: Set<String> = setOf(
            RFC7519.ISSUER,
            RFC7519.SUBJECT,
            RFC7519.AUDIENCE,
            RFC7519.EXPIRATION_TIME,
            RFC7519.NOT_BEFORE,
            RFC7519.ISSUED_AT,
            RFC7519.JWT_ID,
            SdJwtVcSpec.VCT,
            SdJwtVcSpec.VCT_INTEGRITY,
        )

        private fun validate(
            jwtPayload: JsonObject,
            disclosures: List<Disclosure>,
            definition: SdJwtDefinition,
        ): DefinitionBasedValidationResult =
            validate(UnsignedSdJwt(jwtPayload, disclosures), definition)

        private fun validate(sdJwt: UnsignedSdJwt, definition: SdJwtDefinition): DefinitionBasedValidationResult {
            val (processedPayload, disclosuresPerClaimPath) = runCatching {
                val disclosuresPerClaimPath = mutableMapOf<ClaimPath, List<Disclosure>>()
                val visitor = disclosuresPerClaimVisitor(disclosuresPerClaimPath)
                sdJwt.recreateClaims(visitor) to disclosuresPerClaimPath.toMap()
            }.getOrElse {
                return DefinitionBasedValidationResult.Invalid(DefinitionViolation.DisclosureInconsistencies(it))
            }

            val errors = SdJwtVcDefinitionValidator(disclosuresPerClaimPath, definition).validate(processedPayload)
            return if (errors.isEmpty()) {
                DefinitionBasedValidationResult.Valid
            } else {
                DefinitionBasedValidationResult.Invalid(errors)
            }
        }

        override fun SdJwtDefinition.validate(
            jwtPayload: JsonObject,
            disclosures: List<Disclosure>,
        ): DefinitionBasedValidationResult =
            validate(jwtPayload, disclosures, this@validate)
    }
}
