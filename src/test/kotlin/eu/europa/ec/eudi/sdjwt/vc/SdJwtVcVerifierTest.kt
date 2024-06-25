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
package eu.europa.ec.eudi.sdjwt.vc

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private object SampleIssuer {
    val iss = Url("https://example.com")
    private val alg = JWSAlgorithm.ES256
    val issuerKey = ECKeyGenerator(Curve.P_256)
        .keyID("someKeyId")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate()
    val issuerMeta = run {
        val jwks = Json.parseToJsonElement(JWKSet(issuerKey.toPublicJWK()).toString())
        buildJsonObject {
            put("issuer", iss.toString())
            put("jwks", jwks)
        }
    }

    fun issuer(kid: String?) =
        SdJwtIssuer.nimbus(signer = ECDSASigner(issuerKey), signAlgorithm = alg) {
            type(JOSEObjectType(SD_JWT_VC_TYPE))
            kid?.let { keyID(it) }
        }
}

class SdJwtVcVerifierTest {

    @Test
    fun `keySource() should return a Metadata when iss is a https url`() {
        val expectedSource = SdJwtVcIssuerPublicKeySource.Metadata(Url("https://example.com"), null)
        testForMetaDataSource(expectedSource)
    }

    @Test
    fun `keySource() should return a Metadata when iss is a https url and kid is provided`() {
        val expectedSource = SdJwtVcIssuerPublicKeySource.Metadata(Url("https://example.com"), "some-kid")
        testForMetaDataSource(expectedSource)
    }

    @Test
    fun `SdJwtVcVerifier should verify with metadata when iss is HTTPS url using kid`() = runTest {
        // In case the issuer uses the KID
        val unverifiedSdJwt = run {
            val issuer = SampleIssuer.issuer(kid = SampleIssuer.issuerKey.keyID) // correct kid
            val sdJwtSpec = sdJwtSpec(SampleIssuer.iss)
            issuer.issue(sdJwtSpec).getOrThrow().serialize()
        }

        val verifier =
            SdJwtVcVerifier(httpClientFactory = { httpClientReturning(SampleIssuer.issuerMeta) })

        assertDoesNotThrow {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
    }

    @Test
    fun `SdJwtVcVerifier should verify with metadata when iss is HTTPS url and no kid`() = runTest {
        val unverifiedSdJwt = run {
            val issuer = SampleIssuer.issuer(kid = null) // no kid
            val sdJwtSpec = sdJwtSpec(SampleIssuer.iss)
            issuer.issue(sdJwtSpec).getOrThrow().serialize()
        }

        val verifier =
            SdJwtVcVerifier(httpClientFactory = { httpClientReturning(SampleIssuer.issuerMeta) })

        assertDoesNotThrow {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
    }

    @Test
    fun `SdJwtVcVerifier should not verify with metadata when iss is HTTPS url using wrong kid`() = runTest {
        // In case the issuer uses the KID
        val unverifiedSdJwt = run {
            val issuer = SampleIssuer.issuer(kid = "wrong kid")
            val sdJwtSpec = sdJwtSpec(SampleIssuer.iss)
            issuer.issue(sdJwtSpec).getOrThrow().serialize()
        }

        val verifier =
            SdJwtVcVerifier(httpClientFactory = { httpClientReturning(SampleIssuer.issuerMeta) })

        val exception = assertThrows<SdJwtVerificationException> {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
        assertEquals(VerificationError.InvalidJwt, exception.reason)
    }

    private fun sdJwtSpec(iss: Url) = sdJwt {
        iss(iss.toString())
        sd("foo", "bar")
        iat(Instant.now().toEpochMilli())
    }

    private fun httpClientReturning(issuerMeta: JsonObject): HttpClient =
        HttpClient { _ ->
            respond(
                issuerMeta.toString(),
                HttpStatusCode.OK,
                headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
            )
        }
}

@Suppress("TestFunctionName")
private fun HttpClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) {
            json()
        }
    }

private fun testForMetaDataSource(expectedSource: SdJwtVcIssuerPublicKeySource.Metadata) {
    val jwt = run {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).apply {
            type(JOSEObjectType(SD_JWT_VC_TYPE))
            expectedSource.kid?.let { keyID(it) }
        }.build()
        val payload = JWTClaimsSet.Builder().apply {
            issuer(expectedSource.iss.toString())
        }.build()
        SignedJWT(header, payload)
    }

    val actualSource = keySource(jwt)
    assertEquals(expectedSource, actualSource)
}
