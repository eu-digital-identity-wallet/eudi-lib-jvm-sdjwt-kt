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
        return (name ?: SdJwtSpec.CLAIM_THREE_DOTS) to value
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
            val base64Decoded = JwtBase64.decode(disclosure).decodeToString()
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
            val encoded = JwtBase64.encode(jsonArrayStr.encodeToByteArray())
            ArrayElement(encoded)
        }

        /**
         * Encodes a [Claim] into [Disclosure.ObjectProperty] using the provided [saltProvider]
         * according to SD-JWT specification
         *
         * @param saltProvider the [SaltProvider] to be used. Defaults to [SaltProvider.Default]
         * @param claim the claim to be disclosed
         * @param allowNestedDigests whether to allow the presence of nested hash claim (_sd)
         */
        internal fun objectProperty(
            saltProvider: SaltProvider = SaltProvider.Default,
            claim: Claim,
            allowNestedDigests: Boolean = false,
        ): Result<ObjectProperty> {
            // Make sure that claim name is not _sd
            fun isValidAttributeName(attribute: String): Boolean = attribute != SdJwtSpec.CLAIM_SD

            // Make sure that claim value doesn't contain an attribute named _sd
            // is not Json null
            fun isValidJsonElement(json: JsonElement): Boolean =
                when (json) {
                    is JsonPrimitive -> json !is JsonNull
                    is JsonArray -> json.all { isValidJsonElement(it) }
                    is JsonObject -> json.entries.all {
                        (isValidAttributeName(it.key) || allowNestedDigests) && isValidJsonElement(it.value)
                    }
                }
            return runCatching {
                require(isValidAttributeName(claim.name())) {
                    "Given claim should not contain an attribute named ${SdJwtSpec.CLAIM_SD}"
                }

                require(isValidJsonElement(claim.value())) {
                    "Claim should not contain a null value or an JSON object with attribute named ${SdJwtSpec.CLAIM_SD}"
                }

                // Create a Json Array [salt, claimName, claimValue]
                val jsonArray = buildJsonArray {
                    add(JsonPrimitive(saltProvider.salt())) // salt
                    add(claim.name()) // claim name
                    add(claim.value()) // claim value
                }
                val jsonArrayStr = jsonArray.toString()
                // Base64-url-encoded
                val encoded = JwtBase64.encode(jsonArrayStr.encodeToByteArray())
                ObjectProperty(encoded)
            }
        }
    }
}
