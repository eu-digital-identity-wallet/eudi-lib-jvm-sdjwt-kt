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
import com.nimbusds.jose.crypto.ECDSAVerifier
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
import java.text.ParseException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HolderBindingTest {

    val issuer = SampleIssuer(genKey("issuer"))
    val holder = SampleHolder(genKey("holder"))

    @Test
    fun testIssuance() {
        val emailCredential = CredentialSubject(
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
        val selectivelyDisclosedClaims = issuedSdJwt.selectivelyDisclosedClaims()
        assertEquals(4, selectivelyDisclosedClaims.size)
        assertEquals(emailCredential.givenName, selectivelyDisclosedClaims["given_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.familyName, selectivelyDisclosedClaims["family_name"]?.jsonPrimitive?.content)
        assertEquals(emailCredential.email, selectivelyDisclosedClaims["email"]?.jsonPrimitive?.content)

        // Assert issuer verifier is able to verify JWT
        val jwtClaims = assertNotNull(issuer.jwtVerifier().invoke(issuedSdJwt.jwt.serialize()))

        // Assert issuers pub key extractor is able to retrieve holder pub key
        val holderPubKeyExtractor = issuer.extractHolderPubKey()
        assertEquals(holderPubKey, holderPubKeyExtractor(jwtClaims))
    }

    @Test
    fun holderBindingFullTest() {
        val verifier = SampleVerifier { claim -> claim.name() in listOf("email") }

        val emailCredential = CredentialSubject(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        )

        // Issuer should know holder pub key, to included within SD-JWT
        val issuedSdJwt: String = issuer.issue(holder.pubKey(), emailCredential).serialize()
        // Holder should know, issuer pub key & signing algorithm to validate SD-JWT
        holder.verifyIssuance(issuer.jwtVerifier(), issuedSdJwt)

        // Holder must obtain a challenge from verifier to sign it as Holder Binding JWT
        // Also Holder should know what verifier wants to be presented
        val presentedSdJwt: String = holder.present(verifier.challenge(), verifier.query)

        // Verifier should know/trust the issuer.
        // Also, Verifier should know how to obtain Holder Pub Key, from within SD-JWT
        verifier.verifyPresentation(issuer.jwtVerifier(), issuer.extractHolderPubKey(), presentedSdJwt)
    }

    private fun genKey(kid: String) = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
}

/**
 * Domain model of credential
 */
@Serializable
data class CredentialSubject(
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val email: String,
    val countries: List<String>,
)

class SampleIssuer(private val issuerKey: ECKey) {

    private val signAlgorithm = JWSAlgorithm.ES256
    private val jwtType = JOSEObjectType("sd-jwt")

    private val sdJwtIssuer = NimbusSdJwtIssuerFactory.createIssuer(
        signer = ECDSASigner(issuerKey),
        signAlgorithm = signAlgorithm,
    ) {
        type(jwtType)
        keyID(issuerKey.keyID)
    }

    fun jwtVerifier(): JwtVerifier =
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

    fun issue(holderPubKey: JWK?, credentialSubject: CredentialSubject): SdJwt.Issuance<SignedJWT> {
        val sdJwtElements =
            sdJwt {
                iss(didJwk(issuerKey.toPublicJWK()))
                iat(Instant.now())
                exp(Instant.now().plusSeconds(1000))
                cnf(holderPubKey)
                structured("credentialSubject") {
                    flat(credentialSubject.asJsonObject())
                }
            }
        return sdJwtIssuer.issue(sdJwtElements = sdJwtElements).getOrThrow()
    }

    fun didJwk(jwk: JWK): String =
        "did:jwk:${JwtBase64.encodeString(jwk.toJSONString())}"

    private fun SdJwtElementsBuilder.cnf(jwk: JWK?) {
        plain {
            putJsonObject("cnf") {
                jwk?.let { putJsonObjectFromString("jwk", it.toJSONString()) }
            }
        }
    }

    fun extractHolderPubKey(): (Claims) -> JWK? = { claims ->

        claims["cnf"]
            ?.let { cnf -> if (cnf is JsonObject) cnf["jwk"] else null }
            ?.let { jwk -> if (jwk is JsonObject) jwk else null }
            ?.let { jwk ->
                try {
                    JWK.parse(jwk.toString())
                } catch (e: ParseException) {
                    null
                }
            }
    }

    private fun JsonObjectBuilder.putJsonObjectFromString(claimName: String, jsonStr: String) {
        val jsonObject = jsonStr.let { json.parseToJsonElement(it).jsonObject }
        put(claimName, jsonObject)
    }

    private fun CredentialSubject.asJsonObject(): JsonObject = json.encodeToJsonElement(this).jsonObject
}

class SampleHolder(private val holderKey: ECKey) {

    private var credentialSdJwt: SdJwt.Issuance<Jwt>? = null
    fun pubKey(): ECKey = holderKey.toPublicJWK()

    fun verifyIssuance(issuerJwtVerifier: JwtVerifier, sdJwt: String) {
        val issued: SdJwt.Issuance<Pair<Jwt, Claims>> = SdJwtVerifier.verifyIssuance(
            issuerJwtVerifier,
            sdJwt = sdJwt,
        ).getOrThrow().also { println(it) }
        credentialSdJwt = SdJwt.Issuance(issued.jwt.first, issued.disclosures)
    }

    fun present(verifierChallenge: Claims, criteria: (Claim) -> Boolean): String {
        val holderBindingJwt = holderBindingJwt(verifierChallenge)
        return credentialSdJwt!!.present(holderBindingJwt, criteria).toCombinedPresentationFormat({ it }, { it })
    }

    private fun holderBindingJwt(verifierChallenge: Claims): String {
        return SignedJWT(
            JWSHeader(JWSAlgorithm.ES256),
            JWTClaimsSet.parse(JsonObject(verifierChallenge).toString()),
        ).apply {
            sign(ECDSASigner(holderKey))
        }.serialize()
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

    fun verifyPresentation(issuerJwtVerifier: JwtVerifier, holderPubKeyExtractor: (Claims) -> JWK?, sdJwt: String) {
        val issued: SdJwt.Presentation<Pair<Jwt, Claims>, Jwt> = SdJwtVerifier.verifyPresentation(
            jwtVerifier = issuerJwtVerifier,
            holderBindingVerifier = holderBindingVerifier(holderPubKeyExtractor),
            sdJwt = sdJwt,
        ).getOrThrow().also { println(it) }
        presentation = SdJwt.Presentation(issued.jwt.first, issued.disclosures, issued.holderBindingJwt)
    }

    private fun holderBindingVerifier(holderPubKeyExtractor: (Claims) -> JWK?): HolderBindingVerifier {
        fun holderBindingJwtVerifier(jwk: JWK): JwtVerifier {
            return ECDSAVerifier(jwk.toECKey()).asJwtVerifier().and { holderBindingJwtClaims ->
                holderBindingJwtClaims == lastChallenge
            }
        }

        return HolderBindingVerifier.MustBePresentAndValid { sdJwtClaims ->
            holderPubKeyExtractor(sdJwtClaims)?.let { jwk ->
                holderBindingJwtVerifier(jwk)
            }
        }
    }
}
