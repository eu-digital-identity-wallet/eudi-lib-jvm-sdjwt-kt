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
fun interface SelectPath {

    /**
     * Matches the given [path] to the [JsonElement]
     *
     * @receiver the JSON to match against
     * @param path the path to match
     * @return a [JsonElement] if present. In case the structure of the given [this@select]
     * doesn't comply with the [path] a [Result.Failure] is being returned
     */
    fun JsonElement.select(path: ClaimPath): Result<JsonElement?>

    /**
     * Default implementation
     */
    companion object Default : SelectPath by default()
}

//
// Implementation
//

private fun default(): SelectPath = SelectPath { path ->
    try {
        Result.success(selectPath(this to path))
    } catch (e: IllegalStateException) {
        Result.failure(e)
    }
}

private val selectPath: DeepRecursiveFunction<Pair<JsonElement, ClaimPath>, JsonElement?> =
    DeepRecursiveFunction { (element, path) ->
        val (head, tail) = path
        head.fold(
            ifClaim = { name ->
                check(element is JsonObject) {
                    "Path element is $head. Was expecting a JSON object, found $element"
                }
                val selectedElement = element[name]
                if (tail == null) selectedElement
                else selectedElement?.let { callRecursive(it to tail) }
            },
            ifArrayElement = { index ->
                check(element is JsonArray) {
                    "Path element is $head. Was expecting a JSON array, found $element"
                }
                val selectedElement = element.getOrNull(index)
                if (tail == null) selectedElement
                else selectedElement?.let { callRecursive(it to tail) }
            },
            ifAllArrayElements = {
                check(element is JsonArray) {
                    "Path element is $head. Was expecting a JSON array, found $element"
                }
                val selectedElement = element
                if (tail == null) selectedElement
                else selectedElement.mapNotNull { element -> callRecursive(element to tail) }.let(::JsonArray)
            },
        )
    }
