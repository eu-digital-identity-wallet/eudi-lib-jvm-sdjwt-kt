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
package eu.europa.ec.eudi.sdjwt.vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps.asJwtVerifier
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps.serialize
import eu.europa.ec.eudi.sdjwt.dsl.def.DefinitionViolation
import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObjectBuilder
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.*

/**
 * Integration tests for [SdJwtVcVerifier].
 */
class SdJwtVcVerifierIntegrationTest {

    private val issuerKey =
        ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyID("iss#0")
            .keyUse(KeyUse.SIGNATURE)
            .generate()
    private val issuer = NimbusSdJwtOps.issuer(signer = ECDSASigner(issuerKey), signAlgorithm = JWSAlgorithm.ES256)
    private val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
        issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
        TypeMetadataPolicy.AlwaysRequired(
            resolveTypeMetadata = ResolveTypeMetadata(
                lookupTypeMetadata = { vct, _ ->
                    assertEquals("urn:eudi:pid:1", vct.value)
                    withContext(Dispatchers.IO) {
                        runCatchingCancellable {
                            Json.decodeFromString<SdJwtVcTypeMetadata>(loadResource("/pid_arf_v18.json"))
                        }
                    }
                },
            ),
        ),
        null,
    )

    private suspend fun issue(builder: SdJwtObjectBuilder.() -> Unit): String = issuer.issue(sdJwt { builder() }).getOrThrow().serialize()

    @Test
    fun verificationSuccess() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:pid:1")
            sdClaim("family_name", "Neal")
            sdClaim("given_name", "Tyler")
            sdArrClaim("nationalities") {
                sdClaim("AT")
            }
            sdObjClaim("address") {
                sdClaim("house_number", "101")
                sdClaim("street_address", "Trauner")
                sdClaim("locality", "Gemeinde Biberbach")
                sdClaim("region", "Lower Austria")
                sdClaim("postal_code", "3331")
                sdClaim("country", "AT")
            }
            sdClaim("personal_administrative_number", UUID.randomUUID().toString())
            sdClaim("sex", 1)
            sdClaim("email", "tyler.neal@example.com")
            sdClaim("document_number", UUID.randomUUID().toString())
            sdObjClaim("age_equal_or_over") {
                sdClaim("18", true)
            }
            sdClaim("age_in_years", 35)
        }
        verifier.verify(serialized).getOrThrow()
    }

    @Test
    fun verificationFailureWithTypeMetadataResolutionFailureDueSelectivelyDisclosedVct() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            sdClaim(SdJwtVcSpec.VCT, "urn:eudi:pid:1")
        }
        val exception =
            assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val sdJwtVcError = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val sdJwtVcVerificationError =
            assertIs<SdJwtVcVerificationError.TypeMetadataVerificationError.TypeMetadataResolutionFailure>(sdJwtVcError.error)
        assertIs<NullPointerException>(sdJwtVcVerificationError.cause)
    }

    @Test
    fun verificationFailureDueToTypeMetadataValidationFailure() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:pid:1")
            claim("family_name", "Neal")
            sdClaim("nationalities", "AT")
            objClaim("address") {
                sdClaim("house_number", "101")
            }
            objClaim("age_equal_or_over") {
                claim("18", true)
            }
        }
        val exception =
            assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val sdJwtVcError = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val sdJwtVcVerificationError =
            assertIs<SdJwtVcVerificationError.TypeMetadataVerificationError.TypeMetadataValidationFailure>(sdJwtVcError.error)
        val expectedErrors = listOf(
            ClaimPath.claim("address"),
            ClaimPath.claim("family_name"),
            ClaimPath.claim("age_equal_or_over"),
            ClaimPath.claim("age_equal_or_over").claim("18"),
        ).map { DefinitionViolation.IncorrectlyDisclosedClaim(it) } + DefinitionViolation.WrongClaimType(ClaimPath.claim("nationalities"))
        assertContentEquals(expectedErrors, sdJwtVcVerificationError.errors)
    }

    @Test
    fun documentIntegrityParsingSuccess() = runTest {
        val expectedVct = "urn:eudi:ehic:1"
        val expectedIntegrity = "sha256-LF4WHCjileMGyLqVmGTmOSdiDSCFOo4Nffq/NeXqstc="
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, expectedVct)
            claim(SdJwtVcSpec.VCT_INTEGRITY, expectedIntegrity)
        }

        var documentIntegrityParsed = false
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.Optional(
                resolveTypeMetadata = ResolveTypeMetadata(
                    lookupTypeMetadata = { vct, integrity ->
                        assertEquals(expectedVct, vct.value)
                        assertEquals(expectedIntegrity, integrity?.value)
                        documentIntegrityParsed = true
                        Result.success(null)
                    },
                ),
            ),
            null,
        )

        verifier.verify(serialized).getOrThrow()
        assertTrue(documentIntegrityParsed)
    }

    @Test
    fun `when status reference is not present, status resolver is not invoked`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
        }
        val checkStatus = CheckWithTokenStatusList { _, _ -> fail("CheckWithTokenStatusList should not have been invoked") }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        verifier.verify(serialized).getOrThrow()
    }

    @Test
    fun `when status reference is not a json object, verification fails`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
            claim(TokenStatusListSpec.STATUS, "status")
        }
        val checkStatus = CheckWithTokenStatusList { _, _ -> fail("CheckWithTokenStatusList should not have been invoked") }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        val exception = assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val error = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val reason = assertIs<SdJwtVcVerificationError.StatusVerificationError.StatusCheckFailure>(error.error)
        assertEquals("'status' claim must be a JsonObject", reason.message)
    }

    @Test
    fun `when status list reference is malformed, verification fails`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
            objClaim(TokenStatusListSpec.STATUS) {
                objClaim(TokenStatusListSpec.STATUS_LIST) {
                    claim(TokenStatusListSpec.INDEX, "index")
                    claim(TokenStatusListSpec.URI, 1)
                }
            }
        }
        val checkStatus = CheckWithTokenStatusList { _, _ -> fail("CheckWithTokenStatusList should not have been invoked") }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        val exception = assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val error = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val reason = assertIs<SdJwtVcVerificationError.StatusVerificationError.StatusCheckFailure>(error.error)
        assertEquals("'status' claim is malformed", reason.message)
    }

    @Test
    fun `when status resolution fails, verification fails`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
            objClaim(TokenStatusListSpec.STATUS) {
                objClaim(TokenStatusListSpec.STATUS_LIST) {
                    claim(TokenStatusListSpec.INDEX, 4)
                    claim(TokenStatusListSpec.URI, "https://example.com/status_list/10")
                }
            }
        }
        val checkStatus = CheckWithTokenStatusList { uri, index ->
            assertEquals("https://example.com/status_list/10", uri)
            assertEquals(4u, index)
            error("resolution failed")
        }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        val exception = assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val error = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val reason = assertIs<SdJwtVcVerificationError.StatusVerificationError.StatusCheckFailure>(error.error)
        assertIs<IllegalStateException>(reason.cause)
        assertEquals("resolution failed", reason.cause.message)
    }

    @Test
    fun `when status is non-valid, verification fails`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
            objClaim(TokenStatusListSpec.STATUS) {
                objClaim(TokenStatusListSpec.STATUS_LIST) {
                    claim(TokenStatusListSpec.INDEX, 4)
                    claim(TokenStatusListSpec.URI, "https://example.com/status_list/10")
                }
            }
        }
        val checkStatus = CheckWithTokenStatusList { uri, index ->
            assertEquals("https://example.com/status_list/10", uri)
            assertEquals(4u, index)
            Status.NonValid(0x01u, "revoked")
        }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        val exception = assertFailsWith<SdJwtVerificationException> { verifier.verify(serialized).getOrThrow() }
        val error = assertIs<VerificationError.SdJwtVcError>(exception.reason)
        val reason = assertIs<SdJwtVcVerificationError.StatusVerificationError.NonValidStatus>(error.error)
        assertEquals(0x01u, reason.status.status)
    }

    @Test
    fun `when status is valid, verification succeeds`() = runTest {
        val serialized = issue {
            claim(RFC7519.ISSUER, "https://example.com/issuer")
            claim(SdJwtVcSpec.VCT, "urn:eudi:ehic:1")
            objClaim(TokenStatusListSpec.STATUS) {
                objClaim(TokenStatusListSpec.STATUS_LIST) {
                    claim(TokenStatusListSpec.INDEX, 4)
                    claim(TokenStatusListSpec.URI, "https://example.com/status_list/10")
                }
            }
        }
        val checkStatus = CheckWithTokenStatusList { uri, index ->
            assertEquals("https://example.com/status_list/10", uri)
            assertEquals(4u, index)
            Status.Valid
        }
        val verifier = NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingCustom(ECDSAVerifier(issuerKey).asJwtVerifier()),
            TypeMetadataPolicy.NotUsed,
            checkStatus,
        )
        verifier.verify(serialized).getOrThrow()
    }
}
