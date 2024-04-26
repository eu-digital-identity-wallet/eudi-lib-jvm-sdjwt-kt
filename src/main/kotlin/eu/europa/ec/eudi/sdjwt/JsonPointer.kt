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

import com.eygraber.uri.UriCodec

/**
 * A pointer to a specific value withing a JSON object.
 */
class JsonPointer private constructor(internal val tokens: List<String>) {

    /**
     * Whether this is the root element. (i.e. the whole JSON object)
     */
    val isRoot: Boolean
        get() = tokens.isEmpty()

    /**
     * Gets the parent of this pointer, if any.
     */
    fun parent(): JsonPointer? =
        when {
            isRoot -> null
            else -> JsonPointer(tokens.dropLast(1))
        }

    /**
     * Gets a new pointer that points to a child of this pointer.
     */
    fun child(child: String): JsonPointer = JsonPointer(tokens + child)

    /**
     * Gets a new pointer that points to a child of this pointer. Applicable for array elements.
     */
    fun child(index: Int): Result<JsonPointer> =
        runCatching {
            require(index >= 0) { "index must be greater than or equal to '0'" }
            child(index.toString())
        }

    override fun toString(): String = toString { it.encode() }

    /**
     * Converts this pointer to a URI fragment.
     */
    fun toUriFragment(): Result<String> = runCatching { "#${toString { UriCodec.encode(it.encode()) }}" }

    /**
     * Converts this pointer to a string encoding each token using the provided encoding function.
     */
    private fun toString(encode: (String) -> String): String =
        tokens.fold("") { accumulator, current -> "$accumulator/${encode(current)}" }

    override fun equals(other: Any?): Boolean =
        when {
            this === other -> true
            other is JsonPointer -> tokens == other.tokens
            else -> false
        }

    override fun hashCode(): Int = tokens.hashCode()

    companion object {

        /**
         * Creates a new pointer for the root element.
         */
        fun root(): JsonPointer = JsonPointer(emptyList())

        /**
         * Parses a string as a pointer.
         */
        fun parse(pointer: String): Result<JsonPointer> = parse(pointer) { it.decode() }

        /**
         * Parses a URI fragment as a pointer.
         */
        fun fromUriFragment(fragment: String): Result<JsonPointer> =
            runCatching {
                require(fragment.startsWith("#")) { "fragment must start with a '#'" }
                parse(fragment.drop(1)) { UriCodec.decode(it, throwOnFailure = true).decode() }.getOrThrow()
            }

        /**
         * Parses a string as a pointer. Each token is decoded using the provided decoding function.
         */
        private fun parse(pointer: String, decode: (String) -> String): Result<JsonPointer> =
            runCatching {
                when {
                    pointer.isEmpty() -> root()
                    else -> {
                        require(pointer.startsWith("/")) { "pointer must start with a '/'" }
                        pointer.split("/")
                            .drop(1)
                            .map { decode(it) }
                            .let { JsonPointer(it) }
                    }
                }
            }

        /**
         * Encodes a token by replacing instances of '~' with '~0', and instances of '/' with '~1'.
         */
        private fun String.encode() = replace("~", "~0").replace("/", "~1")

        /**
         * Regex used to check whether tokens contain invalid escape sequences.
         */
        private val invalidEscapeSequence = "(~[^0-1])|(~\$)".toRegex()

        /**
         * Decodes a token by replacing instances of '~1' with '/', and instances of '~0' with '~'.
         */
        private fun String.decode(): String {
            require(!matches(invalidEscapeSequence)) { "token contains an invalid escape sequence" }
            return replace("~1", "/").replace("~0", "~")
        }
    }
}
