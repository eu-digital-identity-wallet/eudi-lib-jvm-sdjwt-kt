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

import java.security.MessageDigest

/**
 * The digest of a [presentation][SdJwt.Presentation].
 */
class SdJwtDigest private constructor(private val value: ByteArray) {

    /**
     * Gets the base64-url encoded value of this [SdJwtDigest].
     *
     * @return the base64-url encoded value of this digest
     */
    fun serialize(): String = JwtBase64.encode(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SdJwtDigest

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
         * Wraps the given [value] to a [SdJwtDigest].
         * The [value] is expected to be base64-url encoded.
         *
         * @param value the base64-url encoded distest value to wrap
         * @return the wrapped value
         */
        fun wrap(value: String): Result<SdJwtDigest> = runCatching {
            SdJwtDigest(JwtBase64.decode(value))
        }

        /**
         * Calculates the [integrity][SdJwtDigest] of a [presentation][sdJwt] using the provided
         * [hashing algorithm][hashAlgorithm].
         *
         * @param hashAlgorithm the [HashAlgorithm] to use for the calculation of the digest
         * @param sdJwt the [SdJwt.Presentation] for which to calculate the digest
         * @param serializeJwt serialization function for [JWT]
         * @param JWT the type of the JWT the [SdJwt.Presentation] contains
         * @return the calculated digest
         */
        fun <JWT> digest(
            hashAlgorithm: HashAlgorithm,
            sdJwt: SdJwt.Presentation<JWT, *>,
            serializeJwt: (JWT) -> String,
        ): Result<SdJwtDigest> =
            digestSerialized(hashAlgorithm, sdJwt.noKeyBinding().serialize(serializeJwt))

        /**
         * Calculates the [integrity][SdJwtDigest] of an already serialized [presentation][sdJwt] using the provided
         * [hashing algorithm][hashAlgorithm].
         *
         * @param hashAlgorithm the [HashAlgorithm] to use for the calculation of the digest
         * @return the calculated digest
         */
        fun digestSerialized(hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtDigest> = runCatching {
            require(value.contains("~"))
            fun String.noKeyBinding() =
                if (endsWith("~")) {
                    this
                } else {
                    removeRange(lastIndexOf("~") + 1, length)
                }

            val digestAlgorithm = MessageDigest.getInstance(hashAlgorithm.alias.uppercase())
            val digest = digestAlgorithm.digest(value.noKeyBinding().encodeToByteArray())
            SdJwtDigest(digest)
        }
    }
}
