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
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableId(path: List<K?>, key: K, value: A): Folded<K, M, R>

    /**
     * Handles a selectively disclosable array
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedArray The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableArr(
        path: List<K?>,
        key: K,
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
    fun ifAlwaysSelectivelyDisclosableObj(
        path: List<K?>,
        key: K,
        foldedObject: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable primitive value
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableId(path: List<K?>, key: K, value: A): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable array
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedArray The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableArr(
        path: List<K?>,
        key: K,
        foldedArray: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable object in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedObject The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableObj(
        path: List<K?>,
        key: K,
        foldedObject: Folded<K, M, R>,
    ): Folded<K, M, R>
}

/**
 * Interface defining path-aware handlers for folding a DisclosableArray.
 * Each function receives the current path and returns an EnhancedFoldContext.
 *
 * @param K The type of keys in the disclosable object (used for path tracking)
 * @param A The type of values in the disclosable array
 * @param R The result type of the fold operation
 * @param M The type of metadata stored in the context
 */
interface ArrayFoldHandlers<K, in A, M, R> {
    /**
     * Handles a selectively disclosable primitive value in an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableId(path: List<K?>, index: Int, value: A): Folded<K, M, R>

    /**
     * Handles a selectively disclosable array within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedArray The result of folding the nested array
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableArr(
        path: List<K?>,
        index: Int,
        foldedArray: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a selectively disclosable object within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedObject The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableObj(
        path: List<K?>,
        index: Int,
        foldedObject: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable primitive value in an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableId(path: List<K?>, index: Int, value: A): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable array within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedArray The result of folding the nested array
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableArr(
        path: List<K?>,
        index: Int,
        foldedArray: Folded<K, M, R>,
    ): Folded<K, M, R>

    /**
     * Handles a non-selectively disclosable object within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedObject The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableObj(
        path: List<K?>,
        index: Int,
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
    arrayHandlers: ArrayFoldHandlers<K, A, M, R>,
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
    arrayHandlers: ArrayFoldHandlers<K, A, M, R>,
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
    private val arrayHandlers: ArrayFoldHandlers<K, A, M, R>,
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
                val folded = when (disclosableElement) {
                    is Disclosable.AlwaysSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                objectHandlers.ifAlwaysSelectivelyDisclosableId(currentPath, key, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedArray = foldArray.callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifAlwaysSelectivelyDisclosableArr(currentPath, key, foldedArray)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedObj = callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifAlwaysSelectivelyDisclosableObj(currentPath, key, foldedObj)
                            }
                        }
                    }
                    is Disclosable.NeverSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                objectHandlers.ifNeverSelectivelyDisclosableId(currentPath, key, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedArray = foldArray.callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifNeverSelectivelyDisclosableArr(currentPath, key, foldedArray)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedObj = callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifNeverSelectivelyDisclosableObj(currentPath, key, foldedObj)
                            }
                        }
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
                val elementContext = when (disclosableElement) {
                    is Disclosable.AlwaysSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                arrayHandlers.ifAlwaysSelectivelyDisclosableId(arrayContentPathPrefix, index, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedInnerArrayResult = callRecursive(disclosableValue.value to (arrayContentPathPrefix + null))
                                arrayHandlers.ifAlwaysSelectivelyDisclosableArr(arrayContentPathPrefix, index, foldedInnerArrayResult)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedInnerObjectResult = foldObject.callRecursive(disclosableValue.value to arrayContentPathPrefix)
                                arrayHandlers.ifAlwaysSelectivelyDisclosableObj(arrayContentPathPrefix, index, foldedInnerObjectResult)
                            }
                        }
                    }
                    is Disclosable.NeverSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                arrayHandlers.ifNeverSelectivelyDisclosableId(arrayContentPathPrefix, index, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedInnerArrayResult = callRecursive(disclosableValue.value to (arrayContentPathPrefix + null))
                                arrayHandlers.ifNeverSelectivelyDisclosableArr(arrayContentPathPrefix, index, foldedInnerArrayResult)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedInnerObjectResult = foldObject.callRecursive(disclosableValue.value to arrayContentPathPrefix)
                                arrayHandlers.ifNeverSelectivelyDisclosableObj(arrayContentPathPrefix, index, foldedInnerObjectResult)
                            }
                        }
                    }
                }
                elementResults.add(elementContext.result)
                elementMetadata.add(elementContext.metadata)
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
