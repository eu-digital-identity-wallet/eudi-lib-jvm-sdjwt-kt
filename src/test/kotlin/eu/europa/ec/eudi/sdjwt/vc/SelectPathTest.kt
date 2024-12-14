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

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectPathTest {
    val jsonSupport = Json { ignoreUnknownKeys = false }
    val sampleJson = """
        {
          "vct": "https://betelgeuse.example.com/education_credential",
          "name": "Arthur Dent",
          "address": {
            "street_address": "42 Market Street",
            "city": "Milliways",
            "postal_code": "12345"
          },
          "degrees": [
            {
              "type": "Bachelor of Science",
              "university": "University of Betelgeuse"
            },
            {
              "type": "Master of Science",
              "university": "University of Betelgeuse"
            }
          ],
          "nationalities": ["British", "Betelgeusian"]
        }
    """.trimIndent().let {
        jsonSupport.parseToJsonElement(it).jsonObject
    }

    @Test
    fun matchTopLevel() {
        sampleJson.forEach { (attributeName, attribute) ->
            assetSelectionEquals(expected = attribute, path = ClaimPath.claim(attributeName))
        }
    }

    private fun assetSelectionEquals(expected: JsonElement, path: ClaimPath) {
        val actual = with(SelectPath) { sampleJson.select(path) }.getOrThrow()
        assertEquals(expected, actual)
    }

    @Test
    fun matchNestedAttribute() = assetSelectionEquals(
        expected = checkNotNull(checkNotNull(sampleJson["address"]).jsonObject["street_address"]),
        path = ClaimPath.claim("address").claim("street_address"),
    )

    @Test
    fun matchArray() = assetSelectionEquals(
        expected = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray),
        path = ClaimPath.claim("degrees"),
    )

    @Test
    fun matchAll() = assetSelectionEquals(
        expected = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray),
        path = ClaimPath.claim("degrees").allArrayElements(),
    )

    @Test
    fun matchArrayElement() {
        val degreesPath = ClaimPath.claim("degrees")
        val degrees = checkNotNull(sampleJson["degrees"]).jsonArray
        degrees.forEachIndexed { index, degree ->
            assetSelectionEquals(expected = degree, path = degreesPath.arrayElement(index))
        }
    }

    @Test
    fun matchNestedOfArrayElement() = assetSelectionEquals(
        expected = run {
            val degrees = checkNotNull(sampleJson["degrees"]).jsonArray
            degrees.map { element ->
                check(element is JsonObject)
                checkNotNull(element["type"])
            }.let(::JsonArray)
        },
        path = ClaimPath.claim("degrees").allArrayElements().claim("type"),
    )
}
