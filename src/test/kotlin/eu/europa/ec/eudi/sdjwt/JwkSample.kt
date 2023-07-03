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

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant

fun main() {
    val issuerKey = ECKeyGenerator(Curve.P_256).keyID("issuer").generate()
    val holderKey = ECKeyGenerator(Curve.P_256).keyID("holder").generate()

    val emailCredential = EmailCredential(
        type = "Sample Email",
        issuedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(100),
        issuerPubKey = issuerKey.toPublicJWK(),
        holderPubKey = holderKey.toPublicJWK(),
        credentialSubject = CredentialSubject(
            givenName = "John",
            familyName = "Doe",
            email = "john@foobar.com",
            countries = listOf("GR", "DE"),
        ),
    )
    val sdJwtElements = emailCredential.sdJwtElements()
    val disclosedClaims = DisclosuresCreator().discloseSdJwt(sdJwtElements).getOrThrow()

    disclosedClaims.print()
}

private fun EmailCredential.sdJwtElements(): List<SdJwtElement> =
    sdJwt {
        iss(didJwk(issuerPubKey))
        iat(issuedAt)
        exp(expiresAt)
        cnf(holderPubKey)
        structured("credentialSubject") {
            flat(credentialSubject.asJsonObject())
        }
    }

data class EmailCredential(
    val type: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val issuerPubKey: JWK,
    val holderPubKey: JWK?,
    val credentialSubject: CredentialSubject,
)

@Serializable
data class CredentialSubject(
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val email: String,
    val countries: List<String>,
)

private fun SdJwtElementsBuilder.cnf(jwk: JWK?) {
    plain {
        putJsonObject("cnf") {
            jwk?.let { putJsonObjectFromString("jwk", it.toJSONString()) }
        }
    }
}

private fun didJwk(jwk: JWK): String =
    "did:jwk:${JwtBase64.encodeString(jwk.toJSONString())}"

private fun JsonObjectBuilder.putJsonObjectFromString(claimName: String, jsonStr: String) {
    val jsonObject = jsonStr.let { json.parseToJsonElement(it).jsonObject }
    put(claimName, jsonObject)
}

private fun CredentialSubject.asJsonObject(): JsonObject = json.encodeToJsonElement(this).jsonObject
