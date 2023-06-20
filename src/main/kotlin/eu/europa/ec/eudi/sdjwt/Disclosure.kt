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
 *
 * @param value a string as defined in [SD-JWT][https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt-04#section-5.1.1.1]
 */
@JvmInline
value class Disclosure private constructor(val value: String) {

    /**
     * Decodes and extracts the disclosed claim
     * @return the disclosed claim
     */
    fun claim(): Claim =
        decode(value).getOrThrow().second

    companion object {

        /**
         * Directly wraps a string representing into a [Disclosure]
         * Validates the given string is a base64-url encoded json array that contains
         * a json string (salt)
         * a json string (claim name)
         * a json element (claim value)
         */
        internal fun wrap(s: String): Result<Disclosure> = decode(s).map { Disclosure(s) }

        /**
         * Encodes a [Claim] into [Disclosure] using the provided [saltProvider]
         * according to [SD-JWT spec][https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt-04#section-5.1.1.1]
         *
         * @param saltProvider the [SaltProvider] to be used. Defaults to [SaltProvider.Default]
         * @param claim the claim to be disclosed
         */
        fun encode(
            saltProvider: SaltProvider = SaltProvider.Default,
            claim: Claim,
        ): Result<Disclosure> {
            // Make sure that claim name is not _sd
            fun isValidAttributeName(attribute: String): Boolean = attribute != "_sd"

            // Make sure that claim value doesn't contain an attribute named _sd
            // is not Json null
            fun isValidJsonElement(json: JsonElement): Boolean =
                when (json) {
                    is JsonPrimitive -> json !is JsonNull
                    is JsonArray -> json.all { isValidJsonElement(it) }
                    is JsonObject -> json.entries.all { isValidAttributeName(it.key) && isValidJsonElement(it.value) }
                }
            return runCatching {
                if (!isValidAttributeName(claim.name())) {
                    throw IllegalArgumentException("Given claim should not contain an attribute named _sd")
                }

                if (!isValidJsonElement(claim.value())) {
                    throw IllegalArgumentException("Claim should not contain a null value or an JSON object with attribute named _sd")
                }
                // Create a Json Array [salt, claimName, claimValue]
                val jsonArray = buildJsonArray {
                    add(JsonPrimitive(saltProvider.salt())) // salt
                    add(claim.name()) // claim name
                    add(claim.value()) // claim value
                }
                val jsonArrayStr = jsonArray.toString()
                // Base64-url-encoded
                val encoded = JwtBase64.encodeString(jsonArrayStr)
                Disclosure(encoded)
            }
        }

        /**
         * Decodes the given [string][disclosure]  into a pair of [salt][Salt] and [claim][Claim]
         *
         * @param disclosure the disclosure value
         * @return the [Salt] and the [Claim] of the disclosure, if the provided input is a disclosure.
         */
        fun decode(disclosure: String): Result<Pair<Salt, Claim>> = runCatching {
            val base64Decoded = JwtBase64.decodeString(disclosure)
            val array = Json.decodeFromString<JsonArray>(base64Decoded)
            if (array.size != 3) {
                throw IllegalArgumentException("Was expecting an json array of 3 elements")
            }
            val salt = array[0].jsonPrimitive.content
            val claimName = array[1].jsonPrimitive.content
            val claimValue = array[2]
            salt to (claimName to claimValue)
        }

        /**
         * Concatenates the given disclosures into a single string, separated by
         * "~". The string also starts with "~".
         */
        fun concat(ds: Iterable<Disclosure>): String =
            ds.fold("") { acc, disclosure -> "$acc~${disclosure.value}" }
    }
}

fun Iterable<Disclosure>.concat(): String = Disclosure.concat(this)
