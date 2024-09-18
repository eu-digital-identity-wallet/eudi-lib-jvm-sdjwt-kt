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

import eu.europa.ec.eudi.sdjwt.KeyBindingError.UnexpectedKeyBindingJwt
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.Companion.asException
import eu.europa.ec.eudi.sdjwt.VerificationError.ParsingError
import kotlinx.serialization.json.*

/**
 * Serializes an [SdJwt] in combined format without key binding
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to serialize
 * @return the serialized format of the SD-JWT
 */
fun <JWT> SdJwt<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String {
    val serializedJwt = serializeJwt(jwt)
    return StandardSerialization.concat(serializedJwt, disclosures.map { it.value })
}

enum class JwsSerializationOption {
    General, Flattened
}

/**
 * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 *  the result JSON contains an unprotected header which includes
 *  an array with the disclosures of the [SdJwt] and optionally the key binding JWT
 *
 * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
 *   representation as defined in RFC7515
 * @param kbJwt the key binding JWT for the SD-JWT.
 * @param getParts a function to extract out of the [jwt][SdJwt.jwt]  of the SD-JWT
 * the three JWS parts: protected header, payload and signature.
 * Each part is base64 encoded
 * @receiver the [SdJwt] to serialize
 *
 * @return a JSON object either general or flattened according to RFC7515 having an additional
 * disclosures array and possibly the KB-JWT in an unprotected header as per SD-JWT extension
 */
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    kbJwt: Jwt?,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject {
    val (protected, payload, signature) = getParts(jwt)

    return with(JwsJsonSupport) {
        option.buildJwsJson(protected, payload, signature, disclosures.map { it.value }.toSet(), kbJwt)
    }
}

internal object StandardSerialization {

    private const val TILDE = '~'

    fun concat(
        serializedJwt: Jwt,
        disclosures: Iterable<String>,
    ): String {
        val serializedDisclosures = disclosures.concat { it }
        return "$serializedJwt$serializedDisclosures"
    }

    /**
     * Parses an SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the JWT and the list of disclosures. Raises a [ParsingError] or [UnexpectedKeyBindingJwt]
     * @throws SdJwtVerificationException with a [ParsingError] in case the given string cannot be parsed. It can raise also
     *  [UnexpectedKeyBindingJwt] in case the SD-JWT contains a key bind JWT part
     */
    fun parseIssuance(unverifiedSdJwt: String): Pair<Jwt, List<String>> {
        val (jwt, ds, kbJwt) = parse(unverifiedSdJwt)
        if (null != kbJwt) throw UnexpectedKeyBindingJwt.asException()
        return jwt to ds
    }

    /**
     * Parses an SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the JWT and the list of disclosures and the Key Binding JWT (as string), or raises [ParsingError]
     * @throws SdJwtVerificationException with a [ParsingError] in case the given string cannot be parsed
     */
    fun parse(unverifiedSdJwt: String): Triple<Jwt, List<String>, Jwt?> {
        val parts = unverifiedSdJwt.split(TILDE)
        if (parts.size <= 1) throw ParsingError.asException()
        val jwt = parts[0]
        val containsKeyBinding = !unverifiedSdJwt.endsWith(TILDE)
        val ds = parts
            .drop(1)
            .run { if (containsKeyBinding) dropLast(1) else this }
            .filter { it.isNotBlank() }
        val kbJwt = if (containsKeyBinding) parts.last() else null
        return Triple(jwt, ds, kbJwt)
    }

    private fun <T> Iterable<T>.concat(get: (T) -> String): String =
        joinToString(prefix = "$TILDE", separator = "") { "${get(it)}~" }
}

internal object JwsJsonSupport {
    private const val JWS_JSON_HEADER = "header"
    private const val JWS_JSON_DISCLOSURES = "disclosures"
    private const val JWS_JSON_KB_JWT = "kb_jwt"
    private const val JWS_JSON_PROTECTED = "protected"
    private const val JWS_JSON_SIGNATURE = "signature"
    private const val JWS_JSON_SIGNATURES = "signatures"
    private const val JWS_JSON_PAYLOAD = "payload"

    fun JwsSerializationOption.buildJwsJson(
        protected: String,
        payload: String,
        signature: String,
        disclosures: Set<String>,
        kbJwt: Jwt?,
    ): JsonObject {
        fun JsonObjectBuilder.putHeadersAndSignature() {
            putJsonObject(JWS_JSON_HEADER) {
                put(JWS_JSON_DISCLOSURES, JsonArray(disclosures.map { JsonPrimitive(it) }))
                if (kbJwt != null) {
                    put(JWS_JSON_KB_JWT, kbJwt)
                }
            }
            put(JWS_JSON_PROTECTED, protected)
            put(JWS_JSON_SIGNATURE, signature)
        }

        return buildJsonObject {
            put(JWS_JSON_PAYLOAD, payload)
            when (this@buildJwsJson) {
                JwsSerializationOption.General ->
                    putJsonArray(JWS_JSON_SIGNATURES) {
                        add(buildJsonObject { putHeadersAndSignature() })
                    }

                JwsSerializationOption.Flattened -> putHeadersAndSignature()
            }
        }
    }

    /**
     * Extracts from [unverifiedSdJwt] the JWT and the disclosures
     *
     * @param unverifiedSdJwt a JSON Object that is expected to be in general or flatten form as defined in RFC7515 and
     * extended by SD-JWT specification.
     * @return the jwt and the disclosures (unverified).
     * @throws IllegalArgumentException if the given JSON Object is not compliant
     */
    fun parseJWSJson(unverifiedSdJwt: Claims): Triple<Jwt, List<String>, Jwt?> {
        fun JsonElement.stringContentOrNull() = if (this is JsonPrimitive && isString) contentOrNull else null

        // selects the JsonObject that contains the pair of "protected" & "signature" claims
        // According to RFC7515 General format this could be in "signatures" json array or
        // in flatten format this could be the given root element itself
        val signatureContainer = unverifiedSdJwt[JWS_JSON_SIGNATURES]
            ?.takeIf { it is JsonArray }
            ?.jsonArray
            ?.firstOrNull()
            ?.takeIf { it is JsonObject }
            ?.jsonObject
            ?: unverifiedSdJwt

        val unverifiedJwt = run {
            val protected = signatureContainer[JWS_JSON_PROTECTED]?.stringContentOrNull()
            val signature = signatureContainer[JWS_JSON_SIGNATURE]?.stringContentOrNull()
            val payload = unverifiedSdJwt[JWS_JSON_PAYLOAD]?.stringContentOrNull()
            requireNotNull(payload) { "Given JSON doesn't comply with RFC7515. Misses payload" }
            requireNotNull(protected) { "Given JSON doesn't comply with RFC7515. Misses protected" }
            requireNotNull(signature) { "Given JSON doesn't comply with RFC7515. Misses signature" }
            "$protected.$payload.$signature"
        }

        val unprotectedHeader = signatureContainer[JWS_JSON_HEADER]
            ?.takeIf { element -> element is JsonObject }
            ?.jsonObject

        // SD-JWT specification extends RFC7515 with a "disclosures" top-level json array
        val unverifiedDisclosures = unprotectedHeader?.get(JWS_JSON_DISCLOSURES)
            ?.takeIf { element -> element is JsonArray }
            ?.jsonArray
            ?.takeIf { array -> array.all { element -> element is JsonPrimitive && element.isString } }
            ?.mapNotNull { element -> element.stringContentOrNull() }
            ?: emptyList()

        val unverifiedKBJwt = unprotectedHeader?.get("kb_jwt")?.stringContentOrNull()
        return Triple(unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt)
    }
}
