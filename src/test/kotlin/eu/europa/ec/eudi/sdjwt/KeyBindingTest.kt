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
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * This is an advanced test.
 *
 * It demonstrates the issuance, holder verification, holder presentation and presentation verification
 * use cases, including holder binding.
 *
 *
 */
class KeyBindingTest {

    private val issuer = IssuerActor(genKey("issuer"))
    private val holder = HolderActor(genKey("holder"))

    /**
     * This test focuses on the issuance
     *
     * It makes sure that [IssuerActor] is able to produce correctly an SD-JWT (issuance variation).
     * Furthermore, assures that the [IssuerActor.jwtVerifier] of the issuer successfully verifies the before-mentioned SD-JWT and
     * that [IssuerActor.extractHolderPubKey] is indeed able to extract holder pub key from SD-JWT claims
     */
    @Test
    fun testIssuance() {
        val emailCredential = SampleCredential(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        )

        val holderPubKey = holder.pubKey()
        val issuedSdJwt = issuer.issue(holderPubKey, emailCredential).also {
            println("Issued: ${it.serialize()}")
        }
        // Assert Disclosed claims
        val selectivelyDisclosedClaims =
            issuedSdJwt.recreateClaims { it.jwtClaimsSet.asClaims() }["credentialSubject"]?.jsonObject
                ?: JsonObject(emptyMap())

        assertEquals(4, selectivelyDisclosedClaims.size)
        assertEquals(emailCredential.givenName, selectivelyDisclosedClaims["given_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.familyName, selectivelyDisclosedClaims["family_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.email, selectivelyDisclosedClaims["email"]?.jsonPrimitive?.content)

        // Assert issuer verifier is able to verify JWT
        val jwtClaims = assertNotNull(issuer.jwtVerifier().verify(issuedSdJwt.jwt.serialize()).getOrNull())

        // Assert issuers pub key extractor is able to retrieve holder pub key
        val holderPubKeyExtractor = issuer.extractHolderPubKey()
        assertEquals(holderPubKey, holderPubKeyExtractor(jwtClaims))
    }

    @Test
    fun holderBindingFullTest() {
        val verifier = VerifierActor("Sample Verifier Actor") { claim -> claim.name() in listOf("email") }

        val emailCredential = SampleCredential(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        )

        // Issuer should know holder pub key, to included within SD-JWT

        val (hashAlg, issuedSdJwt) = issuer.issue(holder.pubKey(), emailCredential).run {
            val hashAlg = (jwt.jwtClaimsSet.getClaim("_sd_alg") as String).let { HashAlgorithm.fromString(it) }!!
            hashAlg to serialize()
        }

        // Holder should know, issuer pub key & signing algorithm to validate SD-JWT
        // Holder expects to find algorithm inside SD-JWT, header
        holder.storeCredential(issuer.jwtVerifier(), issuedSdJwt)

        // Holder must obtain a challenge from verifier to sign it as Key Binding JWT
        // Also Holder should know what verifier wants to be presented
        val verifierQuery = verifier.query()
        val presentedSdJwt: String = holder.present(hashAlg, verifierQuery)

        // Verifier should know/trust the issuer.
        // Also, Verifier should know how to obtain Holder Pub Key, from within SD-JWT
        verifier.acceptPresentation(
            issuer.jwtVerifier(),
            issuer.extractHolderPubKey(),
            presentedSdJwt,
        )
    }

    private fun genKey(kid: String) = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
}

/**
 * Domain model of credential
 */
@Serializable
data class SampleCredential(
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val email: String,
    val countries: List<String>,
)

@Serializable
data class VerifierChallenge(
    val nonce: String,
    val aud: String,
    val iat: Long,
) {
    fun asJson(): JsonObject = Json.encodeToJsonElement(this).jsonObject
}

@Serializable
data class VerifierQuery(val challenge: VerifierChallenge, val whatToDisclose: (Claim) -> Boolean)

/**
 * Sample issuer
 */
class IssuerActor(private val issuerKey: ECKey) {

