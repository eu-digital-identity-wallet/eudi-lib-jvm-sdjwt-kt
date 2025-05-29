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
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.ClaimPathAwareArrayFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.ClaimPathAwareObjectFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.recreateClaims
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object DefinitionBasedSdJwtVcValidatorUsingFold : DefinitionBasedSdJwtVcValidator {
    override fun SdJwtDefinition.validate(
        jwtPayload: JsonObject,
        disclosures: List<Disclosure>,
    ): DefinitionBasedValidationResult {
        return credential(jwtPayload, disclosures).fold(
            onSuccess = { (reconstructedCredential, disclosuresPerClaim) ->
                val errors = validateCredential(reconstructedCredential, disclosuresPerClaim)
                DefinitionBasedValidationResult(errors)
            },
            onFailure = { disclosureError ->
                DefinitionBasedValidationResult(
                    listOf(DefinitionViolation.DisclosureInconsistencies(disclosureError)),
                )
            },
        )
    }
}

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
private typealias Validated = Folded<String, Unit, List<DefinitionViolation>>

private val Valid: Validated get() = Validated(emptyList(), Unit, emptyList())

/**
 * Add two Validated
 * This is called to combine the validation of two attribute definitions that belong
 * to the same object definition
 */
private operator fun Validated.plus(that: Validated): Validated =
    this.copy(result = this.result + that.result)

private operator fun ClaimPath?.plus(key: String): ClaimPath =
    this?.let { it + ClaimPathElement.Claim(key) }
        ?: ClaimPath.claim(key)

private fun checkIsObjectWithKnownAttributes(
    attributeClaimPath: ClaimPath?,
    jsonCtx: JsonObject,
    definition: DisclosableObject<String, Any?>,
): List<DefinitionViolation> {
    fun JsonObject.checkUnknownAttributes(): List<DefinitionViolation.UnknownClaim> {
        val unknownAttributeKeys = keys - definition.content.keys
        return unknownAttributeKeys.map {
            DefinitionViolation.UnknownClaim(attributeClaimPath + it)
        }
    }

    val errors = if (attributeClaimPath != null) {
        val json = with(SelectPath) { jsonCtx.select(attributeClaimPath) }.getOrThrow()
        when (json) {
            null -> emptyList()
            is JsonObject -> json.checkUnknownAttributes()
            else -> listOf(DefinitionViolation.WrongClaimType(attributeClaimPath))
        }
    } else {
        jsonCtx.checkUnknownAttributes()
    }

    return errors
}

private fun DisclosableObject<String, *>.validateCredential(
    reconstructedCredential: JsonObject,
    disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
): List<DefinitionViolation> {
    // Check if there are attributes  that do not have a definition
    val unknownAttributes =
        checkIsObjectWithKnownAttributes(null, reconstructedCredential, this)

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

    return definedAttributesErrors + unknownAttributes
}

private class ObjectDefinitionHandler(
    private val reconstructedCredential: JsonObject,
    private val disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
) : ClaimPathAwareObjectFoldHandlers<Any?, Unit, List<DefinitionViolation>>() {

    private fun presented(path: ClaimPath): JsonElement? =
        with(SelectPath.Default) { reconstructedCredential.select(path) }.getOrThrow()

    private fun checkIncorrectlyDisclosedAttribute(
        attributeClaimPath: ClaimPath,
        expectedAlwaysSD: Boolean,
    ): DefinitionViolation? =
        disclosuresPerClaim[attributeClaimPath]?.let { attributeDisclosures ->
            // attribute has been presented
            val isSelectivelyDisclosed =
                attributeDisclosures.lastOrNull()?.claim()?.first == attributeClaimPath.attributeClaim
            val incorrectlyDisclosed =
                (expectedAlwaysSD && !isSelectivelyDisclosed) || (!expectedAlwaysSD && isSelectivelyDisclosed)
            if (incorrectlyDisclosed) {
                DefinitionViolation.IncorrectlyDisclosedClaim(attributeClaimPath)
            } else null
        }

    private inline fun <reified T> checkIs(attributeClaimPath: ClaimPath): DefinitionViolation.WrongClaimType? =
        presented(attributeClaimPath)
            ?.takeIf { it !is T }
            ?.let { DefinitionViolation.WrongClaimType(attributeClaimPath) }

    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, Any?>>,
    ): Pair<Unit, List<DefinitionViolation>> {
        val expectedAlwaysSD = id is Disclosable.AlwaysSelectively
        val errors =
            buildList {
                checkIncorrectlyDisclosedAttribute(path, expectedAlwaysSD)?.let(::add)
            }
        return Unit to errors
    }

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, Any?>>,
        foldedArray: Validated,
    ): Pair<Unit, List<DefinitionViolation>> {
        val expectedAlwaysSD = array is Disclosable.AlwaysSelectively
        val errors = buildList {
            checkIs<JsonArray>(path)?.let(::add)
            checkIncorrectlyDisclosedAttribute(path, expectedAlwaysSD)?.let(::add)
            addAll(foldedArray.result)
        }
        return Unit to errors
    }

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, Any?>>,
        foldedObject: Validated,
    ): Pair<Unit, List<DefinitionViolation>> {
        val expectedAlwaysSD = obj is Disclosable.AlwaysSelectively
        val errors = buildList {
            addAll(checkIsObjectWithKnownAttributes(path, reconstructedCredential, obj.value.value))
            checkIncorrectlyDisclosedAttribute(path, expectedAlwaysSD)?.let(::add)
            addAll(foldedObject.result)
        }
        return Unit to errors
    }
}

private class ArrayDefinitionHandler(
    private val reconstructedCredential: JsonObject,
    private val disclosuresPerClaim: Map<ClaimPath, List<Disclosure>>,
) : ClaimPathAwareArrayFoldHandlers<Any?, Unit, List<DefinitionViolation>>() {

    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, Any?>>,
    ): Pair<Unit, List<DefinitionViolation>> = Unit to emptyList()

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, Any?>>,
        foldedArray: Validated,
    ): Pair<Unit, List<DefinitionViolation>> {
        return Unit to emptyList()
    }

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, Any?>>,
        foldedObject: Validated,
    ): Pair<Unit, List<DefinitionViolation>> {
        return Unit to emptyList()
    }
}
