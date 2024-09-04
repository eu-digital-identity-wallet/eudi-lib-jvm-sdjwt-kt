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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.fail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdJwtVerifierVerifyIssuanceTest {

    @Test
    fun simple() =
        runTest {
            verifyIssuanceSuccess(JwtSignatureVerifier.NoSignatureValidation, unverifiedSdJwt = "$jwt~$d1~")
        }

    @Test
    fun simpleJWSJsonGeneral() =
        runTest {
            verifyIssuanceSuccess(
                JwtSignatureVerifier.NoSignatureValidation,
                unverifiedSdJwtJWSJson(JwsSerializationOption.General),
            )
        }

    @Test
    fun simpleJWSJsonFlattened() =
        runTest {
            verifyIssuanceSuccess(
                JwtSignatureVerifier.NoSignatureValidation,
                unverifiedSdJwtJWSJson(JwsSerializationOption.Flattened),
            )
        }

    @Test
    fun `when sd-jwt doesn't end in ~ raise UnexpectedKeyBindingJwt`() =
        runTest {
            verifyIssuanceExpectingError(
                VerificationError.KeyBindingFailed(KeyBindingError.UnexpectedKeyBindingJwt),
                JwtSignatureVerifier.NoSignatureValidation,
                "$jwt~$d1",
            )
        }

    private suspend fun verifyIssuanceSuccess(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: JsonObject,
    ) {
        val verification =
            SdJwtVerifier.verifyIssuance(jwtSignatureVerifier = jwtSignatureVerifier, unverifiedSdJwt = unverifiedSdJwt)
        assertTrue { verification.isSuccess }
    }

    private suspend fun verifyIssuanceSuccess(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: String,
    ) {
        val verifiedSdJwt = assertDoesNotThrow {
            SdJwtVerifier.verifyIssuance(jwtSignatureVerifier = jwtSignatureVerifier, unverifiedSdJwt = unverifiedSdJwt).getOrThrow()
        }

        val sdJwtWithOutSigVerification = assertDoesNotThrow {
            SdJwt.unverifiedIssuanceFrom(unverifiedSdJwt).getOrThrow()
        }
        assertEquals(verifiedSdJwt, sdJwtWithOutSigVerification)
    }

    private fun unverifiedSdJwtJWSJson(option: JwsSerializationOption): JsonObject {
        val protected = "eyJhbGciOiAiRVMyNTYifQ"
        val payload = """
            eyJfc2QiOiBbIkZwaEZGcGoxdnRyMHJwWUstMTRmaWNrR
            0tNZzN6ZjFmSXBKWHhUSzhQQUUiXSwgImlzcyI6ICJodHRwczovL2V4YW1wbGUuY29tL
            2lzc3VlciIsICJpYXQiOiAxNTE2MjM5MDIyLCAiZXhwIjogMTczNTY4OTY2MSwgInN1Y
            iI6ICI2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCAiX3NkX2FsZ
            yI6ICJzaGEtMjU2In0
        """.trimIndent().removeNewLine()
        val signature = """
            tqqCvNdrZ8ILN82t3g-T8LQJp3ykVf8tVPfAr8ijqhG9uc0Kl
            wYeE4ISu3DQkOk7VeaMMYB73Hsdyjal6e9FS
        """.trimIndent().removeNewLine()

        return with(JwsJsonSupport) {
            option.buildJwsJson(protected, payload, signature, setOf(d1), kbJwt = null)
        }
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

    private suspend fun verifyIssuanceExpectingError(
        expectedError: VerificationError,
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: String,
    ) {
        val verification = SdJwtVerifier.verifyIssuance(
            jwtSignatureVerifier = jwtSignatureVerifier,
            unverifiedSdJwt = unverifiedSdJwt,
        )
        verification.fold(
            onSuccess = { fail("Was expecting error") },
            onFailure = { exception ->
                if (exception is SdJwtVerificationException) {
                    assertEquals(expectedError, exception.reason)
                } else {
                    fail(exception)
                }
            },
        )
    }

    private suspend fun verifyIssuanceExpectingError(
        expectedError: VerificationError,
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: JsonObject,
    ) {
        val verification = SdJwtVerifier.verifyIssuance(
            jwtSignatureVerifier = jwtSignatureVerifier,
            unverifiedSdJwt = unverifiedSdJwt,
        )
        verification.fold(
            onSuccess = { fail("Was expecting error") },
            onFailure = { exception ->
                if (exception is SdJwtVerificationException) {
                    assertEquals(expectedError, exception.reason)
                } else {
                    fail(exception)
                }
            },
        )
    }

    @Test
    fun `when sd-jwt is empty verify should return ParsingError`() = runTest {
        verifyIssuanceExpectingError(
            VerificationError.ParsingError,
            JwtSignatureVerifier.NoSignatureValidation,
            "",
        )
    }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures , no ending ~ verify should return ParsingError`() =
        runTest {
            verifyIssuanceExpectingError(
                VerificationError.ParsingError,
                JwtSignatureVerifier.NoSignatureValidation,
                "jwt",
            )
        }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no holderBinding, no ending ~ verify should return ParsingError`() =
        runTest {
            verifyIssuanceExpectingError(
                VerificationError.ParsingError,
                JwtSignatureVerifier.NoSignatureValidation,
                jwt,
            )
        }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures verify should return InvalidJwt`() = runTest {
        verifyIssuanceExpectingError(
            VerificationError.InvalidJwt,
            JwtSignatureVerifier.NoSignatureValidation,
            "jwt~",
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no holderBinding verify should return Valid`() = runTest {
        verifyIssuanceSuccess(
            JwtSignatureVerifier.NoSignatureValidation,
            "$jwt~",
        )
    }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no holderBinding verify should return Valid in JWS JSON`() =
        runTest {
            val unverifiedSdJwt = unverifiedSdJwtJWSJson(JwsSerializationOption.Flattened).run {
                val mutable = toMutableMap()
                mutable.remove("disclosures")
                JsonObject(mutable)
            }

            verifyIssuanceSuccess(
                JwtSignatureVerifier.NoSignatureValidation,
                unverifiedSdJwt,
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, invalid disclosures verify should return InvalidDisclosures`() = runTest {
        val unverifiedSdJwt = unverifiedSdJwtJWSJson(JwsSerializationOption.Flattened).run {
            val mutableHeader = checkNotNull(this["header"]).jsonObject.toMutableMap()
            mutableHeader["disclosures"] = JsonArray(listOf("d1", "d2").map { JsonPrimitive(it) })
            val mutable = toMutableMap()
            mutable["header"] = JsonObject(mutableHeader)
            JsonObject(mutable)
        }
        verifyIssuanceExpectingError(
            VerificationError.InvalidDisclosures(listOf("d1", "d2")),
            JwtSignatureVerifier.NoSignatureValidation,
            unverifiedSdJwt,
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, invalid disclosures verify should return InvalidDisclosures in JWS Json`() =
        runTest {
            verifyIssuanceExpectingError(
                VerificationError.InvalidDisclosures(listOf("d1", "d2")),
                JwtSignatureVerifier.NoSignatureValidation,
                "$jwt~d1~d2~",
            )
        }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures verify should return Valid`() = runTest {
        verifyIssuanceSuccess(
            JwtSignatureVerifier.NoSignatureValidation,
            "$jwt~$d1~",
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, non unique disclosures verify should return NonUnqueDisclosures`() = runTest {
        val unverifiedSdJwt = unverifiedSdJwtJWSJson(JwsSerializationOption.Flattened).run {
            val mutableHeader = checkNotNull(this["header"]).jsonObject.toMutableMap()
            mutableHeader["disclosures"] = JsonArray(listOf(d1, d1).map { JsonPrimitive(it) })
            val mutable = toMutableMap()
            mutable["header"] = JsonObject(mutableHeader)
            JsonObject(mutable)
        }
        verifyIssuanceExpectingError(
            VerificationError.NonUniqueDisclosures,
            JwtSignatureVerifier.NoSignatureValidation,
            unverifiedSdJwt,
        )
    }

    @Test
    fun `when sd-jwt has an valid jwt, non unique disclosures verify should return NonUnqueDisclosures in JWS JSon`() =
        runTest {
            verifyIssuanceExpectingError(
                VerificationError.NonUniqueDisclosures,
                JwtSignatureVerifier.NoSignatureValidation,
                "$jwt~$d1~$d1~",
            )
        }
}
