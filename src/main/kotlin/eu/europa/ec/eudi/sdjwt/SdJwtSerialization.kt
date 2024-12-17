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
import eu.europa.ec.eudi.sdjwt.VerificationError.ParsingError
import kotlinx.serialization.json.*

fun interface BuildKbJwt {

    suspend operator fun invoke(sdJwtDigest: SdJwtDigest): Result<Jwt>
}

/**
 * @param JWT the type representing the JWT part of the SD-JWT
 */
@Suppress("unused")
interface SdJwtSerializationOps<JWT> {

    /**
     * Serializes a [SdJwt] without a key binding JWT.
     *
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT
     */
    fun SdJwt<JWT>.serialize(): String

    /**
     * Serializes a [SdJwt] with a Key Binding JWT.
     *
     * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
     * in the generated Key Binding JWT
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT including the generated Key Binding JWT
     */
    fun SdJwt<JWT>.serializeWithKeyBinding(kbJwt: Jwt): String {
        val presentationSdJwt = serialize()
        return "$presentationSdJwt$kbJwt}"
    }

    /**
     * Serializes a [SdJwt] with a Key Binding JWT.
     *
     * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
     * in the generated Key Binding JWT
     * @param buildKbJwt a way to construct the Key binding JWT
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT including the generated Key Binding JWT
     */
    suspend fun SdJwt<JWT>.serializeWithKeyBinding(buildKbJwt: BuildKbJwt): Result<String>

    /**
     * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
     * In addition to the General & Flattened representations defined in the RFC7515,
     * the result JSON contains an unprotected header which includes
     * an array with the disclosures
     *
     * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
     *   representation as defined in RFC7515
     * @receiver the [SdJwt] to serialize
     *
     * @return a JSON object either general or flattened according to RFC7515 having an additional
     * disclosures array
     */
    fun SdJwt<JWT>.asJwsJsonObject(option: JwsSerializationOption = JwsSerializationOption.Flattened): JsonObject

    fun SdJwt<JWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption = JwsSerializationOption.Flattened,
        kbJwt: Jwt,
    ): JsonObject

    /**
     * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
     * In addition to the General & Flattened representations defined in the RFC7515,
     * the result JSON contains an unprotected header which includes
     * an array with the disclosures of the [SdJwt] and the key binding JWT
     *
     * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
     *   representation as defined in RFC7515
     * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
     * in the generated Key Binding JWT
     * @param buildKbJwt a way to construct the Key binding JWT
     * @receiver the [SdJwt] to serialize
     *
     * @return a JSON object either general or flattened according to RFC7515 having an additional
     * disclosures array the key binding JWT
     */
    suspend fun SdJwt<JWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption = JwsSerializationOption.Flattened,
        buildKbJwt: BuildKbJwt,
    ): Result<JsonObject>

    companion object {
        /**
         * Factory method
         *
         * @param serializeJwt a function to serialize the [JWT]
         */
        operator fun <JWT> invoke(
            serializeJwt: (JWT) -> String,
            hashAlgorithm: (JWT) -> HashAlgorithm?,
        ): SdJwtSerializationOps<JWT> =
            defaultSdJwtSerializationOps(serializeJwt, hashAlgorithm)
    }
}

private fun <JWT> defaultSdJwtSerializationOps(
    serializeJwt: (JWT) -> String,
    hashAlgorithm: (JWT) -> HashAlgorithm?,
): SdJwtSerializationOps<JWT> = object : SdJwtSerializationOps<JWT> {

    override fun SdJwt<JWT>.serialize(): String {
        val serializedJwt = serializeJwt(jwt)
        return StandardSerialization.concat(serializedJwt, disclosures.map { it.value })
    }

    override suspend fun SdJwt<JWT>.serializeWithKeyBinding(buildKbJwt: BuildKbJwt): Result<String> =
        runCatching {
            val presentationSdJwt = serialize()
            val hashAlgorithm = jwt.hashAlgorithmOrDefault()
            val kbJwt = kbJwt(presentationSdJwt, hashAlgorithm, buildKbJwt).getOrThrow()
            "$presentationSdJwt$kbJwt"
        }

    override fun SdJwt<JWT>.asJwsJsonObject(option: JwsSerializationOption): JsonObject =
        toJwsJsonObject(option, kbJwt = null)

    override suspend fun SdJwt<JWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        buildKbJwt: BuildKbJwt,
    ): Result<JsonObject> = runCatching {
        val presentationSdJwt = serialize()
        val hashAlgorithm = jwt.hashAlgorithmOrDefault()
        val kbJwt = kbJwt(presentationSdJwt, hashAlgorithm, buildKbJwt).getOrThrow()
        asJwsJsonObjectWithKeyBinding(option, kbJwt)
    }

    override fun SdJwt<JWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        kbJwt: Jwt,
    ): JsonObject = toJwsJsonObject(option, kbJwt)

    private fun SdJwt<JWT>.toJwsJsonObject(
        option: JwsSerializationOption,
        kbJwt: Jwt?,
    ): JsonObject {
        val (protected, payload, signature) = run {
            val serializedSdJWt = serializeJwt(this@toJwsJsonObject.jwt)
            val parts = serializedSdJWt.split(".")
            check(parts.size == 3)
            parts
        }
        return with(JwsJsonSupport) {
            val ds = this@toJwsJsonObject.disclosures.map<Disclosure, String> { it.value }.toSet<String>()
            option.buildJwsJson(protected, payload, signature, ds, kbJwt)
        }
    }

    private fun JWT.hashAlgorithmOrDefault(): HashAlgorithm {
        return hashAlgorithm(this) ?: HashAlgorithm.SHA_256
    }
}

private suspend fun kbJwt(
    presentationSdJwt: String,
    hashAlgorithm: HashAlgorithm,
    buildKbJwt: BuildKbJwt,
): Result<Jwt> = runCatching {
    val sdJwtDigest = SdJwtDigest.digest(hashAlgorithm, presentationSdJwt).getOrThrow()
    buildKbJwt(sdJwtDigest).getOrThrow()
}

enum class JwsSerializationOption {
    General, Flattened
}

internal fun jwtClaims(jwt: Jwt): Result<Triple<JsonObject, JsonObject, String>> = runCatching {
    fun json(s: String): JsonObject {
        val decoded = JwtBase64.decode(s).toString(Charsets.UTF_8)
        return Json.parseToJsonElement(decoded).jsonObject
    }
    val (h, p, s) = splitJwt(jwt).getOrThrow()
    Triple(json(h), json(p), s)
}

private fun splitJwt(jwt: Jwt): Result<Triple<String, String, String>> = runCatching {
    val ps = jwt.split(".")
    if (ps.size != 3) throw VerificationError.InvalidJwt.asException()
    val (h, p, s) = jwt.split(".")
    Triple(h, p, s)
}

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
