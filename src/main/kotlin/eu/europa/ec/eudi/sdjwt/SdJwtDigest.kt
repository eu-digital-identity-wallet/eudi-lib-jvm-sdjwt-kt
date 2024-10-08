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
 * It contains the base64-url encoded digest of a presentation with all padding characters removed.
 */
@JvmInline
value class SdJwtDigest private constructor(val value: String) {

    companion object {

        /**
         * The name of the claim, under which the SD-JWT
         * digest is expected to be found in a Key Binding JWT
         */
        const val CLAIM_NAME = "sd_hash"

        /**
         * Wraps the given [value] to a [SdJwtDigest].
         * The [value] is expected to be base64-url encoded.
         *
         * @param value the base64-url encoded without padding digest value to wrap
         * @return the wrapped value
         */
        fun wrap(value: String): Result<SdJwtDigest> = runCatching {
            JwtBase64.decode(value)
            SdJwtDigest(value)
        }

        /**
         * Calculates the [integrity][SdJwtDigest] of serialized [presentation][sdJwt].
         *
         * @param hashAlgorithm the [HashAlgorithm] to be used for the calculation of the digest
         * @param value the serialized SD-JWT to calculate the digest for
         * @return the calculated digest
         */
        fun digest(hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtDigest> = runCatching {
            require(value.contains("~"))
            fun String.noKeyBinding() =
                if (endsWith("~")) {
                    this
                } else {
                    removeRange(lastIndexOf("~") + 1, length)
                }

            val digestAlgorithm = MessageDigest.getInstance(hashAlgorithm.alias.uppercase())
            val digest = digestAlgorithm.digest(value.noKeyBinding().encodeToByteArray())
            SdJwtDigest(JwtBase64.encode(digest))
        }
    }
}
