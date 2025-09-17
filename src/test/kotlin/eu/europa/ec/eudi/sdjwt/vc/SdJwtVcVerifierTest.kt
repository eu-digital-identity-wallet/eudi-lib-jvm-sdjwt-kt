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
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant

private object SampleIssuer {
    const val KEY_ID = "signing-key-01"
    private val alg = JWSAlgorithm.ES256
    private val key: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(KEY_ID)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate()
    val issuerMeta = SdJwtVcIssuerMetadata(
        issuer = "https://example.com",
        jwks = Json.parseToJsonElement(JWKSet(key.toPublicJWK()).toString()).jsonObject,
    )

    private fun sdJwtVcIssuer(kid: String?) =
        NimbusSdJwtOps.issuer(
            signer = ECDSASigner(key),
            signAlgorithm = alg,
        ) {
            type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
            kid?.let { keyID(it) }
        }

    suspend fun issueUsingKid(kid: String?): String {
        val issuer = sdJwtVcIssuer(kid)
        val sdJwtSpec = sdJwt {
            claim(RFC7519.ISSUER, issuerMeta.issuer)
            claim(RFC7519.ISSUED_AT, Clock.System.now().epochSeconds)
            claim(SdJwtVcSpec.VCT, "urn:credential:sample")
            sdClaim("foo", "bar")
        }
        return with(NimbusSdJwtOps) {
            issuer.issue(sdJwtSpec).getOrThrow().serialize()
        }
    }
}

class SdJwtVcVerifierTest {

    @Test
    fun `keySource should return a Metadata when iss is a https url`() {
        val expectedSource = SdJwtVcIssuerPublicKeySource.Metadata(Url("https://example.com"), null)
        testForMetaDataSource(expectedSource)
    }

    @Test
    fun `keySource should return a Metadata when iss is a https url and kid is provided`() {
        val expectedSource = SdJwtVcIssuerPublicKeySource.Metadata(Url("https://example.com"), "some-kid")
        testForMetaDataSource(expectedSource)
    }

    private fun testForMetaDataSource(expectedSource: SdJwtVcIssuerPublicKeySource.Metadata) {
        val jwt = run {
            val header = JWSHeader.Builder(JWSAlgorithm.ES256).apply {
                type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
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
                type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
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
        val verifier = DefaultSdJwtOps.SdJwtVcVerifier(
            IssuerVerificationMethod.usingIssuerMetadata(HttpMock.clientReturning(SampleIssuer.issuerMeta)),
            TypeMetadataPolicy.NotUsed,
        )
        verifier.verify(unverifiedSdJwt).getOrThrow()
    }

    @Test
    fun `SdJwtVcVerifier should verify an SD-JWT-VC when iss is HTTPS url and no kid`() = runTest {
        val unverifiedSdJwt = SampleIssuer.issueUsingKid(kid = null)
        val verifier = DefaultSdJwtOps.SdJwtVcVerifier(
            IssuerVerificationMethod.usingIssuerMetadata(HttpMock.clientReturning(SampleIssuer.issuerMeta)),
            TypeMetadataPolicy.NotUsed,
        )
        verifier.verify(unverifiedSdJwt).getOrThrow()
    }

    @Test
    fun `SdJwtVcVerifier should not verify an SD-JWT-VC when iss is HTTPS url using wrong kid`() = runTest {
        // In case the issuer uses the KID
        val unverifiedSdJwt = SampleIssuer.issueUsingKid("wrong kid")
        val verifier = DefaultSdJwtOps.SdJwtVcVerifier(
            IssuerVerificationMethod.usingIssuerMetadata(HttpMock.clientReturning(SampleIssuer.issuerMeta)),
            TypeMetadataPolicy.NotUsed,
        )
        try {
            verifier.verify(unverifiedSdJwt).getOrThrow()
        } catch (exception: SdJwtVerificationException) {
            val invalidJwt = assertIs<VerificationError.InvalidJwt>(exception.reason)
            val badJoseException = assertIs<BadJOSEException>(invalidJwt.cause)
            assertEquals("Signed JWT rejected: Another algorithm expected, or no matching key(s) found", badJoseException.message)
        }
    }

    @Test
    fun `SdJwtVcVerifier should verify an SD-JWT-VC that has been signed using an OctetKeyPair using Ed25519 curve and EdDSA algorithm`() =
        runTest {
            val key = OctetKeyPairGenerator(Curve.Ed25519).generate()

            val httpClient = HttpClient(MockEngine) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json()
                }
                engine {
                    addHandler { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("https://example.com/.well-known/jwt-vc-issuer", request.url.toString())

                        respond(
                            content = Json.encodeToString(
                                SdJwtVcIssuerMetadata(
                                    issuer = "https://example.com",
                                    jwks = Json.decodeFromString<JsonObject>(
                                        JSONObjectUtils.toJSONString(JWKSet(key.toPublicJWK()).toJSONObject()),
                                    ),
                                ),
                            ),
                            status = HttpStatusCode.OK,
                            headers = headers {
                                append(HttpHeaders.ContentType, ContentType.Application.Json)
                            },
                        )
                    }
                }
            }

            val sdJwt = run {
                val spec = sdJwt {
                    claim(RFC7519.ISSUER, "https://example.com")
                    claim(SdJwtVcSpec.VCT, "urn:credential:sample")
                }
                val signer = NimbusSdJwtOps.issuer(
                    signer = Ed25519Signer(key),
                    signAlgorithm = JWSAlgorithm.EdDSA,
                ) {
                    type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
                }
                signer.issue(spec).getOrThrow()
            }

            val verifier = DefaultSdJwtOps.SdJwtVcVerifier(
                IssuerVerificationMethod.usingIssuerMetadata(httpClient),
                TypeMetadataPolicy.NotUsed,
            )

            val serialized = with(NimbusSdJwtOps) { sdJwt.serialize() }
            verifier.verify(serialized).getOrThrow()
        }

