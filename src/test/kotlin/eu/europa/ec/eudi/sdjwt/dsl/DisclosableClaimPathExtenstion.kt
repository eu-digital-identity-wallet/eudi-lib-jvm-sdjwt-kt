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

import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement

fun <K, A, B> eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableObject<K, A>.calculateSelectiveDisclosureMap(
    claimPathOf: (K) -> ClaimPath,
    valueTransformer: (eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A>) -> B,
): Map<ClaimPath, B> {
    val fn = calculateSelectiveDisclosureMapFn(claimPathOf, valueTransformer)
    return fn(this)
}

private fun <K, A, B> calculateSelectiveDisclosureMapFn(
    claimPathOf: (K) -> ClaimPath,
    valueTransformer: (eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A>) -> B,
): DeepRecursiveFunction<eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableObject<K, A>, Map<ClaimPath, B>> = DeepRecursiveFunction {
        disclosableObject ->
    val pathsMap: MutableMap<ClaimPath, B> = mutableMapOf()
    val processElement = processElement(claimPathOf, valueTransformer, pathsMap)

    // Start the recursive processing for each top-level element in the DisclosableObject
    disclosableObject.content.forEach { (key, element) ->
        val initialPath = claimPathOf(key)
        processElement.callRecursive(element to initialPath)
    }

    pathsMap.toMap()
}

private fun <K, A, B> processValue(
    claimPathOf: (K) -> ClaimPath,
    valueTransformer: (eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A>) -> B,
    pathsMap: MutableMap<ClaimPath, B>,
) =
    DeepRecursiveFunction<Pair<eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue<K, A>, ClaimPath?>, Unit> {
            (disclosableValue, currentPath) ->
        val processElement = processElement(claimPathOf, valueTransformer, pathsMap)
        when (disclosableValue) {
            is eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A> -> {
                requireNotNull(currentPath) { "Selective disclosable element must have a defined ClaimPath." }
                val newValue = valueTransformer(disclosableValue)
                pathsMap[currentPath] = newValue
            }

            is eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Obj<K, A> -> {
                // Recursively process each element in the object
                disclosableValue.value.content.forEach { (key, element) ->
                    val keyPath = claimPathOf(key)
                    val nextPath =
                        currentPath
                            ?.plus(keyPath)
                            ?: keyPath
                    processElement.callRecursive(element to nextPath)
                }
            }

            is eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Arr<K, A> -> {
                val nextPath = currentPath
                    ?.allArrayElements()
                    ?: ClaimPath(ClaimPathElement.AllArrayElements)

                disclosableValue.value.content.forEachIndexed { index, element ->
                    processElement.callRecursive(element to nextPath)
                }
            }
        }
    }

private fun <K, A, B> processElement(
    claimPathOf: (K) -> ClaimPath,
    valueTransformer: (eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A>) -> B,
    pathsMap: MutableMap<ClaimPath, B>,
): DeepRecursiveFunction<Pair<eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableElement<K, A>, ClaimPath?>, Unit> =
    DeepRecursiveFunction { (disclosableElement, currentPath) ->
        val processValue = processValue(claimPathOf, valueTransformer, pathsMap)
        when (disclosableElement) {
            is Disclosable.AlwaysSelectively<eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue<K, A>> -> {
                // This element itself is selectively disclosable.
                requireNotNull(currentPath) { "Selective disclosable element must have a defined ClaimPath." }

                val dv = disclosableElement.value
                when (dv) {
                    is eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue.Id<K, A> -> {
                        val newValue = valueTransformer(dv)
                        pathsMap[currentPath] = newValue
                    }

                    else -> processValue.callRecursive(dv to currentPath)
                }
            }

            is Disclosable.NeverSelectively<eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue<K, A>> -> {
                // This element is not selectively disclosable itself,
                // but it might contain nested selectively disclosable items. Recurse into its value.
                processValue.callRecursive(disclosableElement.value to currentPath)
            }
        }
    }
