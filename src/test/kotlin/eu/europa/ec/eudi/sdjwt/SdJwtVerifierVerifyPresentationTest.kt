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

import eu.europa.ec.eudi.sdjwt.DefaultSdJwtOps.KeyBindingVerifierMustBePresent
import eu.europa.ec.eudi.sdjwt.DefaultSdJwtOps.NoSignatureValidation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SdJwtVerifierVerifyPresentationTest {

    @Test
    fun `when sd-jwt is empty verify should return ParsingError`() = runTest {
        verifyPresentationExpectingError(
            VerificationError.ParsingError,
            NoSignatureValidation,
            KeyBindingVerifierMustBePresent,
            "",
        )
    }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures , no ending ~ verify should return ParsingError`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.ParsingError,
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "jwt",
            )
        }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no keyBinding, no ending ~ verify should return ParsingError`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.ParsingError,
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                jwt,
            )
        }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures verify should return InvalidJwt`() = runTest {
        verifyPresentationExpectingError(
            VerificationError.InvalidJwt("Serialized JWT must have exactly 3 parts"),
            NoSignatureValidation,
            KeyBindingVerifierMustBePresent,
            "jwt~",
        )
    }

    @Test
    fun `when sd-jwt has an invalid jwt, no disclosures and has keyBinding verify should return InvalidJwt`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.InvalidJwt("Serialized JWT must have exactly 3 parts"),
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "jwt~hb",
            )
        }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no keyBinding verify should return Valid`() = runTest {
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and invalid keyBinding verify should return InvalidKeyBindingJwt`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.KeyBindingFailed(
                    KeyBindingError.InvalidKeyBindingJwt(
                        "Could not verify KeyBinding JWT",
                        SdJwtVerificationException(reason = VerificationError.InvalidJwt("Serialized JWT must have exactly 3 parts")),
                    ),
                ),
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "$jwt~hb",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and keyBinding without 'sd_hash' verify fails with InvalidKeyBindingJwt`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.InvalidKeyBindingJwt("sd_hash claim contains an invalid value")),
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "$jwt~$jwt",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures valid keyBinding with 'sd_hash' verify should return Valid`() =
        runTest {
            verifySuccess(
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "$jwt~$kbWithoutD1",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, invalid disclosures verify should return InvalidDisclosures`() = runTest {
        verifyPresentationExpectingError(
            VerificationError.InvalidDisclosures(listOf("d1", "d2")),
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~d1~d2~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures verify should return Valid`() = runTest {
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~$d1~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, non unique disclosures verify should return NonUnqueDisclosures`() = runTest {
        verifyPresentationExpectingError(
            VerificationError.NonUniqueDisclosures,
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            "$jwt~$d1~$d1~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures and keyBinding without 'sd_hash' verify fails with InvalidKeyBindingJwt`() =
        runTest {
            verifyPresentationExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.InvalidKeyBindingJwt("sd_hash claim contains an invalid value")),
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "$jwt~$d1~$jwt",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures and valid keyBinding with 'sd_hash' verify should return Valid`() =
        runTest {
            verifySuccess(
                NoSignatureValidation,
                KeyBindingVerifierMustBePresent,
                "$jwt~$d1~$kbWithD1",
            )
        }

    @Test
    fun `happy path with sd-jwt with kb-jwt in JWS JSON`() = runTest {
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifierMustBePresent,
            Json.parseToJsonElement(ex2).jsonObject,
        )
    }

    @Test
    fun `happy path with sd-jwt without kb-jwt in JWS JSON`() = runTest {
        verifySuccess(
            NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            Json.parseToJsonElement(ex3).jsonObject,
        )
    }

    private suspend fun verifyPresentationExpectingError(
        expectedError: VerificationError,
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        holderBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: String,
    ) {
        try {
            val verification =
                when (holderBindingVerifier) {
                    KeyBindingVerifier.MustNotBePresent -> DefaultSdJwtOps.verify(jwtSignatureVerifier, unverifiedSdJwt)
                    is KeyBindingVerifier.MustBePresentAndValid -> DefaultSdJwtOps.verify(
                        jwtSignatureVerifier,
                        holderBindingVerifier,
                        unverifiedSdJwt,
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
    ) {
        val verification =
            when (keyBindingVerifier) {
                KeyBindingVerifier.MustNotBePresent -> DefaultSdJwtOps.verify(jwtSignatureVerifier, unverifiedSdJwt)
                is KeyBindingVerifier.MustBePresentAndValid -> DefaultSdJwtOps.verify(
                    jwtSignatureVerifier,
                    keyBindingVerifier,
                    unverifiedSdJwt,
                )
            }
        assertTrue { verification.isSuccess }
    }

    private suspend fun verifySuccess(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        keyBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: JsonObject,
    ) {
        val verification =
            when (keyBindingVerifier) {
                KeyBindingVerifier.MustNotBePresent -> DefaultSdJwtOps.verify(jwtSignatureVerifier, unverifiedSdJwt)
                is KeyBindingVerifier.MustBePresentAndValid -> DefaultSdJwtOps.verify(
                    jwtSignatureVerifier,
                    keyBindingVerifier,
                    unverifiedSdJwt,
                )
            }
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

private val ex2 = """
    {
      "header": {
        "disclosures": [
          "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
          "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        ],
        "kb_jwt": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZCI6ICJodHRwczovL3ZlcmlmaWVyLmV4YW1wbGUub3JnIiwgImlhdCI6IDE3MjUzNzQ0MTMsICJzZF9oYXNoIjogImQ5T3pJclJQY2dVanNKb3NzeVJ3SjZNOXo5TGpneGQtWmk3VmJfNGxveXMifQ.KEni_tu4WRFeH7croigMQu2u0Xy3dsUf7bmmDT8Q5yTg_xFh7kMxbWemFglmFUVrwqxdLHvXNuiKguF3TztL9Q"
      },
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJjZ0ZkaHFQbzgzeFlObEpmYWNhQ2FhN3VQOVJDUjUwVkU1UjRMQVE5aXFVIiwgImpNQ1hWei0tOWI4eDM3WWNvRGZYUWluencxd1pjY2NmRlJCQ0ZHcWRHMm8iXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ",
      "protected": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0",
      "signature": "QqT_REPTOaBX4EzA9rQqad_iOL6pMl9_onmFH_q-Npyqal5TsxcUc5FIKjQL9BFO8QvA0BFbVbzaO-NLonN3Mw"
    }
""".trimIndent()

private val ex3 = """
    {
      "header": {
        "disclosures": [
          "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
          "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        ]
      },
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJjZ0ZkaHFQbzgzeFlObEpmYWNhQ2FhN3VQOVJDUjUwVkU1UjRMQVE5aXFVIiwgImpNQ1hWei0tOWI4eDM3WWNvRGZYUWluencxd1pjY2NmRlJCQ0ZHcWRHMm8iXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ",
      "protected": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0",
      "signature": "QqT_REPTOaBX4EzA9rQqad_iOL6pMl9_onmFH_q-Npyqal5TsxcUc5FIKjQL9BFO8QvA0BFbVbzaO-NLonN3Mw"
    }
""".trimIndent()