    private val signAlgorithm = JWSAlgorithm.ES256
    private val jwtType = JOSEObjectType("example+sd-jwt")
    private val iss: String by lazy {
        "did:jwk:${JwtBase64.encode(issuerKey.toPublicJWK().toJSONString().encodeToByteArray())}"
    }
    private val expirationPeriod: Period = Period.ofMonths(12)

    /**
     * The [SdJwtIssuer]
     * It will be able to sign SD-JWT using [issuerKey] and [signAlgorithm]
     * Also, demonstrates the customization of the [JWSHeader] by adding
     * [jwtType] (as "typ" claim) and "kid" claim
     */
    private val sdJwtIssuer: SdJwtIssuer<SignedJWT> = SdJwtIssuer.nimbus(
        signer = ECDSASigner(issuerKey),
        signAlgorithm = signAlgorithm,
    ) {
        type(jwtType)
        keyID(issuerKey.keyID)
    }

    /**
     * This is an advanced [JwtSignatureVerifier] backed by Nimbus [DefaultJWTProcessor]
     * Verifies that the header of JWT contains typ claim equal to [jwtType].
     * Checks the signature of the JWT using issuers pub key and [signAlgorithm].
     * Makes sures that claims : "iss", "iat", "exp" and "cnf" are present
     */
    fun jwtVerifier(): JwtSignatureVerifier =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(jwtType)
            jwsKeySelector = JWSKeySelector { header, context ->
                val algorithm = header.algorithm
                val nestedSelector =
                    SingleKeyJWSKeySelector<SecurityContext>(algorithm, issuerKey.toECKey().toECPublicKey())
                nestedSelector.selectJWSKeys(header, context)
            }
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                JWTClaimsSet.Builder().build(),
                setOf("iss", "iat", "exp", "cnf"),
            )
        }.asJwtVerifier()

    /**
     * This is the main function of the issuer, which issues the SD-JWT
     * @param holderPubKey the holder pub key. It will be included in plain into SD-JWT, to leverage holder binding
     * @param credential the credential
     * @return the issued SD-JWT
     */
    fun issue(holderPubKey: JWK, credential: SampleCredential): SdJwt.Issuance<SignedJWT> {
        issuerDebug("Issuing new SD-JWT ...")
        val iat = Instant.now()
        val exp = iat.plus(expirationPeriod.days.toLong(), ChronoUnit.DAYS)
        val sdJwtElements =
            sdJwt {
                iss(iss)
                iat(iat.toEpochMilli())
                exp(exp.toEpochMilli())
                cnf(holderPubKey)
                structured("credentialSubject") {
                    sd(credential)
                }
            }
        return sdJwtIssuer.issue(sdElements = sdJwtElements).fold(
            onSuccess = { issued ->
                issuerDebug("Issued new SD-JWT")
                issued
            },
            onFailure = { exception ->
                issuerDebug("Failed to issue SD-JWT")
                throw exception
            },
        )
    }

    /**
     * This is a dual of [cnf] function
     * Obtains holder's pub key from claims
     *
     * @return the holder's pub key, if found
     */
    fun extractHolderPubKey(): (Claims) -> JWK? = HolderPubKeyInConfirmationClaim

    private fun issuerDebug(s: String) {
        println("Issuer: $s")
    }
}

/**
 * This is a sample holder capable of keeping a [credential][credentialSdJwt] issued by [IssuerActor]
 * and responding to [VerifierActor] query
 */
class HolderActor(private val holderKey: ECKey) {

    private val keyBindingSigningAlgorithm = JWSAlgorithm.ES256

    /**
     * Keeps the issued credential
     */
    private var credentialSdJwt: SdJwt.Issuance<Jwt>? = null
    fun pubKey(): ECKey = holderKey.toPublicJWK()

