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
import com.nimbusds.jose.jwk.AsymmetricJWK
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.DefaultHttpClientFactory
import eu.europa.ec.eudi.sdjwt.vc.LookupPublicKeysFromDIDDocument
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This is an advanced test.
 *
 * It demonstrates the issuance, holder verification, holder presentation and presentation verification
 * use cases, including key binding.
 */
class KeyBindingTest {

    private val issuer = IssuerActor(genKey("issuer"))
    private val lookup = LookupPublicKeysFromDIDDocument { _, _ -> listOf(issuer.issuerKey.toPublicJWK()) }
    private val verifier = SdJwtVcVerifier(httpClientFactory = DefaultHttpClientFactory, lookup = lookup)
    private val holder = HolderActor(genKey("holder"), lookup)

    /**
     * This test focuses on the issuance
     *
     * It makes sure that [IssuerActor] is able to produce correctly an SD-JWT (issuance variation).
     * Furthermore, assures that the [verifier] successfully verifies the before-mentioned SD-JWT and
     * that [HolderPubKeyInConfirmationClaim] is indeed able to extract holder pub key from SD-JWT claims
     */
    @Test
    fun testIssuance() = runTest {
        val emailCredential = SampleCredential(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        )

        val holderPubKey = holder.pubKey()
        val issuedSdJwtStr = issuer.issue(holderPubKey, emailCredential).also {
            println("Issued: $it")
        }

        val issuedSdJwt = SdJwt.unverifiedIssuanceFrom(issuedSdJwtStr).getOrThrow()
        // Assert Disclosed claims
        val selectivelyDisclosedClaims =
            with(DefaultSdJwtOps) {
                val (claims, _) = issuedSdJwt.recreateClaimsAndDisclosuresPerClaim()
                claims["credentialSubject"]?.jsonObject ?: JsonObject(emptyMap())
            }

        assertEquals(5, selectivelyDisclosedClaims.size)
        assertEquals(emailCredential.givenName, selectivelyDisclosedClaims["given_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.familyName, selectivelyDisclosedClaims["family_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.email, selectivelyDisclosedClaims["email"]?.jsonPrimitive?.content)

        // Assert issuer verifier is able to verify JWT
        val jwtClaims = assertNotNull(verifier.verifyIssuance(issuedSdJwtStr).getOrNull())

        // Extract and verify holder public key
        assertEquals(holderPubKey, HolderPubKeyInConfirmationClaim(jwtClaims.jwt.second))
    }

    @Test
    fun holderBindingFullTest() = runTest {
        suspend fun test(whatToDisclose: Set<ClaimPath>, expectedNumberOfDisclosures: Int) {
            val verifier = VerifierActor("Sample Verifier Actor", whatToDisclose, expectedNumberOfDisclosures, lookup)

            val emailCredential = SampleCredential(
                givenName = "John",
                familyName = "Doe",
                email = "john@foobar.com",
                countries = listOf("GR", "DE"),
            )

            // Issuer should know holder's public key

            val issuedSdJwt = issuer.issue(holder.pubKey(), emailCredential)

            // Holder should know, issuer pub key & signing algorithm to validate SD-JWT
            // Holder expects to find algorithm inside SD-JWT, header
            holder.storeCredential(issuedSdJwt)

            // Holder must obtain a challenge from verifier to sign it as Key Binding JWT
            // Also Holder should know what verifier wants to be presented
            val verifierQuery = verifier.query()
            val presentedSdJwt: String = holder.present(verifierQuery)

            // Verifier should know/trust the issuer.
            // Also, Verifier should know how to obtain Holder Pub Key, from within SD-JWT
            verifier.acceptPresentation(presentedSdJwt)
        }

        val testData: Map<Set<ClaimPath>, Int> = mapOf(
            setOf(
                ClaimPath.claim("credentialSubject").claim("email"),
                ClaimPath.claim("credentialSubject").claim("countries"),
                ClaimPath.claim("credentialSubject").claim("addresses"),
            ) to 3,

            setOf(
                ClaimPath.claim("credentialSubject").claim("email"),
                ClaimPath.claim("credentialSubject").claim("countries"),
                ClaimPath.claim("credentialSubject").claim("addresses").arrayElement(0),
            ) to 4,

            setOf(
                ClaimPath.claim("credentialSubject").claim("email"),
                ClaimPath.claim("credentialSubject").claim("countries"),
                ClaimPath.claim("credentialSubject").claim("addresses").arrayElement(0).claim("street"),
            ) to 5,

            setOf(
                ClaimPath.claim("credentialSubject").claim("email"),
                ClaimPath.claim("credentialSubject").claim("countries"),
                ClaimPath.claim("credentialSubject").claim("addresses").allArrayElements(),
            ) to 5,

            setOf(
                ClaimPath.claim("credentialSubject").claim("email"),
                ClaimPath.claim("credentialSubject").claim("countries"),
                ClaimPath.claim("credentialSubject").claim("addresses").allArrayElements().claim("street"),
            ) to 7,
        )

        testData.forEach { (whatToDisclose, expectedNumberOfDisclosures) ->
            test(whatToDisclose, expectedNumberOfDisclosures)
        }
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

    companion object {
        operator fun invoke(nonce: String, aud: String, iat: Instant) = VerifierChallenge(nonce, aud, iat.epochSecond)
    }
}

@Serializable
data class VerifierQuery(val challenge: VerifierChallenge, val whatToDisclose: Set<ClaimPath>)

/**
 * Sample issuer
 */
class IssuerActor(val issuerKey: ECKey) : NimbusSdJwtOps {

    private val signAlgorithm = JWSAlgorithm.ES256
    private val jwtType = JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT)
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
    private val sdJwtIssuer: SdJwtIssuer<SignedJWT> =
        NimbusSdJwtOps.issuer(signer = ECDSASigner(issuerKey), signAlgorithm = signAlgorithm) {
            type(jwtType)
        }

    /**
     * This is the main function of the issuer, which issues the SD-JWT
     * @param holderPubKey the holder pub key. It will be included in plain in SD-JWT, to leverage key binding
     * @param credential the credential
     * @return the issued SD-JWT
     */
    suspend fun issue(holderPubKey: AsymmetricJWK, credential: SampleCredential): String {
        issuerDebug("Issuing new SD-JWT ...")
        val iat = Instant.now()
        val exp = iat.plus(expirationPeriod.days.toLong(), ChronoUnit.DAYS)
        val sdJwtElements =
            sdJwt {
                iss(iss)
                iat(iat.epochSecond)
                exp(exp.epochSecond)
                cnf(holderPubKey as JWK)
                sd("credentialSubject") {
                    sd(credential)
                    recursiveArray("addresses") {
                        buildSdObject {
                            sd("street", "street1")
                        }
                        buildSdObject {
                            sd("street", "street2")
                        }
                    }
                }
            }
        return sdJwtIssuer.issue(sdElements = sdJwtElements).fold(
            onSuccess = { issued ->
                issuerDebug("Issued new SD-JWT")
                issued.serialize()
            },
            onFailure = { exception ->
                issuerDebug("Failed to issue SD-JWT")
                throw exception
            },
        )
    }

    private fun issuerDebug(s: String) {
        println("Issuer: $s")
    }
}

/**
 * This is a sample holder capable of keeping a [credential][credentialSdJwt] issued by [IssuerActor]
 * and responding to [VerifierActor] query
 */
class HolderActor(
    private val holderKey: ECKey,
    lookup: LookupPublicKeysFromDIDDocument,
) {
    private val verifier = SdJwtVcVerifier(httpClientFactory = DefaultHttpClientFactory, lookup = lookup)

    fun pubKey(): AsymmetricJWK = holderKey.toPublicJWK()

    /**
     * Keeps the issued credential
     */
    private var credentialSdJwt: SdJwt.Issuance<JwtAndClaims>? = null

    suspend fun storeCredential(sdJwt: String) {
        holderDebug("Storing issued SD-JWT ...")
        verifier.verifyIssuance(sdJwt).fold(
            onSuccess = { issued ->
                credentialSdJwt = issued
                holderDebug("Stored SD-JWT")
            },
            onFailure = { exception ->
                holderDebug("Failed to store SD-JWT")
                throw exception
            },
        )
    }

    suspend fun present(verifierQuery: VerifierQuery): String {
        holderDebug("Presenting credentials ...")

        val presentationSdJwt =
            with(DefaultSdJwtOps) {
                val issuanceSdJwt = checkNotNull(credentialSdJwt)
                val whatToDisclose = verifierQuery.whatToDisclose
                issuanceSdJwt.present(whatToDisclose)?.let { tmp ->
                    SdJwt.Presentation(SignedJWT.parse(tmp.jwt.first), tmp.disclosures)
                }
            }
        checkNotNull(presentationSdJwt)

        return with(NimbusSdJwtOps) {
            val buildKbJwt = kbJwtIssuer(JWSAlgorithm.ES256, ECDSASigner(holderKey), holderKey.toPublicJWK()) {
                audience(verifierQuery.challenge.aud)
                claim("nonce", verifierQuery.challenge.nonce)
                issueTime(Date.from(Instant.ofEpochSecond(verifierQuery.challenge.iat)))
            }
            presentationSdJwt.serializeWithKeyBinding(buildKbJwt).getOrThrow()
        }
    }

    private fun holderDebug(s: String) {
        println("Holder: $s")
    }
}

class VerifierActor(
    private val clientId: String,
    private val whatToDisclose: Set<ClaimPath>,
    private val expectedNumberOfDisclosures: Int,
    lookup: LookupPublicKeysFromDIDDocument,
) : DefaultSdJwtOps {
    private val verifier = SdJwtVcVerifier(httpClientFactory = DefaultHttpClientFactory, lookup = lookup)
    private var lastChallenge: JsonObject? = null
    private var presentation: SdJwt.Presentation<JwtAndClaims>? = null
    fun query(): VerifierQuery = VerifierQuery(
        VerifierChallenge(Random.nextBytes(10).toString(), clientId, Instant.now()),
        whatToDisclose,
    ).also { lastChallenge = it.challenge.asJson() }

    suspend fun acceptPresentation(unverifiedSdJwt: String) {
        val (presented, _) = verifier.verifyPresentation(unverifiedSdJwt, lastChallenge).getOrThrow()
        presented.prettyPrint { it.second }
        presentation = presented.ensureContainsWhatRequested()
        verifierDebug("Presentation accepted with SD Claims:")
    }

    private fun SdJwt.Presentation<JwtAndClaims>.ensureContainsWhatRequested() = apply {
        val disclosedPaths = recreateClaimsAndDisclosuresPerClaim().second.keys
        whatToDisclose.forEach { requested ->
            assertTrue("Requested $requested was not disclosed") {
                disclosedPaths.any { disclosed -> disclosed in requested }
            }
        }
        assertEquals(
            expectedNumberOfDisclosures,
            disclosures.size,
            "Expected $expectedNumberOfDisclosures but found ${disclosures.size}",
        )
    }

    private fun verifierDebug(s: String) {
        println("Verifier: $s")
    }
}
