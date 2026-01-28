/*
 * Copyright (c) 2023-2026 European Commission
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

import eu.europa.ec.eudi.sdjwt.vc.SelectPath.Default.query
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun matchSingleEmpty() {
        listOf(
            ClaimPath.claim("foo"),
            ClaimPath.claim("foo").claim("bar"),
            ClaimPath.claim("address").claim("foo"),
            ClaimPath.claim("nationalities").arrayElement(100),

        ).forEach {
            assertSelectionEquals(Selection.SingleMatch(null), it)
        }
    }

    @Test
    fun detectArrayPathOverObject() {
        listOf(
            ClaimPath.claim("address").arrayElement(100),
            ClaimPath.claim("address").allArrayElements().claim("foo"),
        ).forEach {
            assertIs<IllegalStateException>(sampleJson.query(it).exceptionOrNull())
        }
    }

    @Test
    fun matchWildcardEmpty() = assertSelectionEquals(
        Selection.WildcardMatches(emptyList(), ClaimPath.claim("nationalities").allArrayElements()),
        ClaimPath.claim("nationalities").allArrayElements().allArrayElements().claim("foo"),
    )

    @Test
    fun detectStoppingOnKnownPaths() = assertSelectionEquals(
        Selection.WildcardMatches(emptyList(), ClaimPath.claim("nationalities").allArrayElements()),
        ClaimPath.claim("nationalities").allArrayElements().claim("foo"),
    )

    @Test
    fun matchTopLevel() {
        sampleJson.forEach { (attributeName, attribute) ->
            assertSelectionEquals(expected = Selection.SingleMatch(attribute), path = ClaimPath.claim(attributeName))
        }
    }

    private fun assertSelectionEquals(expected: Selection, path: ClaimPath) {
        val actual = with(SelectPath) { sampleJson.query(path) }.getOrThrow()
        assertEquals(expected, actual)
    }

    @Test
    fun matchNestedAttribute() = assertSelectionEquals(
        expected = Selection.SingleMatch(checkNotNull(checkNotNull(sampleJson["address"]).jsonObject["street_address"])),
        path = ClaimPath.claim("address").claim("street_address"),
    )

    @Test
    fun matchArray() = assertSelectionEquals(
        expected = Selection.SingleMatch(checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray)),
        path = ClaimPath.claim("degrees"),
    )

    @Test
    fun matchAll() = assertSelectionEquals(
        expected = run {
            val degrees = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray)
            Selection.WildcardMatches(
                path = ClaimPath.claim("degrees").allArrayElements(),
                matches = degrees.mapIndexed { i, degree ->
                    Selection.WildcardMatches.Match(
                        index = i,
                        value = degree,
                        concretePath = ClaimPath.claim("degrees").arrayElement(i),
                    )
                },

            )
        },
        path = ClaimPath.claim("degrees").allArrayElements(),
    )

    @Test
    fun matchNestedOfArrayElement() = assertSelectionEquals(
        expected = run {
            val degrees = checkNotNull(checkNotNull(sampleJson["degrees"]).jsonArray)
            Selection.WildcardMatches(
                path = ClaimPath.claim("degrees").allArrayElements(),
                matches = degrees.mapIndexed { i, degree ->
                    Selection.WildcardMatches.Match(
                        index = i,
                        value = degree.jsonObject["type"],
                        concretePath = ClaimPath.claim("degrees").arrayElement(i).claim("type"),
                    )
                },
            )
        },
        path = ClaimPath.claim("degrees").allArrayElements().claim("type"),
    )
}
