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
class HolderBindingTest {

    private val issuer = SampleIssuer(genKey("issuer"))
    private val holder = SampleHolder(genKey("holder"))

    /**
     * This test focuses on the issuance
     *
     * It makes sure that [SampleIssuer] is able to produce correctly an SD-JWT (issuance variation).
     * Furthermore, assures that the [SampleIssuer.jwtVerifier] of the issuer successfully verifies the before-mentioned SD-JWT and
     * that [SampleIssuer.extractHolderPubKey] is indeed able to extract holder pub key from SD-JWT claims
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
        val issuedSdJwt = issuer.issue(holderPubKey, emailCredential.asJsonObject()).also {
            println("Issued: ${it.serialize()}")
        }
        // Assert Disclosed claims
        val selectivelyDisclosedClaims = issuedSdJwt.selectivelyDisclosedClaims()
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
        val verifier = SampleVerifier { claim -> claim.name() in listOf("email") }

        val emailCredential = SampleCredential(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        )

        // Issuer should know holder pub key, to included within SD-JWT
        val issuedSdJwt: String = issuer.issue(holder.pubKey(), emailCredential.asJsonObject()).serialize()
        // Holder should know, issuer pub key & signing algorithm to validate SD-JWT
        holder.storeCredential(issuer.jwtVerifier(), issuedSdJwt)

        // Holder must obtain a challenge from verifier to sign it as Holder Binding JWT
        // Also Holder should know what verifier wants to be presented
        val presentedSdJwt: String = holder.present(verifier.challenge(), verifier.query)

        // Verifier should know/trust the issuer.
        // Also, Verifier should know how to obtain Holder Pub Key, from within SD-JWT
        verifier.acceptPresentation(
            issuer.jwtVerifier(),
            issuer.extractHolderPubKey(),
            holder.holderBindingVerifier(),
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

fun SampleCredential.asJsonObject(): JsonObject = json.encodeToJsonElement(this).jsonObject
fun JWK.asJsonObject(): JsonObject = json.parseToJsonElement(toJSONString()).jsonObject

/**
 * Sample issuer
 */
class SampleIssuer(private val issuerKey: ECKey) {

    private val signAlgorithm = JWSAlgorithm.ES256
    private val jwtType = JOSEObjectType("sd-jwt")
    private val iss: String by lazy {
        "did:jwk:${JwtBase64.encodeString(issuerKey.toPublicJWK().toJSONString())}"
    }
    private val expirationPeriod: Period = Period.ofMonths(12)

    /**
     * The [SdJwtIssuer]
     * It will be able to sign SD-JWT using [issuerKey] and [signAlgorithm]
     * Also, demonstrates the customization of the [JWSHeader] by adding
     * [jwtType] (as "typ" claim) and "kid" claim
     */
    private val sdJwtIssuer: SdJwtIssuer<SignedJWT> = NimbusSdJwtIssuerFactory.createIssuer(
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
            jwsKeySelector = SingleKeyJWSKeySelector(
                signAlgorithm,
                issuerKey.toECPublicKey(),
            )
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
    fun issue(holderPubKey: JWK, credential: JsonObject): SdJwt.Issuance<SignedJWT> {
        issuerDebug("Issuing new SD-JWT ...")
        val iat = Instant.now()
        val exp = iat.plus(expirationPeriod.days.toLong(), ChronoUnit.DAYS)
        val sdJwtElements =
            sdJwt {
                iss(iss)
                iat(iat)
                exp(exp)
                cnf(holderPubKey)
                structured("credentialSubject") {
                    flat(credential)
                }
            }
        return sdJwtIssuer.issue(sdJwtElements = sdJwtElements).fold(
            onSuccess = { issued ->
                issuerDebug("Issued new SD-JWT")
                issued.selectivelyDisclosedClaims().onEachIndexed { i, c -> issuerDebug("-> $i $c") }
                issued
            },
            onFailure = { exception ->
                issuerDebug("Failed to issue SD-JWT")
                throw exception
            },
        )
    }

    /**
     * This is the issuer convention of including holder's pub key to SD-JWT
     * @see extractHolderPubKey for its dual function
     */
    private fun SdJwtElementsBuilder.cnf(jwk: JWK) {
        plain {
            putJsonObject("cnf") {
                put("jwk", jwk.asJsonObject())
            }
        }
    }

    /**
     * This is a dual of [cnf] function
     * Obtains holder's pub key from claims
     *
     * @return the holder's pub key, if found
     */
    fun extractHolderPubKey(): (Claims) -> JWK? = { claims ->

        claims["cnf"]
            ?.let { cnf -> if (cnf is JsonObject) cnf["jwk"] else null }
            ?.let { jwk -> if (jwk is JsonObject) jwk else null }
            ?.let { jwk -> runCatching { JWK.parse(jwk.toString()) }.getOrNull() }
    }

    private fun issuerDebug(s: String) {
        println("Issuer: $s")
    }
}

/**
 * This is a sample holder capable of keeping a [credential][credentialSdJwt] issued by [SampleIssuer]
 * and responding to [SampleVerifier] query
 */
class SampleHolder(private val holderKey: ECKey) {

