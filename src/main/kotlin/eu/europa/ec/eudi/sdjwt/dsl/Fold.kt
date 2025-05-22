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

fun <K, A, B> DisclosableObject<K, A>.fold(
    objectHandlers: DisclosableObjectFoldHandlers<K, A, B>,
    arrayHandlers: DisclosableArrayFoldHandlers<K, A, B>,
    initialValue: B,
    combine: (B, B) -> B,
): B {
    val context = FoldContext(objectHandlers, arrayHandlers, initialValue, combine)
    return context.foldObject(this)
}

fun <K, A, B> DisclosableArray<K, A>.fold(
    objectHandlers: DisclosableObjectFoldHandlers<K, A, B>,
    arrayHandlers: DisclosableArrayFoldHandlers<K, A, B>,
    initialValue: B,
    combine: (B, B) -> B,
): B {
    val context = FoldContext(objectHandlers, arrayHandlers, initialValue, combine)
    return context.foldArray(this) // Start the folding process by invoking the DeepRecursiveFunction
}

/**
 * Interface defining the handlers for folding a DisclosableObject.
 * Each function returns a B, representing the folded result for that specific element.
 */
interface DisclosableObjectFoldHandlers<K, A, B> {
    fun ifAlwaysSelectivelyDisclosableId(key: K, value: A): B
    fun ifAlwaysSelectivelyDisclosableArr(key: K, foldedArrayResult: B): B
    fun ifAlwaysSelectivelyDisclosableObj(key: K, foldedObjectResult: B): B
    fun ifNeverSelectivelyDisclosableId(key: K, value: A): B
    fun ifNeverSelectivelyDisclosableArr(key: K, foldedArrayResult: B): B
    fun ifNeverSelectivelyDisclosableObj(key: K, foldedObjectResult: B): B
}

/**
 * Interface defining the handlers for folding a DisclosableArray.
 * Each function returns a B, representing the folded result for that specific element.
 */
interface DisclosableArrayFoldHandlers<K, A, B> {
    fun ifAlwaysSelectivelyDisclosableId(index: Int, value: A): B
    fun ifAlwaysSelectivelyDisclosableArr(index: Int, foldedArrayResult: B): B
    fun ifAlwaysSelectivelyDisclosableObj(index: Int, foldedObjectResult: B): B
    fun ifNeverSelectivelyDisclosableId(index: Int, value: A): B
    fun ifNeverSelectivelyDisclosableArr(index: Int, foldedArrayResult: B): B
    fun ifNeverSelectivelyDisclosableObj(index: Int, foldedObjectResult: B): B
}

//
// Implementation
//

private class FoldContext<K, A, B>(
    private val objectHandlers: DisclosableObjectFoldHandlers<K, A, B>,
    private val arrayHandlers: DisclosableArrayFoldHandlers<K, A, B>,
    private val initialValue: B,
    private val combine: (B, B) -> B,
) {
    // DeepRecursiveFunction for folding objects
    val foldObject: DeepRecursiveFunction<DisclosableObject<K, A>, B> = DeepRecursiveFunction { obj ->
        obj.content.entries.fold(initialValue) { acc, (key, disclosableElement) ->
            val elementResult = when (disclosableElement) {
                is Disclosable.AlwaysSelectively -> {
                    when (val disclosableValue = disclosableElement.value) {
                        is DisclosableValue.Id -> objectHandlers.ifAlwaysSelectivelyDisclosableId(key, disclosableValue.value)
                        is DisclosableValue.Arr -> {
                            val foldedArrayResult = foldArray.callRecursive(disclosableValue.value)
                            objectHandlers.ifAlwaysSelectivelyDisclosableArr(key, foldedArrayResult)
                        }
                        is DisclosableValue.Obj -> {
                            val foldedObjectResult = callRecursive(disclosableValue.value)
                            objectHandlers.ifAlwaysSelectivelyDisclosableObj(key, foldedObjectResult)
                        }
                    }
                }
                is Disclosable.NeverSelectively -> {
                    when (val disclosableValue = disclosableElement.value) {
                        is DisclosableValue.Id -> objectHandlers.ifNeverSelectivelyDisclosableId(key, disclosableValue.value)
                        is DisclosableValue.Arr -> {
                            val foldedArrayResult = foldArray.callRecursive(disclosableValue.value)
                            objectHandlers.ifNeverSelectivelyDisclosableArr(key, foldedArrayResult)
                        }
                        is DisclosableValue.Obj -> {
                            val foldedObjectResult = callRecursive(disclosableValue.value)
                            objectHandlers.ifNeverSelectivelyDisclosableObj(key, foldedObjectResult)
                        }
                    }
                }
            }
            combine(acc, elementResult)
        }
    }

    val foldArray: DeepRecursiveFunction<DisclosableArray<K, A>, B> = DeepRecursiveFunction { arr ->
        arr.content.foldIndexed(initialValue) { index, acc, disclosableElement ->
            val elementResult = when (disclosableElement) {
                is Disclosable.AlwaysSelectively -> {
                    when (val disclosableValue = disclosableElement.value) {
                        is DisclosableValue.Id -> arrayHandlers.ifAlwaysSelectivelyDisclosableId(index, disclosableValue.value)
                        is DisclosableValue.Arr -> {
                            // Calling 'foldArray' recursively (itself)
                            val foldedInnerArrayResult = callRecursive(disclosableValue.value)
                            arrayHandlers.ifAlwaysSelectivelyDisclosableArr(index, foldedInnerArrayResult)
                        }
                        is DisclosableValue.Obj -> {
                            // Calling 'foldObject' from within 'foldArray'
                            val foldedInnerObjectResult = foldObject.callRecursive(disclosableValue.value)
                            arrayHandlers.ifAlwaysSelectivelyDisclosableObj(index, foldedInnerObjectResult)
                        }
                    }
                }
                is Disclosable.NeverSelectively -> {
                    when (val disclosableValue = disclosableElement.value) {
                        is DisclosableValue.Id -> arrayHandlers.ifNeverSelectivelyDisclosableId(index, disclosableValue.value)
                        is DisclosableValue.Arr -> {
                            // Calling 'foldArray' recursively (itself)
                            val foldedInnerArrayResult = callRecursive(disclosableValue.value)
                            arrayHandlers.ifNeverSelectivelyDisclosableArr(index, foldedInnerArrayResult)
                        }
                        is DisclosableValue.Obj -> {
                            // Calling 'foldObject' from within 'foldArray'
                            val foldedInnerObjectResult = foldObject.callRecursive(disclosableValue.value)
                            arrayHandlers.ifNeverSelectivelyDisclosableObj(index, foldedInnerObjectResult)
                        }
                    }
                }
            }
            combine(acc, elementResult)
        }
    }
}
