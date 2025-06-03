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
package eu.europa.ec.eudi.sdjwt.dsl.def

import eu.europa.ec.eudi.sdjwt.dsl.not
import eu.europa.ec.eudi.sdjwt.dsl.unaryPlus
import eu.europa.ec.eudi.sdjwt.vc.*

fun SdJwtDefinition.Companion.fromSdJwtVcMetadataStrict(
    sdJwtVcMetadata: ResolvedTypeMetadata,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): SdJwtDefinition {
    val existingClaimsByPath: Map<ClaimPath, ClaimMetadata> = sdJwtVcMetadata.claims.associateBy { it.path }

    val allNodesChildrenMap: MutableMap<ClaimPath?, MutableList<ClaimPath>> = mutableMapOf()

    existingClaimsByPath.keys.forEach { claimPath ->
        var currentPath: ClaimPath? = claimPath
        while (currentPath != null) {
            val parentPath = currentPath.parent()
            allNodesChildrenMap.getOrPut(parentPath) { mutableListOf() }.add(currentPath)
            currentPath = parentPath // Move up, loop stops when parentPath is null
        }
    }

    // Ensure children lists are unique and sorted for consistent processing
    allNodesChildrenMap.forEach { (_, children) ->
        // Use a Set to ensure uniqueness, then sort
        val distinctSortedChildren = children.toSet().sortedWith(
            compareBy {
                // Sort by the last element's name/value for consistency
                it.value.lastOrNull()?.fold(
                    ifAllArrayElements = { "" },
                    ifArrayElement = { idx -> idx.toString() },
                    ifClaim = { name -> name },
                ) ?: ""
            },
        )
        children.clear()
        children.addAll(distinctSortedChildren)
    }

    // Identify top-level claims (those whose parent is null)
    val topLevelChildrenPaths = allNodesChildrenMap[null] ?: emptyList()

    // 3. Process the top-level object definition
    return processObjectDefinitionAndThenStrict(
        childPaths = topLevelChildrenPaths,
        existingClaimsByPath = existingClaimsByPath,
        allNodesChildrenMap = allNodesChildrenMap,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
    ) { content ->
        val vctMetadata = VctMetadata(
            vct = sdJwtVcMetadata.vct,
            name = sdJwtVcMetadata.name,
            description = sdJwtVcMetadata.description,
            display = sdJwtVcMetadata.display,
            schemas = sdJwtVcMetadata.schemas,
        )
        SdJwtDefinition(content, vctMetadata)
    }
}

/**
 * Processes an object definition recursively.
 * @param childPaths The paths of the direct children of the current object being processed.
 * @param existingClaimsByPath A map of all explicitly defined [ClaimMetadata] by their full path.
 * @param allNodesChildrenMap A comprehensive map of all inferred and explicit nodes in the hierarchy,
 * mapping a parent path to a list of its direct child paths.
 * @param selectivelyDiscloseWhenAllowed Controls behavior for "allowed" claims.
 * @param constructor A function to construct the final object definition type.
 */
