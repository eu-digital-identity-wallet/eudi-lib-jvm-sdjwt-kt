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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant

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

    private val sdJwtIssuer = NimbusSdJwtIssuerFactory.createIssuer(
        signer = ECDSASigner(issuerKey),
        signAlgorithm = JWSAlgorithm.ES256,
    )
    fun jwtVerifier() = ECDSAVerifier(issuerKey.toPublicJWK()).asJwtVerifier()

    fun issue(holderPubKey: JWK?, credentialSubject: CredentialSubject): String {
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
        return sdJwtIssuer.issue(sdJwtElements = sdJwtElements).getOrThrow().serialize()
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

    fun present(criteria: (Claim) -> Boolean): String {
        return credentialSdJwt!!.present(null, criteria).toCombinedPresentationFormat({ it }, { it })
    }
}

class SampleVerifier(val query: (Claim) -> Boolean) {

    private var credentialSdJwt: SdJwt.Presentation<Jwt, Nothing>? = null
    fun verifyPresentation(issuerJwtVerifier: JwtVerifier, sdJwt: String) {
        val issued: SdJwt.Presentation<Pair<Jwt, Claims>, Jwt> = SdJwtVerifier.verifyPresentation(
            jwtVerifier = issuerJwtVerifier,
            holderBindingVerifier = HolderBindingVerifier.ShouldNotBePresent,
            sdJwt = sdJwt,
        ).getOrThrow().also { println(it) }
        credentialSdJwt = SdJwt.Presentation(issued.jwt.first, issued.disclosures, null)
    }
}

fun main() {
    val issuer = SampleIssuer(ECKeyGenerator(Curve.P_256).keyID("issuer").generate())
    val holder = SampleHolder(ECKeyGenerator(Curve.P_256).keyID("holder").generate())
    val verifier = SampleVerifier { claim -> claim.name() in listOf("email") }

    val emailCredential = CredentialSubject(
        givenName = "John",
        familyName = "Doe",
        email = "john@foobar.com",
        countries = listOf("GR", "DE"),
    )

    val issuedSdJwt: String = issuer.issue(holder.pubKey(), emailCredential)
    holder.verifyIssuance(issuer.jwtVerifier(), issuedSdJwt)
    val presentedSdJwt: String = holder.present(verifier.query)
    verifier.verifyPresentation(issuer.jwtVerifier(), presentedSdJwt)
}
