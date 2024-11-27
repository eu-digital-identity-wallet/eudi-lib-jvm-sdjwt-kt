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

fun interface ClaimPathMatcher {
    fun match(jsonElement: JsonElement, path: ClaimPath): Result<JsonElement?>

    companion object {
        val Default: ClaimPathMatcher = ClaimPathMatcher { jsonElement, path ->
            try {
                Result.success(jsonElement.matchPath(path))
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            }
        }
    }
}

private fun JsonElement.matchPath(path: ClaimPath): JsonElement? {
    val (head, tail) = path
    return when (head) {
        is ClaimPathElement.Named -> {
            check(this is JsonObject) {
                "Path element is $head. Was expecting a JSON object"
            }
            matchAttribute(head.value, tail)
        }

        is ClaimPathElement.Indexed -> {
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array"
            }
            matchIndex(head.value, tail)
        }

        ClaimPathElement.All -> {
            check(this is JsonArray) {
                "Path element is $head. Was expecting a JSON array"
            }
            matchWildCard(tail)
        }
    }
}

private fun JsonArray.matchWildCard(tail: ClaimPath?): JsonElement? {
    val selectedElement = this
    return if (tail != null) {
        val newValue = selectedElement.map { element ->
            checkNotNull(element.matchPath(tail))
        }
        JsonArray(newValue)
    } else selectedElement
}

private fun JsonArray.matchIndex(index: Int, tail: ClaimPath?): JsonElement? {
    val selectedElement = this[index]
    return if (tail != null) {
        checkNotNull(selectedElement)
        selectedElement.matchPath(tail)
    } else selectedElement
}

private fun JsonObject.matchAttribute(
    claimName: String,
    tail: ClaimPath?,
): JsonElement? {
    val selectedElement = this[claimName]
    return if (tail != null) {
        checkNotNull(selectedElement)
        selectedElement.matchPath(tail)
    } else selectedElement
}
