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

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdJwtVerifierVerifyPresentationTest {

    @Test
    fun `when sd-jwt is empty verify should return ParsingError`() = runTest {
        verifyPresnetationExpectingError(
            VerificationError.ParsingError,
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustBePresent,
            "",
        )
    }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures , no ending ~ verify should return ParsingError`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.ParsingError,
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "jwt",
            )
        }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no keyBinding, no ending ~ verify should return ParsingError`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.ParsingError,
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                jwt,
            )
        }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures verify should return InvalidJwt`() = runTest {
        verifyPresnetationExpectingError(
            VerificationError.InvalidJwt,
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustBePresent,
            "jwt~",
        )
    }

    @Test
    fun `when sd-jwt has an invalid jwt, no disclosures and has keyBinding verify should return InvalidJwt`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.InvalidJwt,
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "jwt~hb",
            )
        }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no keyBinding verify should return Valid`() = runTest {
        verifySuccess(
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and invalid keyBinding verify should return InvalidKeyBindingJwt`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.InvalidKeyBindingJwt),
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "$jwt~hb",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and keyBinding without 'sd_hash' verify fails with InvalidKeyBindingJwt`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.InvalidKeyBindingJwt),
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "$jwt~$jwt",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures valid keyBinding with 'sd_hash' verify should return Valid`() =
        runTest {
            verifySuccess(
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "$jwt~$kbWithoutD1",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, invalid disclosures verify should return InvalidDisclosures`() = runTest {
        verifyPresnetationExpectingError(
            VerificationError.InvalidDisclosures(listOf("d1", "d2")),
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~d1~d2~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures verify should return Valid`() = runTest {
        verifySuccess(
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~$d1~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, non unique disclosures verify should return NonUnqueDisclosures`() = runTest {
        verifyPresnetationExpectingError(
            VerificationError.NonUniqueDisclosures,
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~$d1~$d1~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures and keyBinding without 'sd_hash' verify fails with InvalidKeyBindingJwt`() =
        runTest {
            verifyPresnetationExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.InvalidKeyBindingJwt),
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "$jwt~$d1~$jwt",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures and valid keyBinding with 'sd_hash' verify should return Valid`() =
        runTest {
            verifySuccess(
                JwtSignatureVerifier.NoSignatureValidation,
                KeyBindingVerifier.MustBePresent,
                "$jwt~$d1~$kbWithD1",
            )
        }

    private suspend fun verifyPresnetationExpectingError(
        expectedError: VerificationError,
        jwtSignatureVerifier: JwtSignatureVerifier,
        holderBindingVerifier: KeyBindingVerifier,
        unverifiedSdJwt: String,
    ) {
        val exception = assertThrows<SdJwtVerificationException> {
            SdJwtVerifier.verifyPresentation(
                jwtSignatureVerifier = jwtSignatureVerifier,
                keyBindingVerifier = holderBindingVerifier,
                unverifiedSdJwt = unverifiedSdJwt,
            ).getOrThrow()
        }
        assertEquals(expectedError, exception.reason)
    }

    private suspend fun verifySuccess(
        jwtSignatureVerifier: JwtSignatureVerifier,
        keyBindingVerifier: KeyBindingVerifier,
        unverifiedSdJwt: String,
    ) {
        val verification =
            SdJwtVerifier.verifyPresentation(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt)
        assertTrue { verification.isSuccess }
    }

    private val jwt = """
            eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIkZwaEZGcGoxdnRyMHJwWUstMTRmaWNrR
            0tNZzN6ZjFmSXBKWHhUSzhQQUUiXSwgImlzcyI6ICJodHRwczovL2V4YW1wbGUuY29tL
            2lzc3VlciIsICJpYXQiOiAxNTE2MjM5MDIyLCAiZXhwIjogMTczNTY4OTY2MSwgInN1Y
            iI6ICI2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCAiX3NkX2FsZ
            yI6ICJzaGEtMjU2In0.tqqCvNdrZ8ILN82t3g-T8LQJp3ykVf8tVPfAr8ijqhG9uc0Kl
            wYeE4ISu3DQkOk7VeaMMYB73Hsdyjal6e9FS
    """.trimIndent().removeNewLine()

    private val d1 = """
            WyJpbVFmR2oxX00wRWw3NmtkdmY3RG
            F3IiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIlNjaHVsc3RyLiAxMiIsIC
            Jsb2NhbGl0eSI6ICJTY2h1bHBmb3J0YSIsICJyZWdpb24iOiAiU2FjaHNlbi1BbmhhbH
            QiLCAiY291bnRyeSI6ICJERSJ9XQ
    """.trimIndent().removeNewLine()

    private val kbWithoutD1 = """
            eyJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoiT2tRZGtDVFNxRFpua2hrLTNWSVNZbEc0
            aW1NQ3FTc09fdVFfaXZxQjJ4ayJ9.u3to8ttbbYCFds3QqhI9D3Hmfygz4-0PG3KjTGKh
            ROlpl5WuylBButnJWN6D2iVyYmLvfZCwgXLiQV0DdO1nOA    
    """.trimIndent().removeNewLine()

    private val kbWithD1 = """
            eyJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoidlFEb0laSlhLWXBCbEZsSF9YM0psTFA4
            Sm02ODBqNHJnbWdUd3JjX2lMcyJ9.rkN3lEPVuXaLU3wrL0a5xGQjj8vBsHxWEGh5IQdZ
            VrKEsvpb1Pe3fK7v5Ygh4gRL4zCR6QVm6VqzxdiZ67m0Hg
    """.trimIndent().removeNewLine()
}
