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
package eu.europa.ec.eudi.sdjwt.dsl

/**
 * Enhanced context object for fold operations that includes path tracking and metadata.
 *
 * @param R The result type of the fold operation
 * @param M The type of metadata stored in the context
 * @property path The current path in the structure
 * @property result The result of the fold operation
 * @property metadata Additional metadata for the fold operation
 */
data class Folded<out K, out M, out R>(
    val path: List<K?> = emptyList(),
    val metadata: M,
    val result: R,
)

/**
 * Interface defining path-aware handlers for folding a DisclosableObject.
 * Each function receives the current path and returns an EnhancedFoldContext.
 *
 * @param K The type of keys in the disclosable object
 * @param A The type of values in the disclosable object
 * @param R The result type of the fold operation
 * @param M The type of metadata stored in the context
 */
interface ObjectFoldHandlers<K, in A, M, R> {
    /**
     * Handles a selectively disclosable primitive value
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param id The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifId(
        path: List<K?>,
        key: K,
        id: Disclosable<DisclosableValue.Id<K, A>>,
    ): Folded<K, M, R>

    /**
     * Handles a selectively disclosable array
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedArray The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifArray(
        path: List<K?>,
        key: K,
        array: Disclosable<DisclosableValue.Arr<K, A>>,
        foldedArray: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a selectively disclosable object
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedObject The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifObject(
        path: List<K?>,
        key: K,
        obj: Disclosable<DisclosableValue.Obj<K, A>>,
        foldedObject: Folded<K, M, R>,
    ): Folded<K, M, R>
}

interface SimpleArrayFoldHandlers<K, in A, M, R> {
    /**
     * Handles a selectively disclosable primitive value
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param id The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifId(
        path: List<K?>,
        index: Int,
        id: Disclosable<DisclosableValue.Id<K, A>>,
    ): Folded<K, M, R>

    /**
     * Handles a selectively disclosable array
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedArray The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifArray(
        path: List<K?>,
        index: Int,
        array: Disclosable<DisclosableValue.Arr<K, A>>,
        foldedArray: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a selectively disclosable object
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedObject The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifObject(
        path: List<K?>,
        index: Int,
        obj: Disclosable<DisclosableValue.Obj<K, A>>,
        foldedObject: Folded<K, M, R>,
    ): Folded<K, M, R>
}

/**
 * Enhanced fold function for DisclosableObject that includes path tracking and post-processing.
 *
 * @param objectHandlers Handlers for processing object elements
 * @param arrayHandlers Handlers for processing array elements
 * @param initial Initial context for the fold operation
 * @param combine Function to combine fold results from sibling object properties.
 * @param postProcess Optional function to post-process the fold result
 * @return The result of the fold operation
 */
fun <K, A, R, M> DisclosableObject<K, A>.fold(
    objectHandlers: ObjectFoldHandlers<K, A, M, R>,
    arrayHandlers: SimpleArrayFoldHandlers<K, A, M, R>,
    initial: Folded<K, M, R>,
    combine: (Folded<K, M, R>, Folded<K, M, R>) -> Folded<K, M, R>,
    arrayResultWrapper: (List<R>) -> R,
    arrayMetadataCombiner: (List<M>) -> M,
    postProcess: (Folded<K, M, R>) -> Folded<K, M, R> = { it },
): Folded<K, M, R> {
    val context = Fold(
        objectHandlers,
        arrayHandlers,
        initial,
        combine,
        arrayResultWrapper,
        arrayMetadataCombiner,
    )
    val result = context.foldObject(this to initial.path)
    return postProcess(result)
}

/**
 * Enhanced fold function for DisclosableArray that includes path tracking and post-processing.
 *
 * @param objectHandlers Handlers for processing object elements
 * @param arrayHandlers Handlers for processing array elements
 * @param initial Initial context for the fold operation
 * @param combine Function to combine fold results from sibling object properties.
 * @param arrayResultWrapper Function to convert a list of individual element results into the final result type for an array.
 * @param arrayMetadataCombiner Function to combine metadata from individual array elements into a single metadata for the array.
 * @param postProcess Optional function to post-process the fold result
 * @return The result of the fold operation
 */
fun <K, A, R, M> DisclosableArray<K, A>.fold(
    objectHandlers: ObjectFoldHandlers<K, A, M, R>,
    arrayHandlers: SimpleArrayFoldHandlers<K, A, M, R>,
    initial: Folded<K, M, R>,
    combine: (Folded<K, M, R>, Folded<K, M, R>) -> Folded<K, M, R>,
    arrayResultWrapper: (List<R>) -> R,
    arrayMetadataCombiner: (List<M>) -> M,
    postProcess: (Folded<K, M, R>) -> Folded<K, M, R> = { it },
): Folded<K, M, R> {
    val context = Fold(
        objectHandlers,
        arrayHandlers,
        initial,
        combine,
        arrayResultWrapper,
        arrayMetadataCombiner,
    )
    val result = context.foldArray(this to initial.path)
    return postProcess(result)
}

//
// Implementation
//

/**
 * Private implementation class for the enhanced fold operation.
 * This class handles the recursive traversal of disclosable structures with path tracking.
 *
 * @param K The type of keys in the disclosable object
 * @param A The type of values in the disclosable structure
 * @param R The result type of the fold operation
 * @param M The type of metadata stored in the context
 * @property objectHandlers Handlers for processing object elements
 * @property arrayHandlers Handlers for processing array elements
 * @property initial Initial context for the fold operation
 * @property combine Function to combine fold results
 */
private class Fold<K, A, R, M>(
    private val objectHandlers: ObjectFoldHandlers<K, A, M, R>,
    private val arrayHandlers: SimpleArrayFoldHandlers<K, A, M, R>,
    private val initial: Folded<K, M, R>,
    private val combine: (Folded<K, M, R>, Folded<K, M, R>) -> Folded<K, M, R>,
    private val arrayResultWrapper: (List<R>) -> R,
    private val arrayMetadataCombiner: (List<M>) -> M,
) {
    // DeepRecursiveFunction for folding objects with path tracking
    val foldObject: DeepRecursiveFunction<Pair<DisclosableObject<K, A>, List<K?>>, Folded<K, M, R>> =
        DeepRecursiveFunction { (obj, currentPath) ->
            obj.content.entries.fold(initial) { acc, (key, disclosableElement) ->
                val keyPath = currentPath + key
                val disclosableValue = disclosableElement.value
                val folded = when (disclosableValue) {
                    is DisclosableValue.Id<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val id = disclosableElement as Disclosable<DisclosableValue.Id<K, A>>
                        objectHandlers.ifId(currentPath, key, id)
                    }
                    is DisclosableValue.Arr<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val array = disclosableElement as Disclosable<DisclosableValue.Arr<K, A>>
                        val foldedArray = foldArray.callRecursive(disclosableValue.value to keyPath)
                        objectHandlers.ifArray(currentPath, key, array, foldedArray)
                    }
                    is DisclosableValue.Obj<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val obj = disclosableElement as Disclosable<DisclosableValue.Obj<K, A>>
                        val foldedObj = callRecursive(disclosableValue.value to keyPath)
                        objectHandlers.ifObject(currentPath, key, obj, foldedObj)
                    }
                }
                combine(acc, folded)
            }
        }

    // DeepRecursiveFunction for folding arrays with path tracking
    val foldArray: DeepRecursiveFunction<Pair<DisclosableArray<K, A>, List<K?>>, Folded<K, M, R>> =
        DeepRecursiveFunction { (arr, currentPath) ->
            val arrayContentPathPrefix = currentPath + null

            val elementResults = mutableListOf<R>()
            val elementMetadata = mutableListOf<M>()

            arr.content.forEachIndexed { index, disclosableElement ->
                val disclosableValue = disclosableElement.value
                val folded = when (disclosableValue) {
                    is DisclosableValue.Id<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val id = disclosableElement as Disclosable<DisclosableValue.Id<K, A>>
                        arrayHandlers.ifId(arrayContentPathPrefix, index, id)
                    }
                    is DisclosableValue.Arr<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val array = disclosableElement as Disclosable<DisclosableValue.Arr<K, A>>
                        val foldedArray = callRecursive(disclosableValue.value to (arrayContentPathPrefix + null))
                        arrayHandlers.ifArray(arrayContentPathPrefix, index, array, foldedArray)
                    }
                    is DisclosableValue.Obj<K, A> -> {
                        @Suppress("UNCHECKED_CAST")
                        val obj = disclosableElement as Disclosable<DisclosableValue.Obj<K, A>>
                        val foldedObj = foldObject.callRecursive(disclosableValue.value to arrayContentPathPrefix)
                        arrayHandlers.ifObject(arrayContentPathPrefix, index, obj, foldedObj)
                    }
                }
                elementResults.add(folded.result)
                elementMetadata.add(folded.metadata)
            }

            // Use the provided wrappers to construct the final array result and combine metadata
            val finalArrayResult = arrayResultWrapper(elementResults)
            val finalArrayMetadata = arrayMetadataCombiner(elementMetadata)

            Folded(
                path = arrayContentPathPrefix,
                metadata = finalArrayMetadata,
                result = finalArrayResult,
            )
        }
}
