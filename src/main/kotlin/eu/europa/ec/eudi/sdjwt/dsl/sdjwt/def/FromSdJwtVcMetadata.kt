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
package eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def

import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.not
import eu.europa.ec.eudi.sdjwt.dsl.unaryPlus
import eu.europa.ec.eudi.sdjwt.vc.*

/**
 * Transforms a [ResolvedTypeMetadata] into a [SdJwtObjectDefinition]
 *
 * A [ResolvedTypeMetadata] represents the outcome of resolving the references
 * of a [SdJwtVcTypeMetadata], thus it is a flat set of descriptions, suitable for serialization.
 *
 * On the other hand, [SdJwtObjectDefinition] is a hierarchical structure for
 * describing the disclosure and display properties of a credential
 *
 * @param sdJwtVcMetadata the SD-JWT-VC metadata to use
 * @param selectivelyDiscloseWhenAllowed
 */
fun SdJwtDefinition.Companion.fromSdJwtVcMetadata(
    sdJwtVcMetadata: ResolvedTypeMetadata,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): SdJwtDefinition {
    val allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>> =
        sdJwtVcMetadata.claims.groupBy { it.path.parent() }
    val topLevelClaims = allClaimsGroupedByParentPath[null] ?: emptyList()
    return processObjectDefinitionAndThen(
        childClaimsMetadatas = topLevelClaims,
        allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
    ) { content ->
        val vctMetadata =
            VctMetadata(
                vct = sdJwtVcMetadata.vct,
                name = sdJwtVcMetadata.name,
                description = sdJwtVcMetadata.description,
                display = sdJwtVcMetadata.display,
            )
        SdJwtDefinition(content, vctMetadata)
    }
}

private fun <DO> processObjectDefinitionAndThen(
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
    constructor: (Map<String, SdJwtElementDefinition>) -> DO,
): DO {
    fun metaOf(childMeta: ClaimMetadata): Pair<String, SdJwtElementDefinition> {
        // The last element of the child's path should be the name of the attribute in the object.
        val lastPathElement = childMeta.path.value.last()

        // This is a necessary safeguard: for object attributes, the last path element MUST be a Claim.
        check(lastPathElement is ClaimPathElement.Claim) {
            "Expected ClaimPathElement.Claim for object attribute name, but got $lastPathElement for path ${childMeta.path}"
        }
        val claimName = lastPathElement.name

        val disclosableElement = childMeta.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed,
        )
        return claimName to disclosableElement
    }

    val contentMap = childClaimsMetadatas.associate(::metaOf)
    return constructor(contentMap)
}

private fun processObjectDefinition(
    objMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtObjectDefinition = processObjectDefinitionAndThen(
    childClaimsMetadatas,
    allClaimsGroupedByParentPath,
    selectivelyDiscloseWhenAllowed,
) { content ->
    SdJwtObjectDefinition(content, objMetadata)
}

private fun processArrayDefinition(
    arrayMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtArrayDefinition {
    val distinctArrayChildElements =
        childClaimsMetadatas.map { it.path.value.last() }.distinct()

    fun metaOf(e: ClaimPathElement): SdJwtElementDefinition {
        val elementClaimMetadata = childClaimsMetadatas.first { it.path.value.last() == e }
        val disclosableElement = elementClaimMetadata.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed,
        )
        return disclosableElement
    }

    val contentList = distinctArrayChildElements.map(::metaOf)

    return SdJwtArrayDefinition(contentList, arrayMetadata)
}

private fun ClaimMetadata.toDisclosableElementMetadata(
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtElementDefinition {
    val (nestedDisclosableValue, isSelectivelyDisclosable) = buildNestedDisclosableValue(
        currentClaimPath = this.path, // The claim path this metadata belongs to
        allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
    )
    return if (isSelectivelyDisclosable) +nestedDisclosableValue else !nestedDisclosableValue
}

private fun buildNestedDisclosableValue(
    currentClaimPath: ClaimPath,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): Pair<DisclosableValue<String, AttributeMetadata>, Boolean> {
    val currentClaimMetadata = allClaimsGroupedByParentPath[currentClaimPath.parent()]
        ?.firstOrNull { it.path == currentClaimPath }

    checkNotNull(currentClaimMetadata) {
        "ClaimMetadata not found for current path: $currentClaimPath. All intermediate paths must have a corresponding ClaimMetadata entry."
    }
    val isCurrentNodeSelectivelyDisclosable = when (currentClaimMetadata.selectivelyDisclosableOrDefault) {
        ClaimSelectivelyDisclosable.Always -> true
        ClaimSelectivelyDisclosable.Never -> false
        ClaimSelectivelyDisclosable.Allowed -> selectivelyDiscloseWhenAllowed
    }

    val directChildrenClaims = allClaimsGroupedByParentPath[currentClaimPath] ?: emptyList()

    if (directChildrenClaims.isEmpty()) {
        val attributeMetadata = AttributeMetadata(
            display = currentClaimMetadata.display?.toList(),
            svgId = currentClaimMetadata.svgId,
        )
        return DisclosableValue.Id<String, AttributeMetadata>(attributeMetadata) to isCurrentNodeSelectivelyDisclosable
    }

    val isNextLevelArray = directChildrenClaims.all { childCm ->
        childCm.path.value.last() is ClaimPathElement.AllArrayElements || childCm.path.value.last() is ClaimPathElement.ArrayElement
    }

    val containerAttributeMetadata = AttributeMetadata(
        display = currentClaimMetadata.display?.toList(),
        svgId = currentClaimMetadata.svgId,
    )

    val disclosableValue: DisclosableValue<String, AttributeMetadata> = if (isNextLevelArray) {
        val arrayDefinition = processArrayDefinition(
            arrayMetadata = containerAttributeMetadata,
            childClaimsMetadatas = directChildrenClaims, // Pass the direct children
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        DisclosableValue.Arr(arrayDefinition)
    } else {
        val objectDefinition = processObjectDefinition(
            objMetadata = containerAttributeMetadata,
            childClaimsMetadatas = directChildrenClaims, // Pass the direct children
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        DisclosableValue.Obj(objectDefinition)
    }

    return disclosableValue to isCurrentNodeSelectivelyDisclosable
}
