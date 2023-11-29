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
import java.time.Instant

/**
 * Serializes an [SdJwt.Issuance]
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to serialize
 * @return the serialized format of the SD-JWT
 */
fun <JWT> SdJwt.Issuance<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String {
    val serializedJwt = serializeJwt(jwt)
    val serializedDisclosures = disclosures.concat()
    return "$serializedJwt$serializedDisclosures~"
}

/**
 * Serializes an [SdJwt.Presentation]
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param serializeKeyBindingJwt a function to serialize the [KB_JWT]
 * @param KB_JWT the type representing the Key Binding part of the SD
 * @receiver the SD-JWT to serialize
 * @return the serialized format of the SD-JWT
 */
fun <JWT, KB_JWT> SdJwt.Presentation<JWT, KB_JWT>.serialize(
    serializeJwt: (JWT) -> String,
    serializeKeyBindingJwt: (KB_JWT) -> String,
): String {
    val serializedJwt = serializeJwt(jwt)
    val serializedDisclosures = disclosures.concat()
    val serializedKbJwt = keyBindingJwt?.run(serializeKeyBindingJwt) ?: ""
    return "$serializedJwt$serializedDisclosures~$serializedKbJwt"
}

fun <JWT> SdJwt.Presentation<JWT, Nothing>.serialize(
    serializeJwt: (JWT) -> String,
): String = this@serialize.serialize(serializeJwt) { it }

/**
 * Concatenates the given disclosures into a single string, separated by
 * `~`. The string also starts with "~".
 *
 * @receiver the disclosures to concatenate
 * @return the string as described above
 */
internal fun Iterable<Disclosure>.concat(): String = fold("") { acc, disclosure -> "$acc~${disclosure.value}" }

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes the following claims:
 * - `iat`
 * - `nonce`
 * - `aud`
 * - `_sd_jwt`
 *
 * @param issuedAt issuance time of the envelope JWT. It will be included as `iat` claim
 * @param audience the audience of the envelope JWT. It will be included as `aud` claim
 * @param nonce the nonce of the envelope JWT. It will be included as `nonce` claim
 * @param serializeJwt a way to serialize the JWT part of the [SdJwt.Presentation]. Will be used to
 * produce the Combined Presentation format.
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped. If it contains [SdJwt.Presentation.keyBindingJwt]
 * it will be removed.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt.Presentation<JWT, *>.toEnvelopedFormat(
    issuedAt: Instant,
    nonce: String,
    audience: String,
    serializeJwt: (JWT) -> String,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val otherClaims = buildJsonObject {
        iat(issuedAt.epochSecond)
        aud(audience)
        put("nonce", nonce)
    }
    return toEnvelopedFormat(otherClaims, serializeJwt, signEnvelop)
}

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes in addition to the [otherClaims]
 * the claim `_sd_jwt`
 *
 * @param otherClaims claims to be included in the envelope JWT, except "_sd_jwt"
 * @param serializeJwt a way to serialize the JWT part of the [SdJwt.Presentation]. Will be used to
 * produce the Combined Presentation format.
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped. If it contains [SdJwt.Presentation.keyBindingJwt]
 * it will be removed.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt.Presentation<JWT, *>.toEnvelopedFormat(
    otherClaims: Claims,
    serializeJwt: (JWT) -> String,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val sdJwtInCombined = noKeyBinding().serialize(serializeJwt)
    val envelopedClaims = otherClaims.toMutableMap()
    envelopedClaims["_sd_jwt"] = JsonPrimitive(sdJwtInCombined)
    return signEnvelop(envelopedClaims)
}

/**
 *
 */
enum class JwsSerializationOption {
    General, Flattened
}

/**
 * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 * the result JSON contains a JSON array with the disclosures of the [SdJwt]
 *
 * Please note that this serialization option cannot be used to convey the key binding JWT
 * of a [SdJwt.Presentation]
 *
 * @param getParts a function to extract out of the [jwt][SdJwt.jwt]  of the SD-JWT
 * the three JWS parts: protected header, payload and signature.
 * Each part is base64 encoded
 *
 * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
 * representation as defined in RFC7515
 * @receiver the [SdJwt] to serialize
 *
 * @return a JSON object either general or flattened according to RFC7515 having an additional
 * disclosures array as per SD-JWT extension
 */
fun <JWT> SdJwt<JWT, *>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject {
    val (protected, payload, signature) = getParts(jwt)
    return option.jwsJsonObject(protected, payload, signature, disclosures.map { it.value }.toSet())
}

internal fun JwsSerializationOption.jwsJsonObject(
    protected: String,
    payload: String,
    signature: String,
    disclosures: Set<String>,
): JsonObject {
    fun JsonObjectBuilder.putProtectedAndSignature() {
        put("protected", protected)
        put("signature", signature)
    }
    return buildJsonObject {
        put("payload", payload)
        when (this@jwsJsonObject) {
            JwsSerializationOption.General -> {
                val element = buildJsonObject { putProtectedAndSignature() }
                put("signatures", JsonArray(listOf(element)))
            }

            JwsSerializationOption.Flattened -> putProtectedAndSignature()
        }
        put("disclosures", JsonArray(disclosures.map { JsonPrimitive(it) }))
    }
}
