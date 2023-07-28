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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SdJwtVerifierVerifyEnvelopedPresentationTest {

    @Test
    fun `happy path`() {
        // given
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() - 1).epochSecond)
        val envelopeJwt = sdJwt.envelope(iat).serialize()
        // run verification
        val sdJwtActual = verify(envelopeJwt)
        // expect
        assertEquals(sdJwt, sdJwtActual)
    }

    @Test
    fun `with invalid iat`() {
        // given invalid iat (outside offset)
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() + 10).epochSecond)
        val envelopeJwt = sdJwt.envelope(iat).serialize()

        // then expect
        val exception = assertThrows<SdJwtVerificationException> {
            verify(envelopeJwt)
        }
        assertEquals(VerificationError.InvalidJwt, exception.reason)
    }

    @Test
    fun `with invalid sdjwt`() {
        val envelopeJwt = "foo bar"
        val exception = assertThrows<SdJwtVerificationException> {
            verify(envelopeJwt)
        }
        assertEquals(VerificationError.InvalidJwt, exception.reason)
    }

    private val now = Instant.now()
    private val clock = Clock.fixed(now, Clock.systemUTC().zone)
    private val iatOffset = Duration.ofSeconds(30)

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

    private val sdJwt = SdJwt.Presentation(jwt, setOf(Disclosure.wrap(d1).getOrThrow()), null)
    private val holderKey = genKey("holder")
    private val holderAlg = JWSAlgorithm.ES256
    private val verifierClientId = "The verifier"
    private fun genKey(kid: String) = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
    private fun SdJwt.Presentation<Jwt, Nothing>.envelope(iat: Instant): SignedJWT =
        toEnvelopedFormat(
            iat,
            "nonce",
            verifierClientId,
            { it },
            holderKey,
            holderAlg,
        ).getOrThrow()

    private fun verify(envelopeJwt: String): SdJwt.Presentation<Jwt, Nothing> {
        return SdJwtVerifier.verifyEnvelopedPresentation(
            sdJwtSignatureVerifier = JwtSignatureVerifier.NoSignatureValidation,
            envelopeJwtVerifier = ECDSAVerifier(holderKey).asJwtVerifier(),
            clock = clock,
            iatOffset = iatOffset,
            expectedAudience = verifierClientId,
            unverifiedEnvelopeJwt = envelopeJwt,
        ).map {
            SdJwt.Presentation(it.jwt.first, it.disclosures, it.keyBindingJwt)
        }.getOrThrow()
    }
}
