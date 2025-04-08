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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private data class IssuerConfig(
    val issuer: URI,
    val hashAlgorithm: HashAlgorithm,
    val issuerKey: ECKey,
    val signAlg: JWSAlgorithm,
    val vct: URI,
)

private class SdJwtVCIssuer(val config: IssuerConfig) {

    suspend fun issue(holderData: IdentityCredential, holderPubKey: JWK): SdJwt<SignedJWT> {
        val sdJwtSpec = holderData.sdJwtSpec(
            holderPubKey,
            iat = Instant.fromEpochSeconds(1683000000),
            exp = Instant.fromEpochSeconds(1883000000),
        )
        return issuer.issue(sdJwtSpec).getOrThrow()
    }

    private fun IdentityCredential.sdJwtSpec(
        holderPubKey: JWK,
        iat: Instant,
        exp: Instant? = null,
    ): DisclosableObject =
        sdJwt {
            //
            // Never Selectively Disclosable claims
            //
            claim("iss", config.issuer.toASCIIString())
            claim(SdJwtVcSpec.VCT, config.vct.toASCIIString())
            claim("iat", iat.epochSeconds)
            exp?.let { claim("exp", it.epochSeconds) }
            cnf(holderPubKey)

            //
            // Always Selectively disclosable claims
            //
            sdClaim("given_name", givenName)
            sdClaim("family_name", familyName)
            sdClaim("email", email)
            sdClaim("phone_number", phoneNumber)
            sdObjClaim("address") {
                claim("street_address", address.streetAddress)
                claim("locality", address.locality)
                claim("region", address.region)
                claim("country", address.country)
            }
            sdClaim("birth_date", birthDate.toString())
            sdClaim("is_over_18", isOver18)
            sdClaim("is_over_21", isOver21)
            sdClaim("is_over_65", isOver65)
        }