private fun <DO> processObjectDefinitionAndThenStrict(
    childPaths: List<ClaimPath>,
    existingClaimsByPath: Map<ClaimPath, ClaimMetadata>,
    allNodesChildrenMap: Map<ClaimPath?, List<ClaimPath>>,
    selectivelyDiscloseWhenAllowed: Boolean,
    constructor: (Map<String, SdJwtElementDefinition>) -> DO,
): DO {
    val contentMap = childPaths.associate { childPath ->
        val lastPathElement = childPath.value.last()
        check(lastPathElement is ClaimPathElement.Claim) {
            "Expected ClaimPathElement.Claim for object attribute name, but got $lastPathElement for path $childPath"
        }
        val claimName = lastPathElement.name

        // Recursively build the nested element definition
        val disclosableElement = buildNestedDisclosableElementStrict(
            currentClaimPath = childPath,
            existingClaimsByPath = existingClaimsByPath,
            allNodesChildrenMap = allNodesChildrenMap,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        claimName to disclosableElement
    }
    return constructor(contentMap)
}

private fun processObjectDefinitionStrict(
    objMetadata: AttributeMetadata,
    childPaths: List<ClaimPath>,
    existingClaimsByPath: Map<ClaimPath, ClaimMetadata>,
    allNodesChildrenMap: Map<ClaimPath?, List<ClaimPath>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtObjectDefinition = processObjectDefinitionAndThenStrict(
    childPaths,
    existingClaimsByPath,
    allNodesChildrenMap,
    selectivelyDiscloseWhenAllowed,
) { content ->
    SdJwtObjectDefinition(content, objMetadata)
}

private fun processArrayDefinitionStrict(
    arrayMetadata: AttributeMetadata,
    arrayContainerClaimMetadata: ClaimMetadata?,
    arrayElementsClaimPath: ClaimPath,
    existingClaimsByPath: Map<ClaimPath, ClaimMetadata>,
    allNodesChildrenMap: Map<ClaimPath?, List<ClaimPath>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtArrayDefinition {
    val elementDefinitions =
        mutableSetOf<DisclosableElementDefinition<String, AttributeMetadata>>()

    // Get direct children of the array elements path (e.g., `degrees[*].type`, `degrees[*].university`)
    val arrayElementsChildrenPaths = allNodesChildrenMap[arrayElementsClaimPath] ?: emptyList()

    if (arrayElementsChildrenPaths.isEmpty()) {
        // Case: No further nested paths (e.g., only `degrees[null]` exists, no `degrees[null].type`)
        // Assumption: Array elements are primitives (DisclosableDef.Id)
        val elementMetadata = AttributeMetadata(
            display = arrayContainerClaimMetadata?.display?.toList(),
            svgId = arrayContainerClaimMetadata?.svgId,
        )
        // Check SD status of the `["my_array", null]` (or `[*]`) entry itself
        val elementSd = existingClaimsByPath[arrayElementsClaimPath]?.selectivelyDisclosableOrDefault
        val isElementSd = elementSd.let {
            when (it) {
                ClaimSelectivelyDisclosable.Always -> true
                ClaimSelectivelyDisclosable.Never -> false
                ClaimSelectivelyDisclosable.Allowed -> selectivelyDiscloseWhenAllowed
                null -> false // If no `sd` for `array[null]` (or `[*]`), assume Never for the element itself.
            }
        }
        val elementDef = DisclosableDef.Id<String, AttributeMetadata>(elementMetadata)
        elementDefinitions.add(if (isElementSd) +elementDef else !elementDef)
    } else {
        // Case: Nested paths exist (e.g., `degrees[*].type`, implying elements are objects/arrays)
        val disclosableElement = buildNestedDisclosableElementStrict(
            currentClaimPath = arrayElementsClaimPath,
            existingClaimsByPath = existingClaimsByPath,
            allNodesChildrenMap = allNodesChildrenMap,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
        )
        elementDefinitions.add(disclosableElement)
    }

    val content: DisclosableElementDefinition<String, AttributeMetadata> = when (elementDefinitions.size) {
        0 -> error("No definition found for array elements at path: $arrayElementsClaimPath")
        1 -> elementDefinitions.first()
        else -> +DisclosableDef.Alt(elementDefinitions)
    }
    return SdJwtArrayDefinition(content, arrayMetadata)
}

/**
 * Builds a [DisclosableElementDefinition] for a given claim, including its nested structure
 * and selective disclosability based on the provided [ClaimMetadata] and the strict assumptions.
 *
 * This function also determines the SD status of the current node.
 */
private fun buildNestedDisclosableElementStrict(
    currentClaimPath: ClaimPath,
    existingClaimsByPath: Map<ClaimPath, ClaimMetadata>,
    allNodesChildrenMap: Map<ClaimPath?, List<ClaimPath>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): SdJwtElementDefinition {
    // Get the ClaimMetadata for the current path, if it explicitly exists
    val currentClaimMetadata = existingClaimsByPath[currentClaimPath]

    // Determine the SD status for the current node itself
    val isCurrentNodeSelectivelyDisclosable = currentClaimMetadata?.selectivelyDisclosableOrDefault.let {
        when (it) {
            ClaimSelectivelyDisclosable.Always -> true
            ClaimSelectivelyDisclosable.Never -> false
            ClaimSelectivelyDisclosable.Allowed -> selectivelyDiscloseWhenAllowed
            // If the ClaimMetadata for the current path doesn't exist, assume Always disclosable
            // This is for inferred intermediate nodes (like the "degrees" array container itself)
            null -> true
        }
    }

    // Get direct children paths for the current path
    val directChildrenPaths = allNodesChildrenMap[currentClaimPath] ?: emptyList()

    val disclosableDef = if (directChildrenPaths.isEmpty()) {
        // This is a leaf node (primitive value)
        val attributeMetadata = AttributeMetadata(
            display = currentClaimMetadata?.display?.toList(),
            svgId = currentClaimMetadata?.svgId,
        )
        DisclosableDef.Id(attributeMetadata)
    } else {
        // This is a container (object or array)
        val containerAttributeMetadata = AttributeMetadata(
            display = currentClaimMetadata?.display?.toList(),
            svgId = currentClaimMetadata?.svgId,
        )

        // Determine if the children indicate an array or an object
        val isNextLevelArray = directChildrenPaths.any { childPath ->
            childPath.value.last() is ClaimPathElement.AllArrayElements ||
                childPath.value.last() is ClaimPathElement.ArrayElement
        }

        if (isNextLevelArray) {
            // Find the specific path for all array elements (e.g., `degrees[*]`)
            val arrayElementsClaimPath = currentClaimPath.allArrayElements()
            // The `ClaimMetadata` for the array container itself (e.g., `degrees`)
            val arrayContainerClaimMetadata = currentClaimMetadata

            val arrayDefinition = processArrayDefinitionStrict(
                arrayMetadata = containerAttributeMetadata, // Metadata for the array container
                arrayContainerClaimMetadata = arrayContainerClaimMetadata,
                arrayElementsClaimPath = arrayElementsClaimPath,
                existingClaimsByPath = existingClaimsByPath,
                allNodesChildrenMap = allNodesChildrenMap,
                selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
            )
            DisclosableDef.Arr(arrayDefinition)
        } else {
            // Children indicate an object
            val objectDefinition = processObjectDefinitionStrict(
                objMetadata = containerAttributeMetadata,
                childPaths = directChildrenPaths,
                existingClaimsByPath = existingClaimsByPath,
                allNodesChildrenMap = allNodesChildrenMap,
                selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed,
            )
            DisclosableDef.Obj(objectDefinition)
        }
    }

    // Apply the disclosable wrapper based on the current node's SD status
    return if (isCurrentNodeSelectivelyDisclosable) +disclosableDef else !disclosableDef
}
