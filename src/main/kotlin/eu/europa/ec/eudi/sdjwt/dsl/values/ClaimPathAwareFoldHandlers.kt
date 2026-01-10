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
package eu.europa.ec.eudi.sdjwt.dsl.values

import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement

/**
 * An abstract base class for handlers used in a folding operation over a [DisclosableObject]
 * that need to generate [ClaimPath]s for each attribute.
 *
 * This class ensures that the [ClaimPath] passed to the abstract `ifId`, `ifArray`, and `ifObject`
 * methods **always terminates with a named attribute element** (e.g., `parent.attributeName`).
 * It will not generate paths ending in array indices (`[0]`) or wildcards (`[*]` or `null`).
 *
 * Subclasses should implement the abstract methods to define how each type of disclosable element
 * (ID, array, object) contributes to the fold result, given its corresponding [ClaimPath].
 */
abstract class ClaimPathAwareObjectFoldHandlers<A, M, R> : ObjectFoldHandlers<String, A, M, R> {

    /**
     * Provides the empty context for folding an object.
     *
     * @param obj The object being folded
     * @param path The current path in the structure
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun empty(path: ClaimPath?, obj: DisclosableObject<String, A>): Pair<M, R>

    final override fun empty(obj: DisclosableObject<String, A>, path: List<String?>): Folded<String, M, R> {
        val (m, r) = empty(path.toClaimPath(), obj)
        return Folded(path, m, r)
    }

    /**
     * Constructs a [ClaimPath] by appending a new named attribute element to the given parent path.
     *
     * @param path The list of string segments representing the parent path (can include `null` for wildcards).
     * @param key The name of the new attribute to append.
     * @return A [ClaimPath] representing the full path to the new attribute.
     */
    protected fun attributeClaimPath(
        path: List<String?>,
        key: String,
    ): ClaimPath = path.toClaimPath()
        ?.let { it + ClaimPathElement.Claim(key) }
        ?: ClaimPath.claim(key)

    /**
     * Handles a disclosable ID (primitive) value at the given [path].
     *
     * @param path The [ClaimPath] to the ID element. This path will end with a named attribute element.
     * @param id The disclosable ID element.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<M, R>

    /**
     * Handles a disclosable array at the given [path].
     *
     * @param path The [ClaimPath] to the array element. This path will end with a named attribute element.
     * @param array The disclosable array element.
     * @param foldedArray The result of folding the contents of the array.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Pair<M, R>

    /**
     * Handles a disclosable object at the given [path].
     *
     * @param path The [ClaimPath] to the object element. This path will end with a named attribute element.
     * @param obj The disclosable object element.
     * @param foldedObject The result of folding the contents of the object.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
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

/**
 * An abstract base class for handlers used in a folding operation over a [DisclosableArray]
 * that need to generate [ClaimPath]s for each element within the array.
 *
 * This class ensures that the [ClaimPath] passed to the abstract `ifId`, `ifArray`, and `ifObject`
 * methods **always terminates with a specific array index element** (e.g., `parentArray.[0]`).
 * It will not generate paths ending in named attributes or wildcards (`[*]` or `null`).
 *
 * Subclasses should implement the abstract methods to define how each type of disclosable element
 * (ID, array, object) within the array contributes to the fold result, given its corresponding [ClaimPath].
 */
abstract class ClaimPathAwareArrayFoldHandlers<A, M, R> : ArrayFoldHandlers<String, A, M, R> {

    /**
     * Provides the empty context for folding an array.
     *
     * @param arr The array being folded
     * @param path The current path in the structure
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun empty(path: ClaimPath?, arr: DisclosableArray<String, A>): Pair<M, R>

    final override fun empty(arr: DisclosableArray<String, A>, path: List<String?>): Folded<String, M, R> {
        val (m, r) = empty(path.toClaimPath(), arr)
        return Folded(path, m, r)
    }

    /**
     * Wraps a list of individual element results into the final result type for an array.
     *
     * @param elements The results of folding individual elements
     * @return The combined result for the array
     */
    abstract override fun wrapResult(elements: List<R>): R

    /**
     * Combines metadata from individual array elements into a single metadata for the array.
     *
     * @param metadata The metadata gathered from folding individual elements
     * @return The combined metadata for the array
     */
    abstract override fun combineMetadata(metadata: List<M>): M

    /**
     * Constructs a [ClaimPath] by appending a new array index element to the given parent path.
     *
     * @param path The list of string segments representing the parent path (can include `null` for wildcards).
     * @param index The integer index of the new array element to append.
     * @return A [ClaimPath] representing the full path to the new array element.
     */
    private fun elementClaimPath(path: List<String?>, index: Int): ClaimPath {
        val indexElement = ClaimPathElement.ArrayElement(index)
        return path.toClaimPath()
            ?.let { it + indexElement }
            ?: ClaimPath(indexElement)
    }

    /**
     * Handles a disclosable ID (primitive) value at the given [path].
     *
     * @param path The [ClaimPath] to the ID element. This path will end with an array index element.
     * @param id The disclosable ID element.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<M, R>

    /**
     * Handles a disclosable array at the given [path].
     *
     * @param path The [ClaimPath] to the array element. This path will end with an array index element.
     * @param array The disclosable array element.
     * @param foldedArray The result of folding the contents of the array.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
    abstract fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, M, R>,
    ): Pair<M, R>

    /**
     * Handles a disclosable object at the given [path].
     *
     * @param path The [ClaimPath] to the object element. This path will end with an array index element.
     * @param obj The disclosable object element.
     * @param foldedObject The result of folding the contents of the object.
     * @return A [Pair] containing the accumulated metadata and the result for this element.
     */
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
