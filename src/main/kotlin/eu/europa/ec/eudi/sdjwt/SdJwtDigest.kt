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

/**
 * The digest of a [presentation][SdJwt].
 * It contains the base64-url encoded digest of a presentation with all padding characters removed.
 */
@JvmInline
value class SdJwtDigest private constructor(val value: String) {

    companion object {

        /**
         * Wraps the given [value] to a [SdJwtDigest].
         * The [value] is expected to be base64-url encoded.
         *
         * @param value the base64-url encoded without padding digest value to wrap
         * @return the wrapped value
         */
        fun wrap(value: String): Result<SdJwtDigest> = runCatching {
            Base64UrlNoPadding.decode(value)
            SdJwtDigest(value)
        }

        /**
         * Calculates the [integrity][SdJwtDigest] of serialized [presentation][sdJwt].
         *
         * @param hashAlgorithm the [HashAlgorithm] to be used for the calculation of the digest
         * @param value the serialized SD-JWT to calculate the digest for
         * @return the calculated digest
         */
        fun digest(hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtDigest> =
            digestInternal(platform().hashes, hashAlgorithm, value)

        /**
         * Internal version of digest that takes a Platform parameter
         */
        internal fun digestInternal(hashes: Hashes, hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtDigest> = runCatching {
            require(value.contains(SdJwtSpec.DISCLOSURE_SEPARATOR))
            fun String.noKeyBinding() =
                if (endsWith(SdJwtSpec.DISCLOSURE_SEPARATOR)) {
                    this
                } else {
                    removeRange(lastIndexOf(SdJwtSpec.DISCLOSURE_SEPARATOR) + 1, length)
                }

            val input = value.noKeyBinding().encodeToByteArray()
            val digest = hashes.digest(hashAlgorithm, input)
            SdJwtDigest(Base64UrlNoPadding.encode(digest))
        }
    }
}