    @Test
    fun `SdJwtVcVerifier ignores KeyId when using x5c as an IssuerJwkSource`() = runTest {
        val issuer = Url("https://issuer.example.com")
        val key = ECKeyGenerator(Curve.P_521)
            .algorithm(JWSAlgorithm.ES512)
            .keyID("issuer-signing-key#0")
            .generate()
        val certificate = run {
            val issuedAt = Clock.System.now()
            val expiresAt = issuedAt.plus(365.days)
            val subject = X500Principal("CN=${issuer.host}")
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(key.toECPrivateKey())
            val holder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(Random.nextLong()),
                Date.from(issuedAt.toJavaInstant()),
                Date.from(expiresAt.toJavaInstant()),
                subject,
                key.toECPublicKey(),
            ).addExtension(
                Extension.subjectAlternativeName,
                true,
                GeneralNames.getInstance(DERSequence(GeneralName(GeneralName.dNSName, issuer.host))),
            ).build(signer)
            X509CertUtils.parse(holder.encoded)
        }

        val sdJwt = run {
            val spec = sdJwt {
                claim(RFC7519.ISSUER, issuer.toString())
                claim(SdJwtVcSpec.VCT, "urn:credential:sample")
            }
            val signer = NimbusSdJwtOps.issuer(
                signer = ECDSASigner(key),
                signAlgorithm = JWSAlgorithm.ES512,
            ) {
                type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
                x509CertChain(listOf(com.nimbusds.jose.util.Base64.encode(certificate.encoded)))
                keyID("an-unrelated-key-id")
            }
            signer.issue(spec).getOrThrow()
        }

        val verifier = run {
            val x509CertificateTrust = X509CertificateTrust.usingVct { chain: List<X509Certificate>, _ ->
                1 == chain.size && certificate.serialNumber == chain.first().serialNumber
            }

            NimbusSdJwtOps.SdJwtVcVerifier(
                IssuerVerificationMethod.usingX5c(x509CertificateTrust),
                TypeMetadataPolicy.NotUsed,
            )
        }

        val serialized = with(NimbusSdJwtOps) { sdJwt.serialize() }
        verifier.verify(serialized).getOrThrow()
    }
}
