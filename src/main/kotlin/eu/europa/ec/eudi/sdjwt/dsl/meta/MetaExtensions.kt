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

import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

fun DisclosableObjectMetadata.claimPaths(): Set<ClaimPath> {
    val collectedPaths = mutableSetOf<ClaimPath>()

    fun collectRecursive(
        currentPrefix: ClaimPath?,
        disclosableElement: DisclosableElementMetadata,
    ) {
        val disclosableValue = disclosableElement.value
        when (disclosableValue) {
            is DisclosableValue.Id -> {
                // This is a leaf node. The currentPrefix already represents its full path.
                currentPrefix?.let { collectedPaths.add(it) }
            }
            is DisclosableValue.Obj -> {
                val obj = disclosableValue.value as DisclosableObjectMetadata // Cast back to specific type for content

                // Add the path of the object container itself
                currentPrefix?.let { collectedPaths.add(it) }

                // Recursively collect paths for each attribute within the object
                obj.content.forEach { (key, childElement) ->
                    // Use the fluent 'claim' method on the currentPrefix
                    val newPrefix = currentPrefix?.claim(key) ?: ClaimPath.claim(key)
                    collectRecursive(newPrefix, childElement)
                }
            }
            is DisclosableValue.Arr -> {
                val arr = disclosableValue.value as DisclosableArrayMetadata

                currentPrefix?.let { collectedPaths.add(it) }

                val newPrefixForArrayContent = checkNotNull(currentPrefix).allArrayElements()

                arr.content.forEach { childElement ->
                    collectRecursive(newPrefixForArrayContent, childElement)
                }
            }
        }
    }
    content.forEach { (key, disclosableElement) ->
        val initialPath = ClaimPath.claim(key)
        collectedPaths.add(initialPath)
        collectRecursive(initialPath, disclosableElement)
    }

    return collectedPaths
}
