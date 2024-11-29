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
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.LocalDate

private data class IssuerConfig(
    val issuer: URI,
    val hashAlgorithm: HashAlgorithm,
    val issuerKey: ECKey,
    val signAlg: JWSAlgorithm,
    val vct: URI,
)

private class SdJwtVCIssuer(val config: IssuerConfig) {

    fun issue(holderData: IdentityCredential, holderPubKey: JWK): SdJwt.Issuance<SignedJWT> {
        val sdJwtSpec = holderData.sdJwtSpec(
            holderPubKey,
            iat = Instant.ofEpochSecond(1683000000),
            exp = Instant.ofEpochSecond(1883000000),
        )
        return issuer.issue(sdJwtSpec).getOrThrow()
    }

    private fun IdentityCredential.sdJwtSpec(
        holderPubKey: JWK,
        iat: Instant,
        exp: Instant? = null,
    ): SdObject =
        sdJwt {
            //
            // Never Selectively Disclosable claims
            //
            iss(config.issuer.toASCIIString())
            plain(SdJwtVcSpec.VCT, config.vct.toASCIIString())
            iat(iat.epochSecond)
            exp?.let { exp(it.epochSecond) }
            cnf(holderPubKey)

            //
            // Always Selectively disclosable claims
            //
            sd {
                put("given_name", givenName)
                put("family_name", familyName)
                put("email", email)
                put("phone_number", phoneNumber)
                putJsonObject("address") {
                    put("street_address", address.streetAddress)
                    put("locality", address.locality)
                    put("region", address.region)
                    put("country", address.country)
                }
                put("birth_date", birthDate.toString())
                put("is_over_18", isOver18)
                put("is_over_21", isOver21)
                put("is_over_65", isOver65)
            }
        }

    private val issuer: SdJwtIssuer<SignedJWT> by lazy {
        val sdJwtFactory = SdJwtFactory(hashAlgorithm = config.hashAlgorithm)
        val signer = ECDSASigner(config.issuerKey)
        SdJwtIssuer.Companion.nimbus(sdJwtFactory, signer, config.signAlg) {
            keyID(config.issuerKey.keyID)
            type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
        }
    }
}

private val HolderKey = ECKey.parse(
    """
        {
          "kty" : "EC",
          "crv" : "P-256",
          "x"   : "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
          "y"   : "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ",
          "d"   : "5K5SCos8zf9zRemGGUl6yfok-_NiiryNZsvANWMhF-I"
        }
    """.trimIndent(),
)

private val JohnDoe = IdentityCredential(
    givenName = "John",
    familyName = "Doe",
    email = "johndoe@example.com",
    phoneNumber = "+1-202-555-0101",
    address = Address(
        streetAddress = "123 Main St",
        locality = "Anytown",
        region = "Anystate",
        country = "US",
    ),
    birthDate = LocalDate.of(1940, 1, 1),
    isOver18 = true,
    isOver21 = true,
    isOver65 = true,
)

private val IssuerSampleCfg = IssuerConfig(
    issuer = URI.create("https://example.com/issuer"),
    issuerKey = ECKey.parse(
        """
        {
          "kty" : "EC",
          "crv" : "P-256",
          "x"   : "b28d4MwZMjw8-00CG4xfnn9SLMVMM19SlqZpVb_uNtQ",
          "y"   : "Xv5zWwuoaTgdS6hV43yI6gBwTnjukmFQQnJ_kCxzqk8",
          "d"   : "Ur2bNKuBPOrAaxsRnbSH6hIhmNTxSGXshDSUD1a1y7g",
          "kid" : "doc-signer-05-25-2022"
        }
        """.trimIndent(),
    ),

    hashAlgorithm = HashAlgorithm.SHA_256,
    signAlg = JWSAlgorithm.ES256,
    vct = URI.create("https://credentials.example.com/identity_credential"),
)

class SdJwtVcIssuanceTest {

    private val issuingService = SdJwtVCIssuer(IssuerSampleCfg)

    @Test
    fun `issued SD-JWT must contain JWT claims type, iat, iss, sub`() = runTest {
        //
        // Issue of SD-JWT according to SD-JWT VC
        //
        val issuedSdJwt: SdJwt.Issuance<SignedJWT> = issuingService.issue(JohnDoe, HolderKey.toPublicJWK())
        issuedSdJwt.print()
        issuedSdJwt.printInJwsJson(JwsSerializationOption.Flattened)
        verify(issuedSdJwt.serialize())
    }

    private fun SdJwt.Issuance<SignedJWT>.print() {
        prettyPrint { it.jwtClaimsSet.asClaims() }
    }

    private fun SdJwt.Issuance<SignedJWT>.printInJwsJson(option: JwsSerializationOption) {
        val jwsJson = serializeAsJwsJson(option)
        println(json.encodeToString(jwsJson))
    }

    //
    // Verify SD-JWT (as Holder)
    //

    private suspend fun verify(issuedSdJwtStr: String) {
        val sdJwtVcVerifier = SdJwtVcVerifier.usingIssuerMetadata {
            val jwksAsJson = JWKSet(issuingService.config.issuerKey.toPublicJWK()).toString()
            val issuerMetadata = SdJwtVcIssuerMetadata(
                issuer = issuingService.config.issuer,
                jwks = Json.parseToJsonElement(jwksAsJson).jsonObject,
            )
            HttpMock.clientReturning(issuerMetadata)
        }

        val verified: SdJwt.Issuance<JwtAndClaims> = sdJwtVcVerifier.verifyIssuance(issuedSdJwtStr).getOrThrow()

        // Check Header
        val jwsHeader = SignedJWT.parse(verified.jwt.first).header
        assertEquals(issuingService.config.issuerKey.keyID, jwsHeader.keyID)
        assertEquals(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT), jwsHeader.type)

        // Check claims
        val claims = verified.jwt.second
        assertEquals(issuingService.config.vct.toASCIIString(), claims[SdJwtVcSpec.VCT]?.jsonPrimitive?.contentOrNull)
        assertEquals(issuingService.config.issuer.toASCIIString(), claims[RFC7519.ISSUER]?.jsonPrimitive?.contentOrNull)
        assertNotNull(claims[RFC7519.ISSUED_AT]?.jsonPrimitive)
        assertNotNull(claims["cnf"]?.jsonObject)
    }
}

//
// Data model
//

private data class IdentityCredential(
    val givenName: String,
    val familyName: String,
    val email: String,
    val phoneNumber: String,
    val address: Address,
    val birthDate: LocalDate,
    val isOver18: Boolean,
    val isOver21: Boolean,
    val isOver65: Boolean,
)

private data class Address(
    val streetAddress: String,
    val locality: String,
    val region: String,
    val country: String,
)
