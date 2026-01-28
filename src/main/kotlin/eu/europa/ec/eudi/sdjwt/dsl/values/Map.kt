/*
 * Copyright (c) 2023-2026 European Commission
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

/**
 * Transforms the keys and values of the current `DisclosableObject` using the provided factory and mapping functions.
 *
 * @param factory The factory used to create the transformed `DisclosableObject` and `DisclosableArray` instances.
 * @param fK A function that maps the keys of the current `DisclosableObject` to keys of a new type.
 * @param fA A function that maps the values of the current `DisclosableObject` to values of a new type.
 * @return A transformed `DisclosableObject` of type `DObj1` created using the provided factory and mapping functions.
 */
fun <K, K1, A, A1, DObj1, DArr1> DisclosableObject<K, A>.map(
    factory: DisclosableContainerFactory<K1, A1, DObj1, DArr1>,
    fK: (K) -> K1,
    fA: (A) -> A1,
): DObj1
    where
          DObj1 : DisclosableObject<K1, A1>,
          DArr1 : DisclosableArray<K1, A1> {
    val context = MapFoldContext(factory, fK, fA)
    return context.mapObject(this)
}

/**
 * Transforms the keys and values of the current `DisclosableObject` using the provided mapping functions.
 *
 * @param fK A function that maps the keys of the current `DisclosableObject` to keys of a new type.
 * @param fA A function that maps the values of the current `DisclosableObject` to values of a new type.
 * @return A new `DisclosableObject` with transformed keys and values.
 */
fun <K, K1, A, A1> DisclosableObject<K, A>.map(
    fK: (K) -> K1,
    fA: (A) -> A1,
): DisclosableObject<K1, A1> =
    map(DisclosableContainerFactory.default(), fK, fA)

/**
 * Transforms the current `DisclosableArray` into a new `DisclosableArray` using the given factory,
 * key transformation function, and value transformation function. This operation applies the transformations
 * recursively to both objects and arrays within the current structure.
 *
 * @param factory A factory to create new instances of `DisclosableObject` and `DisclosableArray` while mapping.
 * @param fK A function to transform the keys of `DisclosableObject` elements.
 * @param fA A function to transform the values of `DisclosableArray` or `DisclosableObject` elements.
 * @return A new `DisclosableArray` created as a result of applying the provided transformations.
 */
fun <K, K1, A, A1, DObj1, DArr1> DisclosableArray<K, A>.map(
    factory: DisclosableContainerFactory<K1, A1, DObj1, DArr1>,
    fK: (K) -> K1,
    fA: (A) -> A1,
): DArr1
    where
          DObj1 : DisclosableObject<K1, A1>,
          DArr1 : DisclosableArray<K1, A1> {
    val context = MapFoldContext(factory, fK, fA)
    return context.mapArray(this)
}

/**
 * Transforms the current [DisclosableArray] into a new [DisclosableArray] by applying the provided
 * key transformation function and value transformation function. The default `DisclosableContainerFactory`
 * is used to create the transformed container.
 *
 * @param fK A function to transform the keys of [DisclosableObject] elements.
 * @param fA A function to transform the values of [DisclosableArray] or [DisclosableObject] elements.
 * @return A new [DisclosableArray] resulting from the application of the provided transformations.
 */
fun <K, K1, A, A1> DisclosableArray<K, A>.map(
    fK: (K) -> K1,
    fA: (A) -> A1,
): DisclosableArray<K1, A1> = map(DisclosableContainerFactory.default(), fK, fA)

//
// Implementation
//

private class MapFoldContext<K, K1, A, A1, DObj1 : DisclosableObject<K1, A1>, DArr1 : DisclosableArray<K1, A1>>(
    private val factory: DisclosableContainerFactory<K1, A1, DObj1, DArr1>,
    private val fK: (K) -> K1,
    private val fA: (A) -> A1,
) {

    val mapObject: DeepRecursiveFunction<DisclosableObject<K, A>, DObj1> =
        DeepRecursiveFunction { obj ->
            val mappedEntries = obj.content.entries.associate { (key, disclosableElement) ->
                val newKey = fK(key)
                val newDisclosableValue = when (disclosableElement) {
                    is Disclosable.AlwaysSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id -> {
                                val newValue = fA(disclosableValue.value)
                                Disclosable.AlwaysSelectively(DisclosableValue.Id(newValue))
                            }

                            is DisclosableValue.Arr -> {
                                val newArray = mapArray.callRecursive(disclosableValue.value)
                                Disclosable.AlwaysSelectively(DisclosableValue.Arr(newArray))
                            }

                            is DisclosableValue.Obj -> {
                                val newObject = callRecursive(disclosableValue.value) // Call itself (mapObject)
                                Disclosable.AlwaysSelectively(DisclosableValue.Obj(newObject))
                            }
                        }
                    }

                    is Disclosable.NeverSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id -> {
                                val newValue = fA(disclosableValue.value)
                                Disclosable.NeverSelectively(DisclosableValue.Id(newValue))
                            }

                            is DisclosableValue.Arr -> {
                                val newArray = mapArray.callRecursive(disclosableValue.value)
                                Disclosable.NeverSelectively(DisclosableValue.Arr(newArray))
                            }

                            is DisclosableValue.Obj -> {
                                val newObject = callRecursive(disclosableValue.value) // Call itself (mapObject)
                                Disclosable.NeverSelectively(DisclosableValue.Obj(newObject))
                            }
                        }
                    }
                }
                newKey to newDisclosableValue
            }
            factory.obj(mappedEntries)
        }

    val mapArray: DeepRecursiveFunction<DisclosableArray<K, A>, DArr1> =
        DeepRecursiveFunction { arr ->
            val mappedElements = arr.content.map { disclosableElement ->
                when (disclosableElement) {
                    is Disclosable.AlwaysSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id -> {
                                val newValue = fA(disclosableValue.value)
                                Disclosable.AlwaysSelectively(DisclosableValue.Id(newValue))
                            }
                            is DisclosableValue.Arr -> {
                                // Call mapArray recursively for nested array
                                val newArray = callRecursive(disclosableValue.value) // Call itself (mapArray)
                                Disclosable.AlwaysSelectively(DisclosableValue.Arr(newArray))
                            }
                            is DisclosableValue.Obj -> {
                                // Call mapObject for nested object
                                val newObject = mapObject.callRecursive(disclosableValue.value)
                                Disclosable.AlwaysSelectively(DisclosableValue.Obj(newObject))
                            }
                        }
                    }
                    is Disclosable.NeverSelectively -> {
                        when (val disclosableValue = disclosableElement.value) {
                            is DisclosableValue.Id -> {
                                val newValue = fA(disclosableValue.value)
                                Disclosable.NeverSelectively(DisclosableValue.Id(newValue))
                            }
                            is DisclosableValue.Arr -> {
                                val newArray = callRecursive(disclosableValue.value) // Call itself (mapArray)
                                Disclosable.NeverSelectively(DisclosableValue.Arr(newArray))
                            }
                            is DisclosableValue.Obj -> {
                                val newObject = mapObject.callRecursive(disclosableValue.value)
                                Disclosable.NeverSelectively(DisclosableValue.Obj(newObject))
                            }
                        }
                    }
                }
            }
            factory.arr(mappedElements)
        }
}
