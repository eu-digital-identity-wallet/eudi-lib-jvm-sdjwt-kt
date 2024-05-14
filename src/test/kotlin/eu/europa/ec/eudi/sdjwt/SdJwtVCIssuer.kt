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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.net.URL
import java.time.*
import java.time.temporal.ChronoField

typealias TimeDependant<F> = (ZonedDateTime) -> F

data class IssuerConfig(
    val issuerName: URL,
    val clock: Clock = Clock.systemDefaultZone(),
    val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
    val issuerKey: ECKey,
    val signAlg: JWSAlgorithm = JWSAlgorithm.ES256,
)

/**
 * Represents a request placed to the issuer, for issuing an SD-JWT
 * @param type The type of the credential
 * @param subject The subject of the SD-JWT. If provided, it will populate the `sub` JWT claim (always disclosable)
 * @param verifiableCredential function that given a point in time returns the verifiable credential expressed
 * as a [SD-JWT specification][SdObject]
 * @param holderPubKey the public key of the holder. Will be included as an always disclosable claim under `cnf`
 * @param expiresAt a function that given a point in time (`iat`) returns the expiration time. If provided,
 * it will be used to populate the `exp` JWT Claim (always disclosable)
 * @param notUseBefore a function that given a point in time (`iat`) returns the "not use before" time. If provided,
 *  * it will be used to populate the `nbf` JWT Claim (always disclosable)
 */
data class SdJwtVCIssuanceRequest(
    val type: String,
    val subject: String? = null,
    val verifiableCredential: TimeDependant<SdObject>,
    val holderPubKey: JWK,
    val expiresAt: TimeDependant<Instant>? = null,
    val notUseBefore: TimeDependant<Instant>? = null,
)

/**
 * An SD-JWT issuer according to SD-JWT VC
 *
 *
 * See [SD-JWT-VC](https://vcstuff.github.io/draft-terbu-sd-jwt-vc/draft-ietf-oauth-sd-jwt-vc-00/draft-ietf-oauth-sd-jwt-vc.html)
 */
class SdJwtVCIssuer(private val config: IssuerConfig) {

    fun issue(request: SdJwtVCIssuanceRequest): String {
        val now = now()
        val sdJwtSpec = request.verifiableCredentialAt(now) + request.standardClaimsAt(now)
        val issuedSdJwt =
            issuer.issue(sdJwtSpec).getOrThrow().also { it.prettyPrint { jwt -> jwt.jwtClaimsSet.asClaims() } }
        return issuedSdJwt.serialize()
    }

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(config.clock.instant(), config.clock.zone)

    private fun SdJwtVCIssuanceRequest.verifiableCredentialAt(iat: ZonedDateTime): SdObject =
        verifiableCredential(iat)

    /**
     * According to SD-JWT-VC,there are some registered JWT claims
     * that must always be disclosable (plain claims).
     * Mandatory claims are: `type`, `iss`, `iat`, `cnf`
     * Optional claims are: `sub`, `exp`, `nbf`
     *
     * **See** [here](https://vcstuff.github.io/draft-terbu-sd-jwt-vc/draft-ietf-oauth-sd-jwt-vc-00/draft-ietf-oauth-sd-jwt-vc.html#name-registered-jwt-claims)
     */
    private fun SdJwtVCIssuanceRequest.standardClaimsAt(iat: ZonedDateTime): SdObject =
        buildSdObject {
            plain {
                put("type", type)
                iss(config.issuerName.toExternalForm())
                iat(iat.toInstant().epochSecond)
                subject?.let { sub(it) }
                expiresAt?.let { provider ->
                    val exp = provider(iat)
                    require(exp.epochSecond > iat.toInstant().epochSecond) { "exp should be after iat" }
                    exp(exp.epochSecond)
                }
                notUseBefore?.let { calculateNbf ->
                    val nbf = calculateNbf(iat)
                    require(nbf.epochSecond > iat.toInstant().epochSecond) { "nbe should be after iat" }
                    nbf(nbf.epochSecond)
                }
            }
            cnf(holderPubKey)
        }

