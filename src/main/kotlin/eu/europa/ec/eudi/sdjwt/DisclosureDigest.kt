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
 * The digest of a [disclosure][Disclosure]
 */
@JvmInline
value class DisclosureDigest private constructor(val value: String) {
    companion object {

        /**
         * Wraps the given [string][s] into [DisclosureDigest]
         * It expects that the given input is base64-url encoded.
         * If not, an exception is thrown
         *
         * @param s the value to wrap
         * @return the [DisclosureDigest] if the given input represents a valid base64 encoded string
         */
        internal fun wrap(s: String): Result<DisclosureDigest> = runCatching {
            JwtBase64.decode(s)
            DisclosureDigest(s)
        }

        /**
         * Calculates the hash of the given [disclosure][d] using the specified [hashing algorithm][hashingAlgorithm]
         *
         * @param hashingAlgorithm the hashing algorithm to use
         * @param d the disclosure to hash
         *
         * @return the [DisclosureDigest] of the given [disclosure][d]
         */
        fun digest(hashingAlgorithm: HashAlgorithm, d: Disclosure): Result<DisclosureDigest> =
            digest(hashingAlgorithm, d.value)

        /**
         * Calculates the hash of the given [value] using the specified [hashing algorithm][hashingAlgorithm]
         *
         * @param hashingAlgorithm the hashing algorithm to use
         * @param value the value for which to calculate the hash
         *
         * @return the [DisclosureDigest] of the given [value]
         */
        fun digest(hashingAlgorithm: HashAlgorithm, value: String): Result<DisclosureDigest> = runCatching {
            val hashFunction = MessageDigest.getInstance(hashingAlgorithm.alias.uppercase())
            val digest = hashFunction.digest(value.encodeToByteArray())
            DisclosureDigest(JwtBase64.encode(digest))
        }
    }
}
