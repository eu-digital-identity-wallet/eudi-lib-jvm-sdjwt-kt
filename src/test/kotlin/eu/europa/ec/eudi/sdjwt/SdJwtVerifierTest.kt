/*
 * Copyright (c) 2023-2026 European Commission
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

import eu.europa.ec.eudi.sdjwt.DefaultSdJwtOps.NoSignatureValidation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

class SdJwtVerifierTest {

    @Test
    fun `when sd-jwt has a valid jwt and context is valid return Valid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
            audience = VALID_AUD,
        )
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt and 'exp' and 'aud' are valid but 'nbf' is not, return Invalid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = INVALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
            audience = VALID_AUD,
        )
        verifyPresentationExpectingError(
            VerificationError.InvalidJwt("JWT nbf claim is before given date"),
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt, 'nbf' and 'aud' are valid but 'exp' is not, return Invalid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = INVALID_EXP_TIME,
            audience = VALID_AUD,
        )
        verifyPresentationExpectingError(
            VerificationError.InvalidJwt("JWT exp claim is after given date"),
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt, 'nbf' and 'exp' are valid but 'aud' is not, return Invalid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
            audience = INVALID_AUD,
        )
        verifyPresentationExpectingError(
            VerificationError.InvalidJwt("JWT not containing valid aud value"),
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt with multiple 'aud' values but 'aud' is invalid, return Invalid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
            audience = INVALID_AUD,
        )
        verifyPresentationExpectingError(
            VerificationError.InvalidJwt("JWT not containing valid aud value"),
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwtWithMultipleAudValues~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt with multiple 'aud' values and 'aud' is valid return Valid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
            audience = VALID_AUD,
        )
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwtWithMultipleAudValues~",
            validityVerificationContext,
        )
    }

    @Test
    fun `when sd-jwt has only 'exp' but context has checks for notBefore value, ignore 'nbf' check and return Valid`() = runTest {
        val validityVerificationContext = ValidityVerificationContext(
            notBefore = VALID_NBF_TIME,
            expiresAt = VALID_EXP_TIME,
        )
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwtWithOnlyExpValue~",
            validityVerificationContext,
        )
    }

    private suspend fun verifyPresentationExpectingError(
        expectedError: VerificationError,
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        holderBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: String,
        validityVerificationContext: ValidityVerificationContext,
    ) {
        try {
            val verification =
                when (holderBindingVerifier) {
                    KeyBindingVerifier.MustNotBePresent -> DefaultSdJwtOps.verify(
                        jwtSignatureVerifier,
                        unverifiedSdJwt,
                        validityVerificationContext,
                    )
                    is KeyBindingVerifier.MustBePresentAndValid -> DefaultSdJwtOps.verify(
                        jwtSignatureVerifier,
                        holderBindingVerifier,
                        unverifiedSdJwt,
                        validityVerificationContext,
                    )
                }
            verification.getOrThrow()
            fail("Was expecting $expectedError")
        } catch (exception: SdJwtVerificationException) {
            assertEquals(expectedError, exception.reason)
        }
    }

    private suspend fun verifySuccess(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        keyBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: String,
        validityVerificationContext: ValidityVerificationContext,
    ) {
        val verification =
            when (keyBindingVerifier) {
                KeyBindingVerifier.MustNotBePresent -> DefaultSdJwtOps.verify(
                    jwtSignatureVerifier,
                    unverifiedSdJwt,
                    validityVerificationContext,
                )
                is KeyBindingVerifier.MustBePresentAndValid -> DefaultSdJwtOps.verify(
                    jwtSignatureVerifier,
                    keyBindingVerifier,
                    unverifiedSdJwt,
                    validityVerificationContext,
                )
            }
        assertTrue { verification.isSuccess }
    }

    private val jwt = """
        eyJ0eXAiOiJzZCtqd3QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGU
        uY29tL2lzc3VlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxODA0MjI5MDUzLCJzdWIiOiI
        2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCJuYmYiOjE1MTYyMzkwMjI
        sImF1ZCI6InRlc3QifQ.r1To6Mgu64GUuKdTngt0ElcqQOZS8tGIZ39BhyzM5xGF5TFVeuVr
        yr46v-tnfBsSa9PX9bQDCmkEsPpzyQaLJA
    """.trimIndent().removeNewLine()

    private val jwtWithMultipleAudValues = """
        eyJ0eXAiOiJzZCtqd3QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGU
        uY29tL2lzc3VlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxODA0MjI5MDUzLCJzdWIiOiI
        2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCJuYmYiOjE1MTYyMzkwMjI
        sImF1ZCI6WyJ0ZXN0IiwidGVzdDEiXX0.AfUgY_j7xwcyfZPXpotuBD3thCvq5jFD5w1NeLa
        g9rAzbtnq7F_T2wTpk1bXLgoPAFoWvXPljKFHZs8AQWSTBA
    """.trimIndent().removeNewLine()

    private val jwtWithOnlyExpValue = """
        eyJ0eXAiOiJzZCtqd3QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGU
        uY29tL2lzc3VlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxODA0MjI5MDUzLCJzdWIiOiI
        2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMifQ.2CHtNzS061cWfjvn-I9e
        LB2ad4-iw4E0uQ4Nt7ONHmhNPkCBYUfM8BImwCbY6jefDR0pKza1Wo9OiCBCuO1s5w~
    """.trimIndent().removeNewLine()

    companion object {
        private val INVALID_NBF_TIME = Instant.fromEpochSeconds(1516239020L)
        private val INVALID_EXP_TIME = Instant.fromEpochSeconds(1804229054L)
        private const val INVALID_AUD = "invalid"
        private const val VALID_AUD = "test"
        private val VALID_NBF_TIME = Instant.fromEpochSeconds(1772703040L)
        private val VALID_EXP_TIME = Instant.fromEpochSeconds(1772703040L)
    }
}
