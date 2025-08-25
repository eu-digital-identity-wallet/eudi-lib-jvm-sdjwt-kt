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
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.runCatchingCancellable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface Selection {
    /**
     * Represents a successfully found value (terminal path segment)
     * @property value The found JSON element (null if explicitly null in JSON)
     */
    @JvmInline
    value class SingleMatch(val value: JsonElement?) : Selection

    /**
     * Represents multiple matches from wildcard navigation
     * @property matches List of successful matches (empty if no matches)
     * @property path The complete claim path used
     */
    data class WildcardMatches(
        val matches: List<Match>,
        val path: ClaimPath,
    ) : Selection {

        /**
         * Individual wildcard match with context
         * @property index Source array index
         * @property value Found value (null if explicit JSON null)
         * @property concretePath Actual path taken including indices
         */
        data class Match(
            val index: Int,
            val value: JsonElement?,
            val concretePath: ClaimPath,
        )
    }

    fun toJsonElement(): JsonElement? = when (this) {
        is SingleMatch -> value
        is WildcardMatches -> matches.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.let(::JsonArray)
    }
}

/**
 * Matches a [ClaimPath] to a [JsonElement]
 */
fun interface SelectPath {

    /**
     * Matches the given [path] to the [JsonElement]
     *
     * @receiver the JSON to match against
     * @param path the path to match
     * @return a [Selection]
     */
    fun JsonElement.query(path: ClaimPath): Result<Selection>

    @Deprecated(
        message = "Will be removed",
        replaceWith = ReplaceWith("query(path).map { it.toJsonElement() }"),
    )
    fun JsonElement.select(path: ClaimPath): Result<JsonElement?> =
        query(path).map { it.toJsonElement() }

    /**
     * Default implementation
     */
    companion object Default : SelectPath by default
}

//
// Implementation
//
private val default: SelectPath = SelectPath { path ->

    val initialParams = SelectionParams(
        currentElement = this,
        remainingPathElements = path.value,
        accumulatedWildcardScopePathElements = emptyList(),
        accumulatedConcretePathElements = emptyList(),
    )
    runCatchingCancellable { deepRecursiveSelector(initialParams) }
}

