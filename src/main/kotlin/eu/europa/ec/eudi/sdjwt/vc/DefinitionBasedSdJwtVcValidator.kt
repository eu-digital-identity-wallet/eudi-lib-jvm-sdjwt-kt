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
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import kotlinx.serialization.json.JsonObject

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
    fun SdJwtDefinition.validate(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult

    companion object {
        val FoldBased: DefinitionBasedSdJwtVcValidator = FoldDefinitionBasedSdJwtVcValidator
        val ProcessedPayloadTraversalBased: DefinitionBasedSdJwtVcValidator = ProcessedPayloadTraversalDefinitionBasedSdJwtVcValidator
    }
}
