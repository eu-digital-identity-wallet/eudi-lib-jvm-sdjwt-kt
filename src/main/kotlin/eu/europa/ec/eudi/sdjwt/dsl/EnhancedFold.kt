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
data class EnhancedFoldContext<K, R, M>(
    val path: List<K?> = emptyList(),
    val result: R,
    val metadata: M,
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
interface PathAwareObjectFoldHandlers<K, A, R, M> {
    /**
     * Handles a selectively disclosable primitive value in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableId(path: List<K?>, key: K, value: A): EnhancedFoldContext<K, R, M>

    /**
     * Handles a selectively disclosable array in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedArrayResult The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableArr(
        path: List<K?>,
        key: K,
        foldedArrayResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a selectively disclosable object in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedObjectResult The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableObj(
        path: List<K?>,
        key: K,
        foldedObjectResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable primitive value in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableId(path: List<K?>, key: K, value: A): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable array in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedArrayResult The result of folding the array
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableArr(
        path: List<K?>,
        key: K,
        foldedArrayResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable object in an object.
     *
     * @param path The current path in the structure
     * @param key The key of the current element
     * @param foldedObjectResult The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableObj(
        path: List<K?>,
        key: K,
        foldedObjectResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>
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
interface PathAwareArrayFoldHandlers<K, A, R, M> {
    /**
     * Handles a selectively disclosable primitive value in an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableId(path: List<K?>, index: Int, value: A): EnhancedFoldContext<K, R, M>

    /**
     * Handles a selectively disclosable array within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedArrayResult The result of folding the nested array
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableArr(
        path: List<K?>,
        index: Int,
        foldedArrayResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a selectively disclosable object within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedObjectResult The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifAlwaysSelectivelyDisclosableObj(
        path: List<K?>,
        index: Int,
        foldedObjectResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable primitive value in an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param value The primitive value
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableId(path: List<K?>, index: Int, value: A): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable array within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedArrayResult The result of folding the nested array
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableArr(
        path: List<K?>,
        index: Int,
        foldedArrayResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>

    /**
     * Handles a non-selectively disclosable object within an array.
     *
     * @param path The current path in the structure
     * @param index The index of the current element in the array
     * @param foldedObjectResult The result of folding the object
     * @return The fold context with the result and metadata
     */
    fun ifNeverSelectivelyDisclosableObj(
        path: List<K?>,
        index: Int,
        foldedObjectResult: EnhancedFoldContext<K, R, M>,
    ): EnhancedFoldContext<K, R, M>
}

/**
 * Enhanced fold function for DisclosableObject that includes path tracking and post-processing.
 *
 * @param objectHandlers Handlers for processing object elements
 * @param arrayHandlers Handlers for processing array elements
 * @param initialContext Initial context for the fold operation
 * @param combine Function to combine fold results from sibling object properties.
 * @param arrayResultWrapper Function to convert a list of individual element results into the final result type for an array.
 * @param arrayMetadataCombiner Function to combine metadata from individual array elements into a single metadata for the array.
 * @param postProcess Optional function to post-process the fold result
 * @return The result of the fold operation
 */
fun <K, A, R, M> DisclosableObject<K, A>.foldWithContext(
    objectHandlers: PathAwareObjectFoldHandlers<K, A, R, M>,
    arrayHandlers: PathAwareArrayFoldHandlers<K, A, R, M>,
    initialContext: EnhancedFoldContext<K, R, M>,
    combine: (EnhancedFoldContext<K, R, M>, EnhancedFoldContext<K, R, M>) -> EnhancedFoldContext<K, R, M>,
    arrayResultWrapper: (List<R>) -> R,
    arrayMetadataCombiner: (List<M>) -> M,
    postProcess: (EnhancedFoldContext<K, R, M>) -> EnhancedFoldContext<K, R, M> = { it },
): EnhancedFoldContext<K, R, M> {
    val context = EnhancedFoldContextImpl(
        objectHandlers,
        arrayHandlers,
        initialContext,
        combine,
        arrayResultWrapper,
        arrayMetadataCombiner,
    )
    val result = context.foldObject(this to initialContext.path)
    return postProcess(result)
}

/**
 * Enhanced fold function for DisclosableArray that includes path tracking and post-processing.
 *
 * @param objectHandlers Handlers for processing object elements
 * @param arrayHandlers Handlers for processing array elements
 * @param initialContext Initial context for the fold operation
 * @param combine Function to combine fold results from sibling object properties.
 * @param arrayResultWrapper Function to convert a list of individual element results into the final result type for an array.
 * @param arrayMetadataCombiner Function to combine metadata from individual array elements into a single metadata for the array.
 * @param postProcess Optional function to post-process the fold result
 * @return The result of the fold operation
 */
fun <K, A, R, M> DisclosableArray<K, A>.foldWithContext(
    objectHandlers: PathAwareObjectFoldHandlers<K, A, R, M>,
    arrayHandlers: PathAwareArrayFoldHandlers<K, A, R, M>,
    initialContext: EnhancedFoldContext<K, R, M>,
    combine: (EnhancedFoldContext<K, R, M>, EnhancedFoldContext<K, R, M>) -> EnhancedFoldContext<K, R, M>,
    arrayResultWrapper: (List<R>) -> R,
    arrayMetadataCombiner: (List<M>) -> M,
    postProcess: (EnhancedFoldContext<K, R, M>) -> EnhancedFoldContext<K, R, M> = { it },
): EnhancedFoldContext<K, R, M> {
    val context = EnhancedFoldContextImpl(
        objectHandlers,
        arrayHandlers,
        initialContext,
        combine,
        arrayResultWrapper,
        arrayMetadataCombiner,
    )
    val result = context.foldArray(this to initialContext.path)
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
 * @property initialContext Initial context for the fold operation
 * @property combine Function to combine fold results
 */
private class EnhancedFoldContextImpl<K, A, R, M>(
    private val objectHandlers: PathAwareObjectFoldHandlers<K, A, R, M>,
    private val arrayHandlers: PathAwareArrayFoldHandlers<K, A, R, M>,
    private val initialContext: EnhancedFoldContext<K, R, M>,
    private val combine: (EnhancedFoldContext<K, R, M>, EnhancedFoldContext<K, R, M>) -> EnhancedFoldContext<K, R, M>,
    private val arrayResultWrapper: (List<R>) -> R,
    private val arrayMetadataCombiner: (List<M>) -> M,
) {
    // DeepRecursiveFunction for folding objects with path tracking
    val foldObject: DeepRecursiveFunction<Pair<DisclosableObject<K, A>, List<K?>>, EnhancedFoldContext<K, R, M>> =
        DeepRecursiveFunction { (obj, currentPath) ->
            obj.content.entries.fold(initialContext) { acc, (key, disclosableElement) ->
                val keyPath = currentPath + key
                val elementResult = when (disclosableElement) {
                    is Disclosable.AlwaysSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                objectHandlers.ifAlwaysSelectivelyDisclosableId(currentPath, key, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedArrayResult = foldArray.callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifAlwaysSelectivelyDisclosableArr(currentPath, key, foldedArrayResult)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedObjectResult = callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifAlwaysSelectivelyDisclosableObj(currentPath, key, foldedObjectResult)
                            }
                        }
                    }
                    is Disclosable.NeverSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id ->
                                objectHandlers.ifNeverSelectivelyDisclosableId(currentPath, key, disclosableValue.value)
                            is DisclosableValue.Arr -> {
                                val foldedArrayResult = foldArray.callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifNeverSelectivelyDisclosableArr(currentPath, key, foldedArrayResult)
                            }
                            is DisclosableValue.Obj -> {
                                val foldedObjectResult = callRecursive(disclosableValue.value to keyPath)
                                objectHandlers.ifNeverSelectivelyDisclosableObj(currentPath, key, foldedObjectResult)
                            }
                        }
                    }
                }
                combine(acc, elementResult)
            }
        }

    // DeepRecursiveFunction for folding arrays with path tracking
    val foldArray: DeepRecursiveFunction<Pair<DisclosableArray<K, A>, List<K?>>, EnhancedFoldContext<K, R, M>> =
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

            EnhancedFoldContext(
                path = arrayContentPathPrefix,
                result = finalArrayResult,
                metadata = finalArrayMetadata,
            )
        }
}
