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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClaimPathTest {
    val jsonSupport = Json { ignoreUnknownKeys = true }
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
        Json.parseToJsonElement(it).jsonObject
    }

    @Test
    fun matchTopLevel() {
        sampleJson.forEach { (attributeName, attribute) ->
            assertMatchEquals(expected = attribute, path = ClaimPath.attribute(attributeName))
        }
    }
    private fun assertMatchEquals(expected: JsonElement, path: ClaimPath) {
        val actual = ClaimPathMatcher.Default.match(sampleJson, path).getOrThrow()
        assertEquals(expected, actual)
    }

    @Test
    fun matchNestedAttribute() = assertMatchEquals(
        expected = checkNotNull(checkNotNull(sampleJson["address"]).jsonObject["street_address"]),
        path = ClaimPath.attribute("address").attribute("street_address"),
    )

    @Test
    fun matchArray() = assertMatchEquals(
        expected = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray),
        path = ClaimPath.attribute("degrees"),
    )

    @Test
    fun matchAll() = assertMatchEquals(
        expected = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray),
        path = ClaimPath.attribute("degrees").all(),
    )

    @Test
    fun matchArrayElement() {
        val degreesPath = ClaimPath.attribute("degrees")
        val degrees = checkNotNull(sampleJson["degrees"]).jsonArray
        degrees.forEachIndexed { index, degree ->
            assertMatchEquals(expected = degree, path = degreesPath.at(index))
        }
    }

    @Test
    fun matchNestedOfArrayElement() = assertMatchEquals(
        expected = run {
            val degrees = checkNotNull(sampleJson["degrees"]).jsonArray
            degrees.map { element ->
                check(element is JsonObject)
                checkNotNull(element["type"])
            }.let(::JsonArray)
        },
        path = ClaimPath.attribute("degrees").all().attribute("type"),
    )

    @Test
    fun `parsing happy path`() {
        val jsonExamples = listOf(
            """["name"]""",
            """["address"]""",
            """["address", "street_address"]""",
            """["degrees", null, "type"]""",
        )

        jsonExamples.forEach { example ->
            val path = jsonSupport.decodeFromString(ClaimPath.serializer(), example)
            println(path)
        }
    }
}