    private val holderBindingSigningAlgorithm = JWSAlgorithm.ES256

    /**
     * Keeps the issued credential
     */
    private var credentialSdJwt: SdJwt.Issuance<Jwt>? = null
    fun pubKey(): ECKey = holderKey.toPublicJWK()

    fun holderBindingVerifier(): (JWK) -> JwtSignatureVerifier = { holderPubKey ->
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = SingleKeyJWSKeySelector(
                holderBindingSigningAlgorithm,
                holderPubKey.toECKey().toECPublicKey(),
            )
        }.asJwtVerifier()
    }

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

    fun present(verifierChallenge: JsonObject, criteria: (Claim) -> Boolean): String {
        holderDebug("Presenting credentials ...")
        val holderBindingJwt = holderBindingJwt(verifierChallenge)
        return credentialSdJwt!!.present(holderBindingJwt, criteria).toCombinedPresentationFormat({ it }, { it })
    }

    private fun holderBindingJwt(verifierChallenge: JsonObject): String =
        SignedJWT(
            JWSHeader.Builder(holderBindingSigningAlgorithm).keyID(holderKey.keyID).build(),
            JWTClaimsSet.parse(verifierChallenge.toString()),
        ).apply {
            sign(ECDSASigner(holderKey))
        }.serialize()

    private fun holderDebug(s: String) {
        println("Holder: $s")
    }
}

class SampleVerifier(val query: (Claim) -> Boolean) {

    private var lastChallenge: JsonObject? = null
    private var presentation: SdJwt.Presentation<Jwt, Jwt>? = null
    fun challenge(): JsonObject = buildJsonObject {
        put("nonce", "XZOUco1u_gEPknxS78sWWg")
        put("aud", "https://example.com/verifier")
        put("iat", Instant.now().epochSecond)
    }.also { lastChallenge = it }

    fun acceptPresentation(
        issuerJwtSignatureVerifier: JwtSignatureVerifier,
        holderPubKeyExtractor: (Claims) -> JWK?,
        holderBindingJwtSignatureVerifier: (JWK) -> JwtSignatureVerifier,
        sdJwt: String,
    ) {
        SdJwtVerifier.verifyPresentation(
            jwtSignatureVerifier = issuerJwtSignatureVerifier,
            holderBindingVerifier = holderBindingVerifier(holderPubKeyExtractor, holderBindingJwtSignatureVerifier),
            sdJwt = sdJwt,
        ).fold(onSuccess = { presented: SdJwt.Presentation<JwtAndClaims, JwtAndClaims> ->
            presentation = SdJwt.Presentation(presented.jwt.first, presented.disclosures, presented.holderBindingJwt?.first)

            verifierDebug("Presentation accepted with SD Claims:")
            presented.selectivelyDisclosedClaims().onEachIndexed { i, c -> verifierDebug("-> $i $c") }
        }, onFailure = { exception ->
            verifierDebug("Unable to verify presentation")
            throw exception
        })
    }

    private fun holderBindingVerifier(
        holderPubKeyExtractor: (Claims) -> JWK?,
        holderBindingJwtSignatureVerifier: (JWK) -> JwtSignatureVerifier,
    ): HolderBindingVerifier =
        HolderBindingVerifier.MustBePresentAndValid { sdJwtClaims ->
            holderPubKeyExtractor(sdJwtClaims)?.let { jwk ->
                holderBindingJwtSignatureVerifier(jwk).and { holderBindingJwtClaims ->
                    holderBindingJwtClaims == lastChallenge
                }
            }
        }

    private fun verifierDebug(s: String) {
        println("Verifier: $s")
    }
}
