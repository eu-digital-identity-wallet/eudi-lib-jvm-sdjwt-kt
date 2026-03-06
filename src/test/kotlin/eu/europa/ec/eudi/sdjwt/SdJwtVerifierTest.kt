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
import kotlin.time.Clock
import kotlin.time.Instant

class SdJwtVerifierTest {

    @Test
    fun `when nbf and exp are valid, verification succeeds`() = runTest {
        val verifier = SdJwtVerifier(Clock.fixed(Instant.fromEpochSeconds(1772703040L)))
        verifier.verifySuccess("$jwt~")
    }

    @Test
    fun `when sd-jwt is not yet active, verification fails`() = runTest {
        val verifier = SdJwtVerifier(Clock.fixed(Instant.fromEpochSeconds(1516239020L)))
        verifier.verifyExpectingError(VerificationError.InvalidJwt("SD-JWT is not active yet"), "$jwt~")
    }

    @Test
    fun `when sd-jwt is expired, verification fails`() = runTest {
        val verifier = SdJwtVerifier(Clock.fixed(Instant.fromEpochSeconds(1804229054L)))
        verifier.verifyExpectingError(VerificationError.InvalidJwt("SD-JWT is expired"), "$jwt~")
    }

    private suspend fun SdJwtVerifier<JwtAndClaims>.verifyExpectingError(
        expectedError: VerificationError,
        unverifiedSdJwt: String,
    ) {
        try {
            val verification = verify(NoSignatureValidation, unverifiedSdJwt)
            verification.getOrThrow()
            fail("Was expecting $expectedError")
        } catch (exception: SdJwtVerificationException) {
            assertEquals(expectedError, exception.reason)
        }
    }

    private suspend fun SdJwtVerifier<JwtAndClaims>.verifySuccess(unverifiedSdJwt: String) {
        val verification = verify(NoSignatureValidation, unverifiedSdJwt)
        assertTrue { verification.isSuccess }
    }

    private val jwt = """
        eyJ0eXAiOiJzZCtqd3QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGU
        uY29tL2lzc3VlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxODA0MjI5MDUzLCJzdWIiOiI
        2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCJuYmYiOjE1MTYyMzkwMjI
        sImF1ZCI6InRlc3QifQ.r1To6Mgu64GUuKdTngt0ElcqQOZS8tGIZ39BhyzM5xGF5TFVeuVr
        yr46v-tnfBsSa9PX9bQDCmkEsPpzyQaLJA
    """.trimIndent().removeNewLine()
}
