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

import eu.europa.ec.eudi.sdjwt.dsl.ArrayFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.Folded
import eu.europa.ec.eudi.sdjwt.dsl.ObjectFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.fold
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

private typealias ClaimPaths = Folded<String, Unit, Set<ClaimPath>>
private fun claimPaths(path: List<String?>, result: Set<ClaimPath>): ClaimPaths =
    Folded(path, Unit, result)

/**
 * Gets the set of [ClaimPath] for the attributes described by
 * a [SdJwtObjectDefinition]
 */
fun SdJwtObjectDefinition.claimPaths(): Set<ClaimPath> =
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

private fun processNode(
    parentPath: List<String?>,
    key: String,
    childPaths: Set<ClaimPath>,
): ClaimPaths =
    claimPaths(parentPath, setOf((parentPath + key).toClaimPath()) + childPaths)

private fun processElement(parentPath: List<String?>): ClaimPaths =
    claimPaths(parentPath, setOf(parentPath.toClaimPath()))

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

    override fun ifAlwaysSelectivelyDisclosableId(path: List<String?>, key: String, value: AttributeMetadata) =
        processNode(path, key, emptySet())

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: ClaimPaths,
    ) = processNode(path, key, foldedArray.result)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: ClaimPaths,
    ) = processNode(path, key, foldedObject.result)

    override fun ifNeverSelectivelyDisclosableId(path: List<String?>, key: String, value: AttributeMetadata) =
        processNode(path, key, emptySet())

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: ClaimPaths,
    ) = processNode(path, key, foldedArray.result)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: ClaimPaths,
    ) = processNode(path, key, foldedObject.result)
}

private val ArrayHandlers = object : ArrayFoldHandlers<String, AttributeMetadata, Unit, Set<ClaimPath>> {

    // All array handler overrides call processElement
    override fun ifAlwaysSelectivelyDisclosableId(path: List<String?>, index: Int, value: AttributeMetadata) =
        processElement(path)

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: ClaimPaths,
    ) = processElement(path)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: ClaimPaths,
    ) = processElement(path)

    override fun ifNeverSelectivelyDisclosableId(path: List<String?>, index: Int, value: AttributeMetadata) =
        processElement(path)

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: ClaimPaths,
    ) = processElement(path)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: ClaimPaths,
    ) = processElement(path)
}