    fun storeCredential(issuerJwtSignatureVerifier: JwtSignatureVerifier, sdJwt: String) {
        holderDebug("Storing issued SD-JWT ...")
        SdJwtVerifier.verifyIssuance(issuerJwtSignatureVerifier, sdJwt).fold(
            onSuccess = { issued: SdJwt.Issuance<JwtAndClaims> ->
                credentialSdJwt = SdJwt.Issuance(issued.jwt.first, issued.disclosures)
                holderDebug("Stored SD-JWT")
            },
            onFailure = { exception ->
                holderDebug("Failed to store SD-JWT")
                throw exception
            },
        )
    }

    fun present(hashAlgorithm: HashAlgorithm, verifierQuery: VerifierQuery): String {
        holderDebug("Presenting credentials ...")

        val issuanceSdJwt = checkNotNull(credentialSdJwt)
        val jwt = issuanceSdJwt.jwt
        val disclosures = issuanceSdJwt.disclosures.filter { disclosure ->
            when (disclosure) {
                is Disclosure.ArrayElement -> true // TODO Figure out what to do
                is Disclosure.ObjectProperty -> verifierQuery.whatToDisclose(disclosure.claim())
            }
        }.toSet()

        val presentationSdJwt = SdJwt.Presentation(jwt, disclosures, null)
        val tempSerialize = presentationSdJwt.serialize { it }
        val sdJwtDigest = SdJwtDigest.digest(hashAlgorithm, tempSerialize).getOrThrow()
        val kbJwt = SignedJWT(
            JWSHeader.Builder(keyBindingSigningAlgorithm)
                .type(JOSEObjectType("kb+jwt"))
                .keyID(holderKey.keyID)
                .build(),
            with(JWTClaimsSet.Builder()) {
                audience(verifierQuery.challenge.aud)
                claim("nonce", verifierQuery.challenge.nonce)
                issueTime(Date.from(Instant.ofEpochSecond(verifierQuery.challenge.iat)))
                claim("_sd_hash", sdJwtDigest.value)
                build()
            },
        ).apply {
            sign(ECDSASigner(holderKey))
        }. serialize()

        return "$tempSerialize$kbJwt"
    }

    private fun holderDebug(s: String) {
        println("Holder: $s")
    }
}

class VerifierActor(private val clientId: String, private val whatToDisclose: (Claim) -> Boolean) {

    private var lastChallenge: JsonObject? = null
    private var presentation: SdJwt.Presentation<Jwt, Jwt>? = null
    fun query(): VerifierQuery = VerifierQuery(
        challenge = VerifierChallenge(
            nonce = Random.nextBytes(10).toString(),
            aud = clientId,
            iat = Instant.now().toEpochMilli(),
        ),
        whatToDisclose = whatToDisclose,
    ).also { lastChallenge = it.challenge.asJson() }

    fun acceptPresentation(
        issuerJwtSignatureVerifier: JwtSignatureVerifier,
        holderPubKeyExtractor: (Claims) -> JWK?,
        sdJwt: String,
    ) {
        val keyBindingVerifier = KeyBindingVerifier.mustBePresentAndValid(holderPubKeyExtractor, lastChallenge)
        SdJwtVerifier.verifyPresentation(
            jwtSignatureVerifier = issuerJwtSignatureVerifier,
            keyBindingVerifier = keyBindingVerifier,
            unverifiedSdJwt = sdJwt,
        ).fold(onSuccess = { presented: SdJwt.Presentation<JwtAndClaims, JwtAndClaims> ->
            presentation =
                SdJwt.Presentation(presented.jwt.first, presented.disclosures, presented.keyBindingJwt?.first)

            verifierDebug("Presentation accepted with SD Claims:")
        }, onFailure = { exception ->
            verifierDebug("Unable to verify presentation")
            throw exception
        })
    }

    private fun verifierDebug(s: String) {
        println("Verifier: $s")
    }
}
