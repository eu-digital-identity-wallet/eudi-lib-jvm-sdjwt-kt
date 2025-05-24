package eu.europa.ec.eudi.sdjwt.dsl.meta

import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.not
import eu.europa.ec.eudi.sdjwt.dsl.unaryPlus
import eu.europa.ec.eudi.sdjwt.vc.*


private fun ClaimMetadata.toDisclosableElementMetadata(
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>, // Pass this for recursive calls
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableElementMetadata {
    val (nestedDisclosableValue, isSelectivelyDisclosable) = buildNestedDisclosableValue(
        currentClaimPath = this.path, // The claim path this metadata belongs to
        allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed
    )
    return if (isSelectivelyDisclosable) +nestedDisclosableValue else !nestedDisclosableValue
}



private fun parseObj(
    objMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableObjectMetadata {
    ClaimPath.ensureObjectAttributes(childClaimsMetadatas.map { it.path }) // Check invariant

    val contentMap = mutableMapOf<String, DisclosableElementMetadata>() // Use the alias here

    childClaimsMetadatas.forEach { childClaimMetadata ->
        val claimName = childClaimMetadata.path.head().fold(
            ifAllArrayElements = { throw IllegalStateException("Should not happen after invariant check") },
            ifArrayElement = { throw IllegalStateException("Should not happen after invariant check") },
            ifClaim = { it }
        )

        // Recursively build the DisclosableElementMetadata for each child
        val disclosableElement = childClaimMetadata.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed
        )
        contentMap[claimName] = disclosableElement
    }

    return DisclosableObjectMetadata(contentMap, objMetadata)
}

private fun parseArr(
    arrayMetadata: AttributeMetadata,
    childClaimsMetadatas: List<ClaimMetadata>,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableArrayMetadata {
    ClaimPath.ensureArrayElements(childClaimsMetadatas.map { it.path }) // Check invariant

    val contentList = mutableListOf<DisclosableElementMetadata>() // Use the alias here

    // Group children by their head element type to handle both specific indices and wildcards
    val distinctArrayChildHeads = childClaimsMetadatas
        .map { it.path.head() }
        .distinct()
        .sortedBy { (it as? ClaimPathElement.ArrayElement)?.index ?: Int.MAX_VALUE } // Order by index for predictability

    distinctArrayChildHeads.forEach { arrayElementHead ->
        // Find the ClaimMetadata corresponding to this specific array element head.
        // There should be only one ClaimMetadata for a given distinct array element head at this level.
        val elementClaimMetadata = childClaimsMetadatas.first { it.path.head() == arrayElementHead }

        // Recursively build the DisclosableElementMetadata for each child element
        val disclosableElement = elementClaimMetadata.toDisclosableElementMetadata(
            allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed
        )
        contentList.add(disclosableElement)
    }

    return DisclosableArrayMetadata(contentList, arrayMetadata)
}

private fun buildNestedDisclosableValue(
    currentClaimPath: ClaimPath,
    allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>>,
    selectivelyDiscloseWhenAllowed: Boolean,
): Pair<DisclosableValue<String, AttributeMetadata>, Boolean> {

    // Retrieve the ClaimMetadata for the current node being processed.
    val currentClaimMetadata = allClaimsGroupedByParentPath[currentClaimPath.parent()]
        ?.firstOrNull { it.path == currentClaimPath }
        ?: throw IllegalStateException("ClaimMetadata not found for current path: ${currentClaimPath}. All intermediate paths must have a corresponding ClaimMetadata entry.")

    // Determine if the current node is selectively disclosable.
    val isCurrentNodeSelectivelyDisclosable = when(currentClaimMetadata.selectivelyDisclosableOrDefault) {
        ClaimSelectivelyDisclosable.Always -> true
        ClaimSelectivelyDisclosable.Never -> false
        ClaimSelectivelyDisclosable.Allowed -> selectivelyDiscloseWhenAllowed
    }

    // Find all immediate children claims of the current path.
    val childrenClaimMetadatas = allClaimsGroupedByParentPath[currentClaimPath] ?: emptyList()

    if (childrenClaimMetadatas.isEmpty()) {
        // If no children, this is a leaf node (ID).
        val attributeMetadata = AttributeMetadata(
            display = currentClaimMetadata.display?.toList(),
            svgId = currentClaimMetadata.svgId
        )
        return DisclosableValue.Id<String, AttributeMetadata>(attributeMetadata) to isCurrentNodeSelectivelyDisclosable
    }

    // Determine if the children indicate an array or object.
    val isNextLevelArray = childrenClaimMetadatas.any { childCm ->
        val head = childCm.path.head()
        head is ClaimPathElement.AllArrayElements || head is ClaimPathElement.ArrayElement
    }

    // The AttributeMetadata for the container itself.
    val containerAttributeMetadata = AttributeMetadata(
        display = currentClaimMetadata.display?.toList(),
        svgId = currentClaimMetadata.svgId
    )

    val disclosableValue: DisclosableValue<String, AttributeMetadata> = if (isNextLevelArray) {
        // Call parseArr to get DisclosableArrayMetadata (which implements DisclosableArray)
        val constructedArray = parseArr(
            arrayMetadata = containerAttributeMetadata,
            childClaimsMetadatas = childrenClaimMetadatas,
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed
        )
        // Wrap the DisclosableArrayMetadata in DisclosableValue.Arr
        DisclosableValue.Arr(constructedArray)
    } else {
        // Call parseObj to get DisclosableObjectMetadata (which implements DisclosableObject)
        val constructedObject = parseObj(
            objMetadata = containerAttributeMetadata,
            childClaimsMetadatas = childrenClaimMetadatas,
            allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
            selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed
        )
        // Wrap the DisclosableObjectMetadata in DisclosableValue.Obj
        DisclosableValue.Obj(constructedObject)
    }

    // Return the constructed DisclosableValue (which could be Id, Obj, or Arr) and its disclosure status.
    return disclosableValue to isCurrentNodeSelectivelyDisclosable
}

fun ResolvedTypeMetadata.toDisclosableMetadataStructure(
    selectivelyDiscloseWhenAllowed: Boolean = true,
): DisclosableObjectMetadata {

    // Define a dummy root path name using the VCT value, which is guaranteed non-blank.
    val rootDefiningPathName = this.vct.value
    val rootDefiningPath = ClaimPath.claim(rootDefiningPathName)

    // Group all claims by their parent path for efficient lookup during recursion.
    // Claims whose parent is null (top-level claims) will be grouped under `null` key.
    val allClaimsGroupedByParentPath: Map<ClaimPath?, List<ClaimMetadata>> =
        claims.groupBy { it.path.parent() }

    // Derive AttributeMetadata for the root object itself.
    val rootAttributeMetadata = AttributeMetadata(
        display = this.display.firstOrNull()?.let { listOf(ClaimDisplay(it.lang, it.name, it.description)) }
    )

    // Get the top-level claims (those with no parent path)
    val topLevelClaims = allClaimsGroupedByParentPath[null] ?: emptyList()

    // Ensure top-level claims are object attributes
    ClaimPath.ensureObjectAttributes(topLevelClaims.map { it.path }) // CORRECTED: pass List<ClaimPath>

    // Now delegate to parseObj for the root object
    return parseObj(
        objMetadata = rootAttributeMetadata,
        childClaimsMetadatas = topLevelClaims,
        allClaimsGroupedByParentPath = allClaimsGroupedByParentPath,
        selectivelyDiscloseWhenAllowed = selectivelyDiscloseWhenAllowed
    )
}