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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdJwtVerifierTest {

    private val verifier = SdJwtVerifier()

    @Test
    fun `when sd-jwt is empty verify should return ParsingError`() {
        val expectedVerification = Verification.Invalid.ParsingError
        val verification = verifier.verify(sdJwt = "")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures , no ending ~ verify should return ParsingError`() {
        val expectedVerification = Verification.Invalid.ParsingError
        val verification = verifier.verify(sdJwt = "jwt")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no holderBinding, no ending ~ verify should return ParsingError`() {
        val expectedVerification = Verification.Invalid.ParsingError
        val verification = verifier.verify(sdJwt = "$jwt")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an invalid jwt but no disclosures verify should return InvalidJwt`() {
        val expectedVerification = Verification.Invalid.InvalidJwt
        val verification = verifier.verify(sdJwt = "jwt~")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an invalid jwt, no disclosures and has holderBinding verify should return InvalidJwt`() {
        val expectedVerification = Verification.Invalid.InvalidJwt
        val verification = verifier.verify(sdJwt = "jwt~hb")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has a valid jwt, no disclosures and no holderBinding verify should return Valid`() {
        val verification = verifier.verify(sdJwt = "$jwt~")
        assertTrue(verification is Verification.Valid)
    }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and invalid holderBinding verify should return InvalidHolderBindingJwt`() {
        val expectedVerification = Verification.Invalid.InvalidHolderBindingJwt
        val verification = verifier.verify(sdJwt = "$jwt~hb")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an valid jwt, no disclosures and valid holderBinding verify should return Valid`() {
        val verification = verifier.verify(sdJwt = "$jwt~$jwt")
        assertTrue { verification is Verification.Valid }
    }

    @Test
    fun `when sd-jwt has an valid jwt, invalid disclosures verify should return InvalidDisclosures`() {
        val expectedVerification = Verification.Invalid.InvalidDisclosures(listOf("d1", "d2"))
        val verification = verifier.verify(sdJwt = "$jwt~d1~d2~")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures verify should return Valid`() {
        val verification = verifier.verify(sdJwt = "$jwt~$d1~")
        assertTrue { verification is Verification.Valid }
    }

    @Test
    fun `when sd-jwt has an valid jwt, non unique disclosures verify should return NonUnqueDisclosures`() {
        val expectedVerification = Verification.Invalid.NonUnqueDisclosures
        val verification = verifier.verify(sdJwt = "$jwt~$d1~$d1~")
        assertEquals(expectedVerification, verification)
    }

    @Test
    fun `when sd-jwt has an valid jwt, valid disclosures and valid holder binding verify should return Valid`() {
        val verification = verifier.verify(sdJwt = "$jwt~$d1~$jwt")
        assertTrue { verification is Verification.Valid }
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
}

private fun String.removeNewLine(): String = replace("\n", "")