private val deepRecursiveSelector = DeepRecursiveFunction<SelectionParams, Selection> { params ->

    val (currentElement, remainingPathElements, accumulatedWildcardScopePathElements, accumulatedConcretePathElements) = params

    if (remainingPathElements.isEmpty()) {
        return@DeepRecursiveFunction Selection.SingleMatch(currentElement)
    }

    val head = remainingPathElements.first()
    val tailElements = remainingPathElements.drop(1)

    // This path is used if 'head' is AllArrayElements to report which path caused the wildcard match.
    val newWildcardScopePath = accumulatedWildcardScopePathElements + head

    head.fold(
        ifClaim = { name ->
            check(currentElement is JsonObject) {
                "Path segment '$name' expects an object, found ${currentElement::class.simpleName} at ${params.pathForError()}"
            }
            val nextElement = currentElement[name]
            if (nextElement == null || tailElements.isEmpty()) {
                Selection.SingleMatch(nextElement)
            } else {
                val newConcretePathElements = accumulatedConcretePathElements + head
                val nextParams =
                    SelectionParams(
                        currentElement = nextElement,
                        remainingPathElements = tailElements,
                        accumulatedWildcardScopePathElements = newWildcardScopePath,
                        accumulatedConcretePathElements = newConcretePathElements,
                    )
                callRecursive(nextParams)
            }
        },
        ifArrayElement = { index ->
            val newConcretePathElements = accumulatedConcretePathElements + head
            check(currentElement is JsonArray) {
                "Path segment for index $index expects an array, found ${currentElement::class.simpleName} at ${params.pathForError()}"
            }
            val nextElement = currentElement.getOrNull(index)
            if (nextElement == null || tailElements.isEmpty()) {
                Selection.SingleMatch(nextElement)
            } else {
                val nextParams =
                    SelectionParams(
                        currentElement = nextElement,
                        remainingPathElements = tailElements,
                        accumulatedWildcardScopePathElements = newWildcardScopePath,
                        accumulatedConcretePathElements = newConcretePathElements,
                    )
                callRecursive(nextParams)
            }
        },
        ifAllArrayElements = {
            check(currentElement is JsonArray) {
                "Path segment 'all array elements' expects an array, found ${currentElement::class.simpleName} at ${params.pathForError()}"
            }

            val matches = mutableListOf<Selection.WildcardMatches.Match>()
            val wildcardExpansionPath = buildClaimPathOrFail(newWildcardScopePath, "wildcard expansion path")

            for ((index, arrayItem) in currentElement.withIndex()) {
                val concretePathToCurrentArrayItemElements =
                    accumulatedConcretePathElements + ClaimPathElement.ArrayElement(index)

                if (tailElements.isEmpty()) { // Wildcard is the last element in the path
                    matches.add(
                        Selection.WildcardMatches.Match(
                            index = index,
                            value = arrayItem,
                            concretePath = buildClaimPathOrFail(
                                concretePathToCurrentArrayItemElements,
                                "match concrete path",
                            ),
                        ),
                    )
                } else { // Path continues after wildcard
                    // For the sub-selection, its WildcardMatches.path should be relative to its part of the path.
                    // So, accumulatedWildcardScopePathElements starts fresh from tailElements for the sub-problem.
                    val subSelectionParams = SelectionParams(
                        currentElement = arrayItem,
                        remainingPathElements = tailElements,
                        accumulatedWildcardScopePathElements = emptyList(), // Reset for sub-problem's own wildcard scope
                        accumulatedConcretePathElements = concretePathToCurrentArrayItemElements,
                    )

                    try {
                        val subSelection = callRecursive(subSelectionParams)
                        val finalConcretePathForThisMatch = buildClaimPathOrFail(
                            concretePathToCurrentArrayItemElements + tailElements,
                            "final match concrete path",
                        )
                        matches.add(subSelection.toWildcardMatch(index, finalConcretePathForThisMatch))
                    } catch (_: Exception) {
                    }
                }
            }
            Selection.WildcardMatches(matches, wildcardExpansionPath)
        },
    )
}

// Data class to hold the parameters for the deep recursive function
private data class SelectionParams(
    val currentElement: JsonElement,
    val remainingPathElements: List<ClaimPathElement>,
    val accumulatedWildcardScopePathElements: List<ClaimPathElement>,
    val accumulatedConcretePathElements: List<ClaimPathElement>,
)

private fun Selection.toWildcardMatch(
    index: Int,
    finalConcretePathForThisMatch: ClaimPath,
): Selection.WildcardMatches.Match {
    val json = when (this) {
        is Selection.SingleMatch -> value
        is Selection.WildcardMatches -> JsonArray(matches.mapNotNull { it.value })
    }
    return Selection.WildcardMatches.Match(index, json, finalConcretePathForThisMatch)
}

private fun SelectionParams.pathForError() =
    formatPathForError(accumulatedConcretePathElements)

private fun buildClaimPathOrFail(
    elements: List<ClaimPathElement>,
    context: String,
): ClaimPath {
    require(elements.isNotEmpty()) {
        "Cannot build ClaimPath for $context from empty elements list. This indicates a logic error."
    }
    return ClaimPath(elements.first(), *elements.drop(1).toTypedArray())
}

private fun formatPathForError(elements: List<ClaimPathElement>): String {
    return if (elements.isEmpty()) "[root]"
    else try {
        buildClaimPathOrFail(elements, "error formatting").toString()
    } catch (_: IllegalArgumentException) {
        elements.joinToString(prefix = "[", separator = ", ", postfix = "]") {
            when (it) {
                is ClaimPathElement.Claim -> "\"${it.name}\""
                is ClaimPathElement.ArrayElement -> it.index.toString()
                is ClaimPathElement.AllArrayElements -> "null" // As per its toString()
            }
        }
    }
}
