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
internal fun Iterable<Disclosure>.concat(): String = concatDisclosureValues(this, Disclosure::value)
internal fun <T> concatDisclosureValues(vs: Iterable<T>, get: (T) -> String): String =
    vs.fold("") { acc, disclosure -> "$acc~${get(disclosure)}" }

/**
 * Option on how to embed an SD-JWT in an envelope
 * @param JWT the type representing the JWT part of the SD-JWT
 */
sealed interface EnvelopOption<JWT> {

    /**
     * Using the combined format
     *
     * This means that the envelope will include a claim named `_sd_jwt`
     * having as value the SD-JWT in combined format
     */
    data class Combined<JWT>(val serializeJwt: (JWT) -> String) : EnvelopOption<JWT>

    /**
     * Using the JWS Json serialization
     * @param jwsSerializationOption General or Flatten
     * @param getParts a function that gets the base 64 encoded parts: protected, payload, signature
     */
    data class JwsJson<JWT>(
        val jwsSerializationOption: JwsSerializationOption,
        val getParts: (JWT) -> Triple<String, String, String>,
    ) : EnvelopOption<JWT>
}

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes the following claims:
 * - `iat`
 * - `nonce`
 * - `aud`
 * - `_sd_jwt` (or `_js_sd_jwt`)
 *
 * @param issuedAt issuance time of the envelope JWT. It will be included as `iat` claim
 * @param audience the audience of the envelope JWT. It will be included as `aud` claim
 * @param nonce the nonce of the envelope JWT. It will be included as `nonce` claim
 * @param envelopOption option on how to include the SD-JWT in the envelope
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt<JWT>.toEnvelopedFormat(
    issuedAt: Instant,
    nonce: String,
    audience: String,
    envelopOption: EnvelopOption<JWT>,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val otherClaims = buildJsonObject {
        iat(issuedAt.epochSecond)
        aud(audience)
        put("nonce", nonce)
    }
    return toEnvelopedFormat(otherClaims, envelopOption, signEnvelop)
}

const val ENVELOPED_SD_JWT_IN_COMBINED_FROM = "_sd_jwt"
const val ENVELOPED_SD_JWT_IN_JWS_JSON = "_js_sd_jwt"

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes in addition to the [otherClaims]
 * the claim `_sd_jwt` or `_js_sd_jwt` depending on the [envelopOption]
 *
 * @param otherClaims claims to be included in the envelope JWT, except "_sd_jwt"
 * @param envelopOption option of how to nest the SD-JWT into the envelop
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped. If it contains [SdJwt.Presentation.keyBindingJwt]
 * it will be removed.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt<JWT>.toEnvelopedFormat(
    otherClaims: Claims,
    envelopOption: EnvelopOption<JWT>,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val envelopedClaims = otherClaims.toMutableMap()
    when (envelopOption) {
        is EnvelopOption.Combined<JWT> -> {
            val sdJwtInCombined = serialize(envelopOption.serializeJwt)
            envelopedClaims[ENVELOPED_SD_JWT_IN_COMBINED_FROM] = JsonPrimitive(sdJwtInCombined)
        }

        is EnvelopOption.JwsJson<JWT> -> {
            val sdJwtInJwsJson = asJwsJsonObject(envelopOption.jwsSerializationOption, envelopOption.getParts)
            envelopedClaims[ENVELOPED_SD_JWT_IN_JWS_JSON] = sdJwtInJwsJson
        }
    }

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
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
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
