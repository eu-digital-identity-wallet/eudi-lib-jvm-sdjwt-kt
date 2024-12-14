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

import kotlinx.serialization.json.*

/**
 * A combination of a salt, a cleartext claim name, and a cleartext claim value,
 * all of which are used to calculate a digest for the respective claim.
 */
sealed interface Disclosure {

    val value: String

    /**
     * Decodes and extracts the disclosed claim
     *
     * @return the disclosed claim
     */
    fun claim(): Claim {
        val (_, name, value) = decode(value).getOrThrow()
        return (name ?: SdJwtSpec.CLAIM_ARRAY_ELEMENT_DIGEST) to value
    }

    @JvmInline
    value class ArrayElement internal constructor(override val value: String) : Disclosure

    @JvmInline
    value class ObjectProperty internal constructor(override val value: String) : Disclosure

    companion object {

        /**
         * Directly wraps a string representing into a [Disclosure]
         * Validates the given string is a base64-url encoded json array that contains
         * a json string (salt)
         * a json string (claim name)
         * a json element (claim value)
         * for [ObjectProperty]
         *
         * or
         *
         * a json string (salt)
         * a json element (claim value)
         * for [ArrayElement]
         */
        internal fun wrap(s: String): Result<Disclosure> =
            decode(s).map { (_, name, _) ->
                if (name != null) ObjectProperty(s)
                else ArrayElement(s)
            }

        internal fun decode(disclosure: String): Result<Triple<Salt, String?, JsonElement>> = runCatching {
            val base64Decoded = Base64UrlNoPadding.decode(disclosure).decodeToString()
            val array = Json.decodeFromString<JsonArray>(base64Decoded)
            when (array.size) {
                3 -> {
                    val salt = array[0].jsonPrimitive.content
                    val claimName = array[1].jsonPrimitive.content
                    val claimValue = array[2]
                    Triple(salt, claimName, claimValue)
                }

                2 -> {
                    val salt = array[0].jsonPrimitive.content
                    val claimValue = array[1]
                    Triple(salt, null, claimValue)
                }

                else -> throw IllegalArgumentException("Was expecting an json array of 3 or 2 elements")
            }
        }

        internal fun arrayElement(
            saltProvider: SaltProvider = SaltProvider.Default,
            element: JsonElement,
        ): Result<ArrayElement> = runCatching {
            // Create a Json Array [salt, claimName, claimValue]
            val jsonArray = buildJsonArray {
                add(JsonPrimitive(saltProvider.salt())) // salt
                add(element) // claim value
            }
            val jsonArrayStr = jsonArray.toString()
            // Base64-url-encoded
            val encoded = Base64UrlNoPadding.encode(jsonArrayStr.encodeToByteArray())
            ArrayElement(encoded)
        }

        /**
         * Encodes a [Claim] into [Disclosure.ObjectProperty] using the provided [saltProvider]
         * according to SD-JWT specification
         *
         * @param saltProvider the [SaltProvider] to be used. Defaults to [SaltProvider.Default]
         * @param claim the claim to be disclosed
         */
        internal fun objectProperty(
            saltProvider: SaltProvider = SaltProvider.Default,
            claim: Claim,
        ): Result<ObjectProperty> {
            fun Claim.ensureValidAttributeName() {
                val reserved = setOf(SdJwtSpec.CLAIM_SD_ALG, SdJwtSpec.CLAIM_SD, SdJwtSpec.CLAIM_ARRAY_ELEMENT_DIGEST)
                require(name() !in reserved) {
                    "Given claim should not contain an attribute named ${reserved.joinToString(separator = ", or")}"
                }
            }

            return runCatching {
                // Make sure that claim name is valid
                claim.ensureValidAttributeName()

                // Create a Json Array [salt, claimName, claimValue]
                val jsonArray = buildJsonArray {
                    add(JsonPrimitive(saltProvider.salt())) // salt
                    add(claim.name()) // claim name
                    add(claim.value()) // claim value
                }
                val jsonArrayStr = jsonArray.toString()
                // Base64-url-encoded
                val encoded = Base64UrlNoPadding.encode(jsonArrayStr.encodeToByteArray())
                ObjectProperty(encoded)
            }
        }
    }
}
