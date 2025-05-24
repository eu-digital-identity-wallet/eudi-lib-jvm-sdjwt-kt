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

import eu.europa.ec.eudi.sdjwt.dsl.ArrayFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.Folded
import eu.europa.ec.eudi.sdjwt.dsl.ObjectFoldHandlers
import eu.europa.ec.eudi.sdjwt.dsl.fold
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

fun DisclosableObjectMetadata.claimPaths(): Set<ClaimPath> =
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
    childResult: Set<ClaimPath>,
): Folded<String, Set<ClaimPath>, Unit> =
    Folded(
        parentPath,
        Unit,
        result = setOf((parentPath + key).toClaimPath()) + childResult,
    )

private fun processElement(
    parentPath: List<String?>,
    childResult: Set<ClaimPath>,
): Folded<String, Set<ClaimPath>, Unit> {
    return Folded(
        parentPath,
        Unit,
        result = if (childResult.isEmpty()) setOf(parentPath.toClaimPath()) else setOf(parentPath.toClaimPath()) + childResult,
    )
}

private fun List<String?>.toClaimPath(): ClaimPath {
    require(isNotEmpty()) { "Path segments cannot be empty" }
    require(first() != null) { "First path segment must be an object key" }

    return drop(1).fold(ClaimPath.claim(first()!!)) { acc, segment ->
        when (segment) {
            null -> acc.allArrayElements()
            else -> acc.claim(segment)
        }
    }
}

private val ObjectHandlers = object : ObjectFoldHandlers<String, AttributeMetadata, Set<ClaimPath>, Unit> {

    override fun ifAlwaysSelectivelyDisclosableId(path: List<String?>, key: String, value: AttributeMetadata) =
        processNode(path, key, emptySet())

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Folded<String, Set<ClaimPath>, Unit>,
    ) = processNode(path, key, foldedArray.result)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Folded<String, Set<ClaimPath>, Unit>,
    ) = processNode(path, key, foldedObject.result)

    override fun ifNeverSelectivelyDisclosableId(path: List<String?>, key: String, value: AttributeMetadata) =
        processNode(path, key, emptySet())

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        key: String,
        foldedArray: Folded<String, Set<ClaimPath>, Unit>,
    ) = processNode(path, key, foldedArray.result)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        key: String,
        foldedObject: Folded<String, Set<ClaimPath>, Unit>,
    ) = processNode(path, key, foldedObject.result)
}

private val ArrayHandlers = object : ArrayFoldHandlers<String, AttributeMetadata, Set<ClaimPath>, Unit> {

    // All array handler overrides call processElement
    override fun ifAlwaysSelectivelyDisclosableId(path: List<String?>, index: Int, value: AttributeMetadata) =
        processElement(path, emptySet())

    override fun ifAlwaysSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Folded<String, Set<ClaimPath>, Unit>,
    ) = processElement(path, foldedArray.result)

    override fun ifAlwaysSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Folded<String, Set<ClaimPath>, Unit>,
    ) = processElement(path, foldedObject.result)

    override fun ifNeverSelectivelyDisclosableId(path: List<String?>, index: Int, value: AttributeMetadata) =
        processElement(path, emptySet())

    override fun ifNeverSelectivelyDisclosableArr(
        path: List<String?>,
        index: Int,
        foldedArray: Folded<String, Set<ClaimPath>, Unit>,
    ) = processElement(path, foldedArray.result)

    override fun ifNeverSelectivelyDisclosableObj(
        path: List<String?>,
        index: Int,
        foldedObject: Folded<String, Set<ClaimPath>, Unit>,
    ) = processElement(path, foldedObject.result)
}
