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

fun interface KbJwtBuilder {
    suspend fun kbJwt(hashAlgorithm: HashAlgorithm, sdJwt: String, kbJwtClaims: JsonObject): Result<Jwt> =
        runCatching {
            val sdJwtDigest = SdJwtDigest.digest(hashAlgorithm, sdJwt).getOrThrow()
            kbJwt(sdJwtDigest, kbJwtClaims).getOrThrow()
        }

    suspend fun kbJwt(sdJwtDigest: SdJwtDigest, kbJwtClaims: JsonObject): Result<Jwt>
}

/**
 * @param JWT the type representing the JWT part of the SD-JWT
 */
interface SdJwtSerializationOps<JWT> {

    /**
     * Serializes a [SdJwt] without a key binding part.
     *
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT
     */
    fun SdJwt<JWT>.serialize(): String

    /**
     * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
     * In addition to the General & Flattened representations defined in the RFC7515,
     *  the result JSON contains an unprotected header which includes
     *  an array with the disclosures of the [SdJwt] and optionally the key binding JWT
     *
     * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
     *   representation as defined in RFC7515
     * @param kbJwt the key binding JWT for the SD-JWT.
     * @receiver the [SdJwt] to serialize
     *
     * @return a JSON object either general or flattened according to RFC7515 having an additional
     * disclosures array and possibly the KB-JWT in an unprotected header as per SD-JWT extension
     */
    fun SdJwt<JWT>.asJwsJsonObject(
        option: JwsSerializationOption = JwsSerializationOption.Flattened,
        kbJwt: Jwt?,
    ): JsonObject

    /**
     * Serializes a [SdJwt.Presentation] with a Key Binding JWT.
     *
     * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
     * in the generated Key Binding JWT
     * of the generated Key Binding JWT.
     * @param JWT the type representing the JWT part of the SD-JWT
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT including the generated Key Binding JWT
     */
    suspend fun SdJwt.Presentation<JWT>.serializeWithKeyBinding(
        hashAlgorithm: HashAlgorithm,
        kbJwtBuilder: KbJwtBuilder,
        cs: JsonObject,
    ): Result<String> = runCatching {
        // Serialize the presentation SD-JWT with no Key binding
        val presentationSdJwt = serialize()
        val kbJwt = kbJwtBuilder.kbJwt(hashAlgorithm, presentationSdJwt, cs).getOrThrow()
        // concatenate the two parts together
        "$presentationSdJwt$kbJwt"
    }

    suspend fun SdJwt.Presentation<JWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption = JwsSerializationOption.Flattened,
        hashAlgorithm: HashAlgorithm,
        kbJwtBuilder: KbJwtBuilder,
        kbJwtClaims: JsonObject,
    ): Result<JsonObject> = runCatching {
        // Serialize the presentation SD-JWT with no Key binding
        val presentationSdJwt = serialize()
        val kbJwt = kbJwtBuilder.kbJwt(hashAlgorithm, presentationSdJwt, kbJwtClaims).getOrThrow()
        asJwsJsonObject(option, kbJwt)
    }

    companion object {
        /**
         * @param serializeJwt a function to serialize the [JWT]
         */
        operator fun <JWT> invoke(
            serializeJwt: (JWT) -> String,
        ): SdJwtSerializationOps<JWT> = object : SdJwtSerializationOps<JWT> {

            override fun SdJwt<JWT>.serialize(): String {
                val serializedJwt = serializeJwt(jwt)
                return StandardSerialization.concat(serializedJwt, disclosures.map { it.value })
            }

            override fun SdJwt<JWT>.asJwsJsonObject(
                option: JwsSerializationOption,
                kbJwt: Jwt?,
            ): JsonObject {
                if (kbJwt != null) {
                    require(this is SdJwt.Presentation<JWT>) {
                        "Key binding JWT requires a presentation"
                    }
                }

                val (protected, payload, signature) = run {
                    val serializedSdJWt = serializeJwt(jwt)
                    val parts = serializedSdJWt.split(".")
                    check(parts.size == 3)
                    parts
                }
                return with(JwsJsonSupport) {
                    val ds = disclosures.map<Disclosure, String> { it.value }.toSet<String>()
                    option.buildJwsJson(protected, payload, signature, ds, kbJwt)
                }
            }
        }
    }
}

