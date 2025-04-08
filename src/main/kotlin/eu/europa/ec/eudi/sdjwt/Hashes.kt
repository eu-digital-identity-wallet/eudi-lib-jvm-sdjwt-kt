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

internal interface Hashes {
    fun sha256(input: ByteArray): ByteArray
    fun sha384(input: ByteArray): ByteArray
    fun sha512(input: ByteArray): ByteArray

    @Suppress("ktlint:standard:function-naming")
    fun sha3_256(input: ByteArray): ByteArray

    @Suppress("ktlint:standard:function-naming")
    fun sha3_384(input: ByteArray): ByteArray

    @Suppress("ktlint:standard:function-naming")
    fun sha3_512(input: ByteArray): ByteArray
}

internal fun Hashes.digest(hashAlgorithm: HashAlgorithm, input: ByteArray): ByteArray =
    when (hashAlgorithm) {
        HashAlgorithm.SHA_256 -> sha256(input)
        HashAlgorithm.SHA_384 -> sha384(input)
        HashAlgorithm.SHA_512 -> sha512(input)
        HashAlgorithm.SHA3_256 -> sha3_256(input)
        HashAlgorithm.SHA3_384 -> sha3_384(input)
        HashAlgorithm.SHA3_512 -> sha3_512(input)
    }

internal object JvmAndAndroidHashes : Hashes {

    override fun sha256(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(input)

    override fun sha384(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-384").digest(input)

    override fun sha512(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-512").digest(input)

    override fun sha3_256(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA3-256").digest(input)

    override fun sha3_384(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA3-384").digest(input)

    override fun sha3_512(input: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA3-512").digest(input)
}
