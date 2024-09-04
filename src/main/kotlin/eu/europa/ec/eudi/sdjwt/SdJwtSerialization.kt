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
    val serializedDisclosures = disclosures.concat()
    return "$serializedJwt$serializedDisclosures~"
}

/**
 * Concatenates the given disclosures into a single string, separated by
 * `~`. The string also starts with "~".
 *
 * @receiver the disclosures to concatenate
 * @return the string as described above
 */
private fun List<Disclosure>.concat(): String = concat(Disclosure::value)

internal fun <T> List<T>.concat(get: (T) -> String): String =
    joinToString(separator = "~", prefix = "~", transform = get)

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
    return option.jwsJsonObject(protected, payload, signature, disclosures.map { it.value }.toSet(), kbJwt)
}

internal fun JwsSerializationOption.jwsJsonObject(
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
        when (this@jwsJsonObject) {
            JwsSerializationOption.General ->
                putJsonArray(JWS_JSON_SIGNATURES) {
                    add(buildJsonObject { putHeadersAndSignature() })
                }

            JwsSerializationOption.Flattened -> putHeadersAndSignature()
        }
    }
}

private const val JWS_JSON_HEADER = "header"
private const val JWS_JSON_DISCLOSURES = "disclosures"
private const val JWS_JSON_KB_JWT = "kb_jwt"
private const val JWS_JSON_PROTECTED = "protected"
private const val JWS_JSON_SIGNATURE = "signature"
private const val JWS_JSON_SIGNATURES = "signatures"
private const val JWS_JSON_PAYLOAD = "payload"
