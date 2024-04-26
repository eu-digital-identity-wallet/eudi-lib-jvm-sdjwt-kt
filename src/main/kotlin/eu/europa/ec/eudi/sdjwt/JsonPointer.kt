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

/**
 * A pointer to a specific value withing a JSON object.
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC6901</a>
 */
class JsonPointer private constructor(internal val tokens: List<String>) {

    /**
     * Gets the parent of this pointer, if any.
     */
    fun parent(): JsonPointer? =
        when (this) {
            Root -> null
            else -> JsonPointer(tokens.dropLast(1))
        }

    /**
     * Gets a new pointer that points to a child of this pointer.
     */
    fun child(child: String): JsonPointer = JsonPointer(tokens + child)

    /**
     * Gets a new pointer that points to a child of this pointer. Applicable for array elements.
     */
    fun child(index: Int): JsonPointer {
        require(index >= 0) { "index must be greater than or equal to '0'" }
        return child(index.toString())
    }

    override fun toString(): String =
        tokens.fold("") { accumulator, current -> "$accumulator/${current.encode()}" }

    override fun equals(other: Any?): Boolean =
        when {
            this === other -> true
            other is JsonPointer -> tokens == other.tokens
            else -> false
        }

    override fun hashCode(): Int = tokens.hashCode()

    companion object {

        /**
         * The root element.
         */
        val Root: JsonPointer = JsonPointer(emptyList())

        /**
         * Parses a string as a pointer.
         * @return the JsonPointer or null if the provided [pointer] is not
         * a valid representation
         */
        fun parse(pointer: String): JsonPointer? = runCatching {
            when {
                pointer.isEmpty() -> Root
                !pointer.startsWith("/") -> null
                else -> {
                    val tokens = pointer.split("/")
                        .drop(1)
                        .map { it.decode() }
                    JsonPointer(tokens)
                }
            }
        }.getOrNull()

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
