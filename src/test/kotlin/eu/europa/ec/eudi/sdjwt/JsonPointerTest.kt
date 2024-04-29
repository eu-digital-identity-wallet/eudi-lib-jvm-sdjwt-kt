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
package eu.europa.ec.eudi.sdjwt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test cases for [JsonPointer].
 */
internal class JsonPointerTest {

    @Test
    internal fun `verify toString and parse`() {
        testData.forEach { (string, tokens) ->
            val recreated = tokens.fold(JsonPointer.Root) { accumulator, token -> accumulator.child(token) }
            assertEquals(string, recreated.toString())

            val parsed = assertNotNull(JsonPointer.parse(string))
            assertEquals(tokens, parsed.tokens)
        }
    }

    private val testData = listOf(
        "" to emptyList(),
        "/foo" to listOf("foo"),
        "/foo/0" to listOf("foo", "0"),
        "/" to listOf(""),
        "/a~1b" to listOf("a/b"),
        "/c%d" to listOf("c%d"),
        "/e^f" to listOf("e^f"),
        "/g|h" to listOf("g|h"),
        "/i\\j" to listOf("i\\j"),
        "/k\"l" to listOf("k\"l"),
        "/ " to listOf(" "),
        "/m~0n" to listOf("m~n"),
    )
}