    /**
     * Creates a Nimbus-based SD-JWT issuer
     * according to the requirements of SD-JWT VC
     * - No decoys
     * - JWS header kid should contain the id of issuer's key
     * - JWS header typ should contain value "vs+sd-jwt"
     * In addition the issuer will use the [config] to select
     * [HashAlgorithm], [JWSAlgorithm] and [issuer's key][ECKey]
     */
    private val issuer: SdJwtIssuer<SignedJWT> by lazy {
        // SD-JWT VC requires no decoys

        val sdJwtFactory = SdJwtFactory(hashAlgorithm = config.hashAlgorithm, numOfDecoysLimit = 0)
        val signer = ECDSASigner(config.issuerKey)
        SdJwtIssuer.nimbus(sdJwtFactory, signer, config.signAlg) {
            // SD-JWT VC requires the kid & typ header attributes
            // Check [here](https://vcstuff.github.io/draft-terbu-sd-jwt-vc/draft-ietf-oauth-sd-jwt-vc-00/draft-ietf-oauth-sd-jwt-vc.html#name-header-parameters)
            keyID(config.issuerKey.keyID)
            type(JOSEObjectType("vc+sd-jwt"))
        }
    }
}

//
// Example Usage
//
//

class SdJwtVCIssuerTest {

    private val fixedClock: Clock = Clock.fixed(Instant.ofEpochSecond(1683000000), Clock.systemDefaultZone().zone)
    private val issuerCfg = IssuerConfig(
        issuerName = URL("https://example.com/issuer"),
        issuerKey = ECKeyGenerator(Curve.P_256).keyID("issuer-kid-0").generate(),
        clock = fixedClock,
    )
    private val issuingService = SdJwtVCIssuer(issuerCfg)
    private val holderKey = RSAKeyGenerator(2048).keyID("johnDoe-kid-0").generate()
    private val johnDoeIdentity = IdentityCredential(
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
    )

    @Test
    fun `issued SD-JWT must contain JWT claims type, iat, iss, sub`() = runTest {
        //
        // Issue of SD-JWT according to SD-JWT VC
        //
        val request = SdJwtVCIssuanceRequest(
            type = "IdentityCredential",
            verifiableCredential = { iat -> johnDoeIdentity.asSdObjectAt(iat) },
            holderPubKey = holderKey.toPublicJWK(),
            expiresAt = { iat -> iat.plusYears(2).with(LocalTime.MIDNIGHT).toInstant() },
        )
        val issuedSdJwt: String = issuingService.issue(request).also { println(it) }

        //
        // Verify SD-JWT (as Holder)
        //
        val verified: SdJwt.Issuance<JwtAndClaims> =
            Assertions.assertDoesNotThrow(
                ThrowingSupplier {
                    runBlocking {
                        SdJwtVerifier.verifyIssuance(
                            jwtSignatureVerifier = ECDSAVerifier(issuerCfg.issuerKey.toECPublicKey()).asJwtVerifier(),
                            unverifiedSdJwt = issuedSdJwt,
                        ).getOrThrow()
                    }
                },
            )

        // Check Header
        val jwsHeader = Assertions.assertDoesNotThrow(
            ThrowingSupplier {
                SignedJWT.parse(verified.jwt.first).header
            },
        )
        assertEquals(issuerCfg.issuerKey.keyID, jwsHeader.keyID)
        assertEquals(JOSEObjectType("vc+sd-jwt"), jwsHeader.type)

        // Check claims
        val claims = verified.jwt.second
        assertEquals(request.type, claims["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(issuerCfg.issuerName.toExternalForm(), claims["iss"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(claims["iat"]?.jsonPrimitive)
        assertNotNull(claims["exp"]?.jsonPrimitive)
        assertNotNull(claims["cnf"]?.jsonObject)
    }
}

data class Address(
    val streetAddress: String,
    val locality: String,
    val region: String,
    val country: String,
)

/**
 * A class that represents some kind of credential data
 */
data class IdentityCredential(
    val givenName: String,
    val familyName: String,
    val email: String,
    val phoneNumber: String,
    val address: Address,
    val birthDate: LocalDate,
)

/**
 * A function (time dependant) that maps the [IdentityCredential]
 * into a [SD-JWT specification][SdObject].
 *
 * Basically, it reflects the issuer's decision of which claims are always or selectively disclosable
 */
fun IdentityCredential.asSdObjectAt(iat: ZonedDateTime): SdObject =
    sdJwt {
        sd {
            put("given_name", givenName)
            put("family_name", familyName)
            put("email", email)
            putJsonObject("address") {
                put("street_address", address.streetAddress)
                put("locality", address.locality)
                put("region", address.region)
                put("country", address.country)
            }
            put("birth_date", birthDate.toString())
            //
            // Claims that depend on issuance time
            //
            val age = iat.year - birthDate.get(ChronoField.YEAR)
            put("is_over_18", age >= 18)
            put("is_over_21", age >= 21)
            put("is_over_65", age >= 65)
        }
    }
