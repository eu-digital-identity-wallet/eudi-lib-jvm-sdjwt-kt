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
        Result.success(selectPath(path))
    } catch (e: IllegalStateException) {
        Result.failure(e)
    }
}

private fun JsonElement.selectPath(path: ClaimPath): JsonElement? {
    val (head, tail) = path
    return head.fold(
        ifClaim = { name ->
            check(this is JsonObject) {
                "Path element is $head. Was expecting a JSON object, found $this"
            }
            val selectedElement = this[name]
            if (tail == null) selectedElement
            else selectedElement?.selectPath(tail)
        },
        ifArrayElement = { index ->
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array, found $this"
            }
            val selectedElement = this.getOrNull(index)
            if (tail == null) selectedElement
            else selectedElement?.selectPath(tail)
        },
        ifAllArrayElements = {
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array, found $this"
            }
            val selectedElement = this
            if (tail == null) selectedElement
            else selectedElement.mapNotNull { element -> element.selectPath(tail) }.let(::JsonArray)
        },
    )
}
