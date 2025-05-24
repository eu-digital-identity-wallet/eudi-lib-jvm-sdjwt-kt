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
package eu.europa.ec.eudi.sdjwt.dsl.meta

import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.not
import eu.europa.ec.eudi.sdjwt.dsl.unaryPlus
import eu.europa.ec.eudi.sdjwt.vc.*

fun ResolvedTypeMetadata.toDisclosableMetadataStructure(
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableObjectMetadata {
    val rootAttributeMetadata = AttributeMetadata(
        display = display.map { ClaimDisplay(it.lang, it.name, it.description) },
    )

    val allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>> =
        claims.groupBy { it.path.parent() }

    val topLevelClaims = allClaimsGroupedByParentPath[null] ?: emptyList()

    return parseObj(
        objMetadata = rootAttributeMetadata,
        childClaimsMetadatas = topLevelClaims,
        allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
    )
}

private fun parseObj(
    objMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableObjectMetadata {
    fun element(childClaimMetadata: ClaimMetadata): Pair<String, DisclosableElementMetadata> {
        // The last element of the child's path should be the name of the attribute in the object.
        val lastPathElement = childClaimMetadata.path.value.last()

        // This is a necessary safeguard: for object attributes, the last path element MUST be a Claim.
        check(lastPathElement is ClaimPathElement.Claim) {
            "Expected ClaimPathElement.Claim for object attribute name, but got $lastPathElement for path ${childClaimMetadata.path}"
        }
        val claimName = lastPathElement.name

        val disclosableElement = childClaimMetadata.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed,
        )
        return claimName to disclosableElement
    }

    val contentMap = childClaimsMetadatas.associate(::element)

    return DisclosableObjectMetadata(contentMap, objMetadata)
}

private fun parseArr(
    arrayMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableArrayMetadata {
    // This correctly maps to the last path element (e.g., ArrayElement(index) or AllArrayElements)
    // and sorts by index if available, placing AllArrayElements last.
    val distinctArrayChildElements = childClaimsMetadatas
        .map { it.path.value.last() }
        .distinct()
        .sortedBy {
            when (it) {
                is ClaimPathElement.ArrayElement -> it.index
                ClaimPathElement.AllArrayElements -> Int.MAX_VALUE // Sort AllArrayElements to the end
                else -> error(
                    "Unexpected ClaimPathElement type for array child: $it for path ${
                        childClaimsMetadatas.first { c ->
                            c.path.value.last() == it
                        }.path
                    }",
                )
            }
        }

    fun element(currentArrayElement: ClaimPathElement): DisclosableElementMetadata {
        val elementClaimMetadata = childClaimsMetadatas.first { it.path.value.last() == currentArrayElement }
        val disclosableElement = elementClaimMetadata.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed,
        )
        return disclosableElement
    }
    val contentList = distinctArrayChildElements.map(::element)

    return DisclosableArrayMetadata(contentList, arrayMetadata)
}

private fun ClaimMetadata.toDisclosableElementMetadata(
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>, // Pass this for recursive calls
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableElementMetadata {
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
        // currentClaimPath is a leaf (e.g., "house_number", "family_name")
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
        val constructedArray = parseArr(
            arrayMetadata = containerAttributeMetadata,
            childClaimsMetadatas = directChildrenClaims, // Pass the direct children
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        DisclosableValue.Arr(constructedArray)
    } else {
        val constructedObject = parseObj(
            objMetadata = containerAttributeMetadata,
            childClaimsMetadatas = directChildrenClaims, // Pass the direct children
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        DisclosableValue.Obj(constructedObject)
    }

    return disclosableValue to isCurrentNodeSelectivelyDisclosable
}
