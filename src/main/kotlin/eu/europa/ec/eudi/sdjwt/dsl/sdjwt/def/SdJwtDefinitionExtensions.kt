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

import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

private typealias ClaimPaths = Folded<String, Unit, Set<ClaimPath>>

private fun claimPaths(path: List<String?>, result: Set<ClaimPath>): ClaimPaths =
    Folded(path, Unit, result)

/**
 * Gets the set of [ClaimPath] for the attributes described by
 * a [SdJwtObjectDefinition] or [SdJwtDefinition]
 */
fun DisclosableObject<String, AttributeMetadata>.claimPaths(): Set<ClaimPath> =
    fold(
        objectHandlers = ObjectHandlers,
        arrayHandlers = ArrayHandlers,
        initial = Folded(
            path = emptyList(),
            metadata = Unit,
            result = emptySet(),
        ),
        combine = { acc, current -> Folded(acc.path, Unit, acc.result + current.result) },
        arrayResultWrapper = { elementResults -> elementResults.flatten().toSet() },
        arrayMetadataCombiner = { },
    ).result

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

private val ObjectHandlers = object : ObjectFoldHandlers<String, AttributeMetadata, Unit, Set<ClaimPath>> {

    private fun processNode(
        parentPath: List<String?>,
        key: String,
        childPaths: Set<ClaimPath>,
    ): ClaimPaths =
        claimPaths(parentPath, setOf((parentPath + key).toClaimPath()) + childPaths)

    override fun ifId(
        path: List<String?>,
        key: String,
        id: Disclosable<DisclosableValue.Id<String, AttributeMetadata>>,
    ): Folded<String, Unit, Set<ClaimPath>> {
        return processNode(path, key, emptySet())
    }

    override fun ifArray(
        path: List<String?>,
        key: String,
        array: Disclosable<DisclosableValue.Arr<String, AttributeMetadata>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Folded<String, Unit, Set<ClaimPath>> {
        return processNode(path, key, foldedArray.result)
    }

    override fun ifObject(
        path: List<String?>,
        key: String,
        obj: Disclosable<DisclosableValue.Obj<String, AttributeMetadata>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Folded<String, Unit, Set<ClaimPath>> {
        return processNode(path, key, foldedObject.result)
    }
}

private val ArrayHandlers = object : ArrayFoldHandlers<String, AttributeMetadata, Unit, Set<ClaimPath>> {

    private fun processElement(path: List<String?>): ClaimPaths =
        claimPaths(path, setOf(path.toClaimPath()))

    override fun ifId(
        path: List<String?>,
        index: Int,
        id: Disclosable<DisclosableValue.Id<String, AttributeMetadata>>,
    ): Folded<String, Unit, Set<ClaimPath>> = processElement(path)

    override fun ifArray(
        path: List<String?>,
        index: Int,
        array: Disclosable<DisclosableValue.Arr<String, AttributeMetadata>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Folded<String, Unit, Set<ClaimPath>> = processElement(path)

    override fun ifObject(
        path: List<String?>,
        index: Int,
        obj: Disclosable<DisclosableValue.Obj<String, AttributeMetadata>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Folded<String, Unit, Set<ClaimPath>> = processElement(path)
}
