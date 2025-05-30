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
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement

/**
 * Gets the set of [ClaimPath] for the attributes described by
 * a [SdJwtObjectDefinition] or [SdJwtDefinition]
 */
fun DisclosableObject<String, *>.claimPaths(): Set<ClaimPath> =
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

abstract class ClaimPathAwareObjectFoldHandlers<A, M, R> : ObjectFoldHandlers<String, A, M, R> {

    protected val ClaimPath.attributeClaim: String
        get() {
            val lastClaim = value.last()
            check(lastClaim is ClaimPathElement.Claim) {
                "Not an attribute claim path: $this"
            }
            return lastClaim.name
        }

    protected fun attributeClaimPath(
        path: List<String?>,
        key: String,
    ): ClaimPath = path.toClaimPath()
        ?.let { it + ClaimPathElement.Claim(key) }
        ?: ClaimPath.claim(key)

    abstract fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<M, R>

    abstract fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Pair<M, R>

    abstract fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, M, R>,
    ): Pair<M, R>

    final override fun ifId(
        path: List<String?>,
        key: String,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Folded<String, M, R> {
        val (m, r) = ifId(attributeClaimPath(path, key), id)
        return Folded(path, m, r)
    }

    final override fun ifArray(
        path: List<String?>,
        key: String,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Folded<String, M, R> {
        val (m, r) = ifArray(attributeClaimPath(path, key), array, foldedArray)
        return Folded(path, m, r)
    }

    final override fun ifObject(
        path: List<String?>,
        key: String,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, M, R>,
    ): Folded<String, M, R> {
        val (m, r) = ifObject(attributeClaimPath(path, key), obj, foldedObject)
        return Folded(path, m, r)
    }
}
abstract class ClaimPathAwareArrayFoldHandlers<A, M, R> : ArrayFoldHandlers<String, A, M, R> {

    private fun elementClaimPath(path: List<String?>, index: Int): ClaimPath {
        val indexElement = ClaimPathElement.ArrayElement(index)
        return path.toClaimPath()
            ?.let { it -> it + indexElement }
            ?: ClaimPath(indexElement)
    }

    abstract fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<M, R>

    abstract fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Pair<M, R>

    abstract fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, M, R>,
    ): Pair<M, R>

    final override fun ifId(
        path: List<String?>,
        index: Int,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Folded<String, M, R> {
        val (m, r) = ifId(elementClaimPath(path, index), id)
        return Folded(path, m, r)
    }

    final override fun ifArray(
        path: List<String?>,
        index: Int,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Folded<String, M, R> {
        val (m, r) = ifArray(elementClaimPath(path, index), array, foldedArray)
        return Folded(path, m, r)
    }

    final override fun ifObject(
        path: List<String?>,
        index: Int,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, M, R>,
    ): Folded<String, M, R> {
        val (m, r) = ifObject(elementClaimPath(path, index), obj, foldedObject)
        return Folded(path, m, r)
    }
}

private object ObjectHandlers : ClaimPathAwareObjectFoldHandlers<Any?, Unit, Set<ClaimPath>>() {
    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, Any?>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(path)

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, Any?>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = foldedArray.metadata to setOf(path) + foldedArray.result

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, Any?>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = foldedObject.metadata to setOf(path) + foldedObject.result
}

private object ArrayHandlers : ClaimPathAwareArrayFoldHandlers<Any?, Unit, Set<ClaimPath>>() {

    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, Any?>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent()))

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, Any?>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent()))

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, Any?>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent())) + foldedObject.result
}

private fun List<String?>.toClaimPath(): ClaimPath? {
    if (isEmpty()) return null
    val head = requireNotNull(first()) { "First path segment must be an object key" }
    return drop(1).fold(ClaimPath.claim(head)) { path, claim ->
        when (claim) {
            null -> path.allArrayElements()
            else -> path.claim(claim)
        }
    }
}
