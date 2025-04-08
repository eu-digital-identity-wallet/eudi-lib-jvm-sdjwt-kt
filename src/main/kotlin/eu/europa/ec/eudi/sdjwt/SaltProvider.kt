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
 * An interface for generating [Salt] values.
 */
fun interface SaltProvider {

    /**
     * Provides a new [Salt]
     * @return a new [Salt] value
     */
    fun salt(): Salt

    companion object {

        /**
         * A default implementation of [SaltProvider].
         * It produces random [Salt] values of size 16 bytes (128 bits)
         */
        val Default: SaltProvider by lazy { randomSaltProvider(16) }

        private val secureRandom: Random = platform().random

        /**
         * Creates a salt provider which generates random [Salt] values
         *
         * @param numberOfBytes the size of salt in bytes
         *
         * @return a salt provider which generates random [Salt] values
         */
        fun randomSaltProvider(numberOfBytes: Int): SaltProvider =
            SaltProvider {
                val randomByteArray: ByteArray = ByteArray(numberOfBytes).also { secureRandom.nextBytesCopyTo(it) }
                Base64UrlNoPadding.encode(randomByteArray)
            }
    }
}
