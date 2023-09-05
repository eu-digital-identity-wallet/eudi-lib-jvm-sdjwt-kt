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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
 * https://vcstuff.github.io/draft-terbu-sd-jwt-vc/draft-ietf-oauth-sd-jwt-vc-00/draft-ietf-oauth-sd-jwt-vc.html#I-D.looker-oauth-jwt-cwt-status-list
 */
class SdJwtVCIssuer(private val config: IssuerConfig) {

    fun issue(request: SdJwtVCIssuanceRequest): String {
        val now = now()
        val sdJwtSpec = request.verifiableCredentialAt(now) + request.standardClaimsAt(now)
        val issuedSdJwt = issuer.issue(sdJwtSpec).getOrThrow().also { it.prettyPrint({ jwt -> jwt.jwtClaimsSet.asClaims() }) }
        return issuedSdJwt.toCombinedIssuanceFormat { it.serialize() }
    }

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(config.clock.instant(), config.clock.zone)

    private fun SdJwtVCIssuanceRequest.verifiableCredentialAt(iat: ZonedDateTime): SdObject =
        verifiableCredential(iat)

    /**
     * According to SD-JWT VC there are some registered JWT claims
     * that must always be disclosable (plain claims).
     * Mandatory claims are: iss, iat, cnf
     * Optional claims are: sub, exp, nbe
     */
    private fun SdJwtVCIssuanceRequest.standardClaimsAt(iat: ZonedDateTime): SdObject =
        buildSdObject {
            iss(config.issuerName.toExternalForm())
            iat(iat.toInstant().epochSecond)
            cnf(holderPubKey)
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
            keyID(config.issuerKey.keyID)
            type(JOSEObjectType("vc+sd-jwt"))
        }
    }
}

//
// Example
//
//

data class Address(
    val streetAddress: String,
    val locality: String,
    val region: String,
    val country: String,
)

data class IdentityCredential(
    val givenName: String,
    val familyName: String,
    val email: String,
    val phoneNumber: String,
    val address: Address,
    val birthDate: LocalDate,
)

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

fun main() {
    val issuerKey = ECKeyGenerator(Curve.P_256).keyID("issuer-kid-0").generate()
    val config = IssuerConfig(issuerName = URL("https://example.com"), issuerKey = issuerKey)
    val issuingService = SdJwtVCIssuer(config)
    val holderKey = RSAKeyGenerator(2048).keyID("babis-kid-0").generate()
    val aliceIdentity = IdentityCredential(
        givenName = "Alice",
        familyName = "Routis",
        email = "alice@foo.com",
        phoneNumber = "+30-1111111",
        address = Address(
            streetAddress = "some street",
            locality = "some locality",
            region = "some region",
            country = "Wonderland",
        ),
        birthDate = LocalDate.of(1974, 2, 11),
    )

    //
    // Issue of SD-JWT according to SD-JWT VC
    //
    val request = SdJwtVCIssuanceRequest(
        type = "IdentityCredential",
        verifiableCredential = { iat -> aliceIdentity.asSdObjectAt(iat) },
        holderPubKey = holderKey.toPublicJWK(),
        expiresAt = { iat -> iat.plusYears(10).with(LocalTime.MIDNIGHT).toInstant() },
    )
    val issuedSdJwt: String = issuingService.issue(request).also { println(it) }

    //
    // Verify SD-JWT (as Holder)
    //
    val verified: SdJwt.Issuance<JwtAndClaims> = SdJwtVerifier.verifyIssuance(
        jwtSignatureVerifier = ECDSAVerifier(issuerKey.toECPublicKey()).asJwtVerifier(),
        unverifiedSdJwt = issuedSdJwt,
    ).getOrThrow()

    //
    // Recreate claims
    //
    verified.recreateClaims { it.second }.also {
        val jsonObj = JsonObject(it)
        println(json.encodeToString(jsonObj))
    }

    //
    // As Holder present is_over_18
    verified
        .present<JwtAndClaims, Nothing> { claim -> claim.name() == "is_over_18" }
        .also { x -> x.prettyPrint { it.second } }
}
