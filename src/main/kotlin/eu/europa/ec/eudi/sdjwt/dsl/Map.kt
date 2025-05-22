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

fun <K, K1, A, A1> DisclosableObject<K, A>.map(
    factory: DisclosableContainerFactory<K1, A1> = DisclosableContainerFactory.default(),
    fK: (K) -> K1,
    fA: (A) -> A1,
): DisclosableObject<K1, A1> {
    val context = MapFoldContext(factory, fK, fA)
    return context.mapObject(this)
}

fun <K, K1, A, A1> DisclosableArray<K, A>.map(
    factory: DisclosableContainerFactory<K1, A1> = DisclosableContainerFactory.default(),
    fK: (K) -> K1,
    fA: (A) -> A1,
): DisclosableArray<K1, A1> {
    val context = MapFoldContext(factory, fK, fA)
    return context.mapArray(this)
}

//
// Implementation
//

private class MapFoldContext<K, K1, A, A1>(
    private val factory: DisclosableContainerFactory<K1, A1>,
    private val fK: (K) -> K1,
    private val fA: (A) -> A1,
) {

    val mapObject: DeepRecursiveFunction<DisclosableObject<K, A>, DisclosableObject<K1, A1>> =
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

    val mapArray: DeepRecursiveFunction<DisclosableArray<K, A>, DisclosableArray<K1, A1>> =
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