/**
 * Serializes an [SdJwt] in combined format without key binding
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to serialize
 * @return the serialized format of the SD-JWT
 */
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith("with(SdJwtSerializationOps<JWT>(serializeJwt)) { serialize() }"),
)
fun <JWT> SdJwt<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String = with(SdJwtSerializationOps<JWT>(serializeJwt)) { serialize() }

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
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith(
        "with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(\".\")})) { asJwsJsonObject(option, kbJwt) }",
    ),
)
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    kbJwt: Jwt?,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject =
    with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(".") })) { asJwsJsonObject(option, kbJwt) }

internal object StandardSerialization {

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
        val parts = unverifiedSdJwt.split(SdJwtSpec.DISCLOSURE_SEPARATOR)
        if (parts.size <= 1) throw ParsingError.asException()
        val jwt = parts[0]
        val containsKeyBinding = !unverifiedSdJwt.endsWith(SdJwtSpec.DISCLOSURE_SEPARATOR)
        val ds = parts
            .drop(1)
            .run { if (containsKeyBinding) dropLast(1) else this }
            .filter { it.isNotBlank() }
        val kbJwt = if (containsKeyBinding) parts.last() else null
        return Triple(jwt, ds, kbJwt)
    }

    private fun <T> Iterable<T>.concat(get: (T) -> String): String =
        joinToString(
            prefix = "${SdJwtSpec.DISCLOSURE_SEPARATOR}",
            separator = "",
        ) { "${get(it)}${SdJwtSpec.DISCLOSURE_SEPARATOR}" }
}

internal object JwsJsonSupport {

    fun JwsSerializationOption.buildJwsJson(
        protected: String,
        payload: String,
        signature: String,
        disclosures: Set<String>,
        kbJwt: Jwt?,
    ): JsonObject {
        fun JsonObjectBuilder.putHeadersAndSignature() {
            putJsonObject(RFC7515.JWS_JSON_HEADER) {
                put(SdJwtSpec.JWS_JSON_DISCLOSURES, JsonArray(disclosures.map { JsonPrimitive(it) }))
                if (kbJwt != null) {
                    put(SdJwtSpec.JWS_JSON_KB_JWT, kbJwt)
                }
            }
            put(RFC7515.JWS_JSON_PROTECTED, protected)
            put(RFC7515.JWS_JSON_SIGNATURE, signature)
        }

        return buildJsonObject {
            put(RFC7515.JWS_JSON_PAYLOAD, payload)
            when (this@buildJwsJson) {
                JwsSerializationOption.General ->
                    putJsonArray(RFC7515.JWS_JSON_SIGNATURES) {
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
    fun parseJWSJson(unverifiedSdJwt: JsonObject): Triple<Jwt, List<String>, Jwt?> {
        fun JsonElement.stringContentOrNull() = if (this is JsonPrimitive && isString) contentOrNull else null

        // selects the JsonObject that contains the pair of "protected" & "signature" claims
        // According to RFC7515 General format this could be in "signatures" json array or
        // in flatten format this could be the given root element itself
        val signatureContainer = unverifiedSdJwt[RFC7515.JWS_JSON_SIGNATURES]
            ?.takeIf { it is JsonArray }
            ?.jsonArray
            ?.firstOrNull()
            ?.takeIf { it is JsonObject }
            ?.jsonObject
            ?: unverifiedSdJwt

        val unverifiedJwt = run {
            val protected = signatureContainer[RFC7515.JWS_JSON_PROTECTED]?.stringContentOrNull()
            val signature = signatureContainer[RFC7515.JWS_JSON_SIGNATURE]?.stringContentOrNull()
            val payload = unverifiedSdJwt[RFC7515.JWS_JSON_PAYLOAD]?.stringContentOrNull()
            requireNotNull(payload) { "Given JSON doesn't comply with RFC7515. Misses payload" }
            requireNotNull(protected) { "Given JSON doesn't comply with RFC7515. Misses protected" }
            requireNotNull(signature) { "Given JSON doesn't comply with RFC7515. Misses signature" }
            "$protected.$payload.$signature"
        }

        val unprotectedHeader = signatureContainer[RFC7515.JWS_JSON_HEADER]
            ?.takeIf { element -> element is JsonObject }
            ?.jsonObject

        // SD-JWT specification extends RFC7515 with a "disclosures" top-level JSON array
        val unverifiedDisclosures = unprotectedHeader?.get(SdJwtSpec.JWS_JSON_DISCLOSURES)
            ?.takeIf { element -> element is JsonArray }
            ?.jsonArray
            ?.takeIf { array -> array.all { element -> element is JsonPrimitive && element.isString } }
            ?.mapNotNull { element -> element.stringContentOrNull() }
            ?: emptyList()

        val unverifiedKBJwt = unprotectedHeader?.get("kb_jwt")?.stringContentOrNull()
        return Triple(unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt)
    }
}
