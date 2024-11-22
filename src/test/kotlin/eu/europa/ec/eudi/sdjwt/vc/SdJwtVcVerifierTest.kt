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
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
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
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private object SampleIssuer {
    private val iss = Url("https://example.com")
    const val KEY_ID = "signing-key-01"
    private val alg = JWSAlgorithm.ES256
    private val key: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(KEY_ID)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate()
    val issuerMeta = run {
        val jwks = Json.parseToJsonElement(JWKSet(key.toPublicJWK()).toString())
        buildJsonObject {
            put("issuer", iss.toString())
            put("jwks", jwks)
        }
    }

    private fun issuer(kid: String?) =
        SdJwtIssuer.nimbus(signer = ECDSASigner(key), signAlgorithm = alg) {
            type(JOSEObjectType(SD_JWT_VC_TYPE))
            kid?.let { keyID(it) }
        }

    fun issueUsingKid(kid: String?): String {
        val issuer = issuer(kid)
        val sdJwtSpec = sdJwt {
            iss(iss.toString())
            iat(Instant.now().toEpochMilli())
            sd("foo", "bar")
        }
        return issuer.issue(sdJwtSpec).getOrThrow().serialize()
    }
}

private object HttpMock {

    fun clientReturning(issuerMeta: JsonObject): HttpClient =
        HttpClient { _ ->
            respond(
                issuerMeta.toString(),
                HttpStatusCode.OK,
                headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
            )
        }

    @Suppress("TestFunctionName")
    private fun HttpClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
        }
}

class SdJwtVcVerifierTest {

    @Test
    fun `keySource should return a Metadata when iss is a https url`() {
        val expectedSource = SdJwtVcIssuerPublicKeySource.Metadata(Url("https://example.com"))
        testForMetaDataSource(expectedSource)
    }

    private fun testForMetaDataSource(expectedSource: SdJwtVcIssuerPublicKeySource.Metadata) {
        val jwt = run {
            val header = JWSHeader.Builder(JWSAlgorithm.ES256).apply {
                type(JOSEObjectType(SD_JWT_VC_TYPE))
            }.build()
            val payload = JWTClaimsSet.Builder().apply {
                issuer(expectedSource.iss.toString())
            }.build()
            SignedJWT(header, payload)
        }

        val actualSource = keySource(jwt)
        assertEquals(expectedSource, actualSource)
    }

    @Test
    fun `keySource should return a DIDUrl when iss is a DID and kid is provided`() {
        val expectedSource =
            SdJwtVcIssuerPublicKeySource.DIDUrl(
                iss = "did:ebsi:zkC6cUFUs3FiRp2xedNwih2",
                kid = "did:ebsi:zkC6cUFUs3FiRp2xedNwih2#x8x4WxXHoPW7ccEO0zACL_miBfO-V7X_jofc-UEGzw4",
            )
        testForDid(expectedSource)
    }

    private fun testForDid(expectedSource: SdJwtVcIssuerPublicKeySource.DIDUrl) {
        val jwt = run {
            val header = JWSHeader.Builder(JWSAlgorithm.ES256).apply {
                type(JOSEObjectType(SD_JWT_VC_TYPE))
                expectedSource.kid?.let { keyID(it) }
            }.build()
            val payload = JWTClaimsSet.Builder().apply {
                issuer(expectedSource.iss)
            }.build()
            SignedJWT(header, payload)
        }

        val actualSource = keySource(jwt)
        assertEquals(expectedSource, actualSource)
    }

    @Test
    fun `SdJwtVcVerifier should verify an SD-JWT-VC when iss is HTTPS url using kid`() = runTest {
        val unverifiedSdJwt = SampleIssuer.issueUsingKid(kid = SampleIssuer.KEY_ID)
        val verifier = SdJwtVcVerifier.builder()
            .enableIssuerMetadataResolution { HttpMock.clientReturning(SampleIssuer.issuerMeta) }
            .build()

        assertDoesNotThrow {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
    }

    @Test
    fun `SdJwtVcVerifier should verify an SD-JWT-VC when iss is HTTPS url and no kid`() = runTest {
        val unverifiedSdJwt = SampleIssuer.issueUsingKid(kid = null)
        val verifier = SdJwtVcVerifier.builder()
            .enableIssuerMetadataResolution { HttpMock.clientReturning(SampleIssuer.issuerMeta) }
            .build()

        assertDoesNotThrow {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
    }

    @Test
    fun `SdJwtVcVerifier should not verify an SD-JWT-VC when iss is HTTPS url using wrong kid`() = runTest {
        // In case the issuer uses the KID
        val unverifiedSdJwt = SampleIssuer.issueUsingKid("wrong kid")
        val verifier = SdJwtVcVerifier.builder()
            .enableIssuerMetadataResolution { HttpMock.clientReturning(SampleIssuer.issuerMeta) }
            .build()

        val exception = assertThrows<SdJwtVerificationException> {
            verifier.verifyIssuance(unverifiedSdJwt).getOrThrow()
        }
        assertEquals(VerificationError.InvalidJwt, exception.reason)
    }

    @Test
    fun `SdJwtVcVerifier should verify an SD-JWT-VC that has been signed using an OctetKeyPair using Ed25519 curve and EdDSA algorithm`() =
        runTest {
            val key = OctetKeyPairGenerator(Curve.Ed25519).generate()
            val didJwk = "did:jwk:${Base64.UrlSafe.encode(key.toPublicJWK().toJSONString().toByteArray())}"

            val sdJwt = run {
                val spec = sdJwt {
                    iss(didJwk)
                }
                val signer = SdJwtIssuer.nimbus(signer = Ed25519Signer(key), signAlgorithm = JWSAlgorithm.EdDSA) {
                    type(JOSEObjectType(SD_JWT_VC_TYPE))
                }
                signer.issue(spec).getOrThrow()
            }

            val verifier = SdJwtVcVerifier.builder()
                .enableIssuerMetadataResolution { fail("Issuer metadata resolution should not have been used") }
                .enableDidResolution { did, _ ->
                    assertEquals(didJwk, did)
                    listOf(key.toPublicJWK())
                }
                .build()

            verifier.verifyIssuance(sdJwt.serialize()).getOrThrow()
        }
}
