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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Matches a [ClaimPath] to a [JsonElement]
 */
fun interface MatchClaimPath {

    /**
     * Matches the given [path] to the [jsonElement]
     *
     * @param path the path to match
     * @param jsonElement the JSON to match against
     * @return a [JsonElement] if present. In case the structure of the given [jsonElement]
     * doesn't complies with the [path] a [Result.Failure] is being returned
     */
    fun match(jsonElement: JsonElement, path: ClaimPath): Result<JsonElement?>

    /**
     * Default implementation
     */
    companion object Default : MatchClaimPath by default()
}

//
// Implementation
//

private fun default(): MatchClaimPath = MatchClaimPath { jsonElement, path ->
    try {
        Result.success(jsonElement.matchPath(path))
    } catch (e: IllegalStateException) {
        Result.failure(e)
    }
}

private fun JsonElement.matchPath(path: ClaimPath): JsonElement? {
    val (head, tail) = path
    return when (head) {
        is ClaimPathElement.Named -> {
            check(this is JsonObject) {
                "Path element is $head. Was expecting a JSON object, found $this"
            }
            val name = head.value
            matchAttributeAndThen(name, tail)
        }

        is ClaimPathElement.Indexed -> {
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array, found $this"
            }
            val index = head.value
            matchIndexAndThen(index, tail)
        }

        ClaimPathElement.All -> {
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array, found $this"
            }
            matchWildCardAndThen(tail)
        }
    }
}

private fun JsonArray.matchWildCardAndThen(tail: ClaimPath?): JsonElement? {
    val selectedElement = this
    return if (tail != null) {
        val newValue = selectedElement.map { element ->
            checkNotNull(element.matchPath(tail))
        }
        JsonArray(newValue)
    } else selectedElement
}

private fun JsonArray.matchIndexAndThen(index: Int, tail: ClaimPath?): JsonElement? {
    val selectedElement = this[index]
    return if (tail != null) {
        checkNotNull(selectedElement)
        selectedElement.matchPath(tail)
    } else selectedElement
}

private fun JsonObject.matchAttributeAndThen(
    claimName: String,
    tail: ClaimPath?,
): JsonElement? {
    val selectedElement = this[claimName]
    return if (tail != null) {
        checkNotNull(selectedElement)
        selectedElement.matchPath(tail)
    } else selectedElement
}
