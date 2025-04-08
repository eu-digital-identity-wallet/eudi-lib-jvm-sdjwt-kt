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

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmAndAndroidHashesTest {

    @Test
    fun testSha256() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA-256").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha256(input))
        assertEquals(expected, actual)
    }

    @Test
    fun testSha384() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA-384").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha384(input))
        assertEquals(expected, actual)
    }

    @Test
    fun testSha512() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA-512").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha512(input))
        assertEquals(expected, actual)
    }

    @Test
    fun testSha3_256() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA3-256").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha3_256(input))
        assertEquals(expected, actual)
    }

    @Test
    fun testSha3_384() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA3-384").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha3_384(input))
        assertEquals(expected, actual)
    }

    @Test
    fun testSha3_512() {
        val input = "test".encodeToByteArray()
        val expected = Base64UrlNoPadding.encode(java.security.MessageDigest.getInstance("SHA3-512").digest(input))
        val actual = Base64UrlNoPadding.encode(JvmAndAndroidHashes.sha3_512(input))
        assertEquals(expected, actual)
    }
}
