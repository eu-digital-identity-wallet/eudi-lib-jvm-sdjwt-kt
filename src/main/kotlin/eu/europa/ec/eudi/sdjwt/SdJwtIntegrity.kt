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

import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * The integrity of a [presentation][SdJwt.Presentation].
 */
class SdJwtIntegrity private constructor(private val value: ByteArray) {

    /**
     * Returns the base64-url encoded value of this [SdJwtIntegrity].
     */
    fun serialize(): String = JwtBase64.encode(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SdJwtIntegrity

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return "SdJwtIntegrity(value=${serialize()})"
    }

    companion object {

        /**
         * Name of the Claim where [SdJwtIntegrity] is, in a KeyBinding JWT.
         */
        const val CLAIM_NAME = "_sd_hash"

        /**
         * Wraps the given [value] to a [SdJwtIntegrity].
         * The [value] is expected to be base64-url encoded.
         */
        fun wrap(value: String): Result<SdJwtIntegrity> = runCatching {
            SdJwtIntegrity(JwtBase64.decode(value))
        }

        /**
         * Calculates the [integrity][SdJwtIntegrity] of a [presentation][sdJwt] using the provided
         * [hashing algorithm][hashAlgorithm].
         */
        fun <JWT> digest(
            hashAlgorithm: HashAlgorithm,
            sdJwt: SdJwt.Presentation<JWT, *>,
            serializeJwt: (JWT) -> String,
        ): Result<SdJwtIntegrity> =
            digestSerialized(hashAlgorithm, sdJwt.noKeyBinding().serialize(serializeJwt))

        /**
         * Calculates the [integrity][SdJwtIntegrity] of an already serialized [presentation][sdJwt] using the provided
         * [hashing algorithm][hashAlgorithm].
         */
        fun digestSerialized(hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtIntegrity> = runCatching {
            require(value.contains("~"))
            fun String.noKeyBinding() =
                if (endsWith("~")) {
                    this
                } else {
                    removeRange(lastIndexOf("~") + 1, length)
                }

            val digestAlgorithm = MessageDigest.getInstance(hashAlgorithm.alias.uppercase())
            val digest = digestAlgorithm.digest(value.noKeyBinding().encodeToByteArray())
            SdJwtIntegrity(digest)
        }

        /**
         * Extracts the [SdJwtIntegrity Claim][CLAIM_NAME] from the provided set of [claims] and [wraps][wrap] it in
         * an [SdJwtIntegrity].
         */
        fun extract(claims: Claims): Result<SdJwtIntegrity> = runCatching {
            val claim = claims[CLAIM_NAME]!!
            wrap(claim.jsonPrimitive.content).getOrThrow()
        }
    }
}
