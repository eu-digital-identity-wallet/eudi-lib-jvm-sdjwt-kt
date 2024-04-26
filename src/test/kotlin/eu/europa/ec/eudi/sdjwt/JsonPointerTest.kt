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

/**
 * Test cases for [JsonPointer].
 */
internal class JsonPointerTest {

    @Test
    internal fun `handles root properly`() {
        val expectedTokens = emptyList<String>()

        val pointer = JsonPointer.root()
        assertEquals(emptyList(), pointer.tokens)
        assertEquals("", pointer.toString())
        assertEquals("#", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#").getOrThrow().tokens)
    }

    @Test
    internal fun `handles 'foo' properly`() {
        val expectedTokens = listOf("foo")

        val pointer = JsonPointer.root().child("foo")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/foo", pointer.toString())
        assertEquals("#/foo", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/foo").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/foo").getOrThrow().tokens)
    }

    // foo[0]
    @Test
    internal fun `handles 'foo~0' properly`() {
        val expectedTokens = listOf("foo", "0")

        val pointer = JsonPointer.root().child("foo").child(0).getOrThrow()
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/foo/0", pointer.toString())
        assertEquals("#/foo/0", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/foo/0").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/foo/0").getOrThrow().tokens)
    }

    @Test
    internal fun `handles '' properly`() {
        val expectedTokens = listOf("")

        val pointer = JsonPointer.root().child("")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/", pointer.toString())
        assertEquals("#/", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/").getOrThrow().tokens)
    }

    // a/b
    @Test
    internal fun `handles 'a~b' properly`() {
        val expectedTokens = listOf("a/b")

        val pointer = JsonPointer.root().child("a/b")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/a~1b", pointer.toString())
        assertEquals("#/a~1b", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/a~1b").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/a~1b").getOrThrow().tokens)
    }

    // c%d
    @Test
    internal fun `handles 'c~d' properly`() {
        val expectedTokens = listOf("c%d")

        val pointer = JsonPointer.root().child("c%d")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/c%d", pointer.toString())
        assertEquals("#/c%25d", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/c%d").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/c%25d").getOrThrow().tokens)
    }

    @Test
    internal fun `handles 'e^f' properly`() {
        val expectedTokens = listOf("e^f")

        val pointer = JsonPointer.root().child("e^f")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/e^f", pointer.toString())
        assertEquals("#/e%5Ef", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/e^f").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/e%5Ef").getOrThrow().tokens)
    }

    // g|h
    @Test
    internal fun `handles 'g~h' properly`() {
        val expectedTokens = listOf("g|h")

        val pointer = JsonPointer.root().child("g|h")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/g|h", pointer.toString())
        assertEquals("#/g%7Ch", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/g|h").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/g%7Ch").getOrThrow().tokens)
    }

    // i\\j
    @Test
    internal fun `handles 'i~~j' properly`() {
        val expectedTokens = listOf("i\\j")

        val pointer = JsonPointer.root().child("i\\j")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/i\\j", pointer.toString())
        assertEquals("#/i%5Cj", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/i\\j").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/i%5Cj").getOrThrow().tokens)
    }

    // k\"l
    @Test
    internal fun `handles 'k~~l' properly`() {
        val expectedTokens = listOf("k\"l")

        val pointer = JsonPointer.root().child("k\"l")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/k\"l", pointer.toString())
        assertEquals("#/k%22l", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/k\"l").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/k%22l").getOrThrow().tokens)
    }

    @Test
    internal fun `handles ' ' properly`() {
        val expectedTokens = listOf(" ")

        val pointer = JsonPointer.root().child(" ")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/ ", pointer.toString())
        assertEquals("#/%20", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/ ").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/%20").getOrThrow().tokens)
    }

    @Test
    internal fun `handles 'm~n' properly`() {
        val expectedTokens = listOf("m~n")

        val pointer = JsonPointer.root().child("m~n")
        assertEquals(expectedTokens, pointer.tokens)
        assertEquals("/m~0n", pointer.toString())
        assertEquals("#/m~0n", pointer.toUriFragment().getOrThrow())

        assertEquals(expectedTokens, JsonPointer.parse("/m~0n").getOrThrow().tokens)
        assertEquals(expectedTokens, JsonPointer.fromUriFragment("#/m~0n").getOrThrow().tokens)
    }
}