    private val issuer: SdJwtIssuer<SignedJWT> by lazy {
        val sdJwtFactory = SdJwtFactory(hashAlgorithm = config.hashAlgorithm)
        val signer = ECDSASigner(config.issuerKey)
        NimbusSdJwtOps.issuer(sdJwtFactory, signer, config.signAlg) {
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
    birthDate = LocalDate(1940, 1, 1),
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

    val sdJwtVcVerifier = DefaultSdJwtOps.SdJwtVcVerifier.usingIssuerMetadata {
        val jwksAsJson = JWKSet(issuingService.config.issuerKey.toPublicJWK()).toString()
        val issuerMetadata = SdJwtVcIssuerMetadata(
            issuer = issuingService.config.issuer,
            jwks = Json.parseToJsonElement(jwksAsJson).jsonObject,
        )
        HttpMock.clientReturning(issuerMetadata)
    }

    @Test
    fun `issued SD-JWT must contain JWT claims type, iat, iss, sub`() = runTest {
        //
        // Issue of SD-JWT according to SD-JWT VC
        //
        val issuedSdJwt: SdJwt<SignedJWT> = issuingService.issue(JohnDoe, HolderKey.toPublicJWK())
        issuedSdJwt.print()
        issuedSdJwt.printInJwsJson(JwsSerializationOption.Flattened)
        val serialized = with(NimbusSdJwtOps) { issuedSdJwt.serialize() }
        verify(serialized)
    }

    @Test
    fun issued() = runTest {
        val unverified = """
            eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImRjK3NkLWp3dCIsICJraWQiOiAiZG9jLXNp
            Z25lci0wNS0yNS0yMDIyIn0.eyJfc2QiOiBbIjA5dktySk1PbHlUV00wc2pwdV9wZE9C
            VkJRMk0xeTNLaHBINTE1blhrcFkiLCAiMnJzakdiYUMwa3k4bVQwcEpyUGlvV1RxMF9k
            YXcxc1g3NnBvVWxnQ3diSSIsICJFa084ZGhXMGRIRUpidlVIbEVfVkNldUM5dVJFTE9p
            ZUxaaGg3WGJVVHRBIiwgIklsRHpJS2VpWmREd3BxcEs2WmZieXBoRnZ6NUZnbldhLXNO
            NndxUVhDaXciLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQ
            WWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJ
            IiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAi
            amRyVEU4WWNiWTRFaWZ1Z2loaUFlX0JQZWt4SlFaSUNlaVVRd1k5UXF4SSIsICJqc3U5
            eVZ1bHdRUWxoRmxNXzNKbHpNYVNGemdsaFFHMERwZmF5UXdMVUs0Il0sICJpc3MiOiAi
            aHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiLCAiaWF0IjogMTY4MzAwMDAwMCwgImV4
            cCI6IDE4ODMwMDAwMDAsICJ2Y3QiOiAiaHR0cHM6Ly9jcmVkZW50aWFscy5leGFtcGxl
            LmNvbS9pZGVudGl0eV9jcmVkZW50aWFsIiwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJj
            bmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRD
            QUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJa
            eGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ.cWT4V
            Ms1G0iqUt-ajx98dCwq0I4djdqC9vX6ELCpjYBNrhRNK6u3wds9cSwB8REuA1RRCE9Bp
            rDDyjOVDLgLvg~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLC
            AiSm9obiJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgI
            kRvZSJd~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VA
            ZXhhbXBsZS5jb20iXQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251b
            WJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIi
            wgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2
            FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOi
            AiVVMifV0~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImJpcnRoZGF0ZSIsICIxOT
            QwLTAxLTAxIl0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImlzX292ZXJfMTgiLC
            B0cnVlXQ~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgImlzX292ZXJfMjEiLCB0cnV
            lXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgImlzX292ZXJfNjUiLCB0cnVlXQ~
        """.trimIndent().removeNewLine()

        val sdJwt = sdJwtVcVerifier.verify(unverified).getOrThrow()
        val jwsJson = with(DefaultSdJwtOps) {
            sdJwt.asJwsJsonObject(JwsSerializationOption.Flattened)
        }
        println(json.encodeToString(jwsJson))
    }

    @Test
    fun presented() = runTest {
        val unverified =
            """
                eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImRjK3NkLWp3dCIsICJraWQiOiAiZG9jLXNp
                Z25lci0wNS0yNS0yMDIyIn0.eyJfc2QiOiBbIjA5dktySk1PbHlUV00wc2pwdV9wZE9C
                VkJRMk0xeTNLaHBINTE1blhrcFkiLCAiMnJzakdiYUMwa3k4bVQwcEpyUGlvV1RxMF9k
                YXcxc1g3NnBvVWxnQ3diSSIsICJFa084ZGhXMGRIRUpidlVIbEVfVkNldUM5dVJFTE9p
                ZUxaaGg3WGJVVHRBIiwgIklsRHpJS2VpWmREd3BxcEs2WmZieXBoRnZ6NUZnbldhLXNO
                NndxUVhDaXciLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQ
                WWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJ
                IiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAi
                amRyVEU4WWNiWTRFaWZ1Z2loaUFlX0JQZWt4SlFaSUNlaVVRd1k5UXF4SSIsICJqc3U5
                eVZ1bHdRUWxoRmxNXzNKbHpNYVNGemdsaFFHMERwZmF5UXdMVUs0Il0sICJpc3MiOiAi
                aHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiLCAiaWF0IjogMTY4MzAwMDAwMCwgImV4
                cCI6IDE4ODMwMDAwMDAsICJ2Y3QiOiAiaHR0cHM6Ly9jcmVkZW50aWFscy5leGFtcGxl
                LmNvbS9pZGVudGl0eV9jcmVkZW50aWFsIiwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJj
                bmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRD
                QUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJa
                eGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ.cWT4V
                Ms1G0iqUt-ajx98dCwq0I4djdqC9vX6ELCpjYBNrhRNK6u3wds9cSwB8REuA1RRCE9Bp
                rDDyjOVDLgLvg~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgImlzX292ZXJfNjUiLC
                B0cnVlXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgImFkZHJlc3MiLCB7InN0cmV
                ldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCA
                icmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~eyJhbGciOiAiRVM
                yNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZC
                I6ICJodHRwczovL2V4YW1wbGUuY29tL3ZlcmlmaWVyIiwgImlhdCI6IDE3MzE1MzA3MD
                QsICJzZF9oYXNoIjogImNUNkRmMXRCODNxM3EtQVFjdGYxVXFncXBhaW5BOVFSNmVmS3
                NQdFBFQ2cifQ.fp4Dgu3k1oU09BHnP7U2aU2v_z96JSi8T1T7f47qUW5ypQsh_39F1S4
                EOtnT09YNGp9nZbETjor3nCzM0J0MvQ
            """.trimIndent().removeNewLine()

        val (sdJwt, kbJwtAndClaims) = sdJwtVcVerifier.verify(unverified, null).getOrThrow()
        val (kbJwt, kbJwtClaims) = assertNotNull(kbJwtAndClaims)

        println(json.encodeToString(JsonObject(kbJwtClaims)))

        with(DefaultSdJwtOps) {
            val jwsJson =
                sdJwt.asJwsJsonObjectWithKeyBinding(JwsSerializationOption.Flattened, kbJwt)

            println(json.encodeToString(jwsJson))
        }
    }

    private fun SdJwt<SignedJWT>.print() {
        prettyPrint { it.jwtClaimsSet.jsonObject() }
    }

    private fun SdJwt<SignedJWT>.printInJwsJson(option: JwsSerializationOption) {
        with(NimbusSdJwtOps) {
            val jwsJson = asJwsJsonObject(option)
            println(json.encodeToString(jwsJson))
        }
    }

    //
    // Verify SD-JWT (as Holder)
    //

    private suspend fun verify(issuedSdJwtStr: String) {
        val verified: SdJwt<JwtAndClaims> = sdJwtVcVerifier.verify(issuedSdJwtStr).getOrThrow()

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
