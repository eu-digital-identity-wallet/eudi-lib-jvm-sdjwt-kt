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
import com.nimbusds.jose.crypto.*
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Clock
import java.util.*
import kotlin.test.Test
import kotlin.test.fail

/**
 * Test cases for Nimbus integration.
 */
internal class NimbusIntegrationTest {

    /**
     * Verifies SD-JWTs can be signed using both asymmetric and symmetric signing algorithms
     */
    @Test
    fun `successfully creates signed sd-jwt with both symmetric and asymmetric algorithms`() = runTest {
        suspend fun test(context: Context) {
            val (_, issuer, verifier) = context
            val issued = issuer.issue(sdObject).fold(onSuccess = { it }, onFailure = { fail(it.message, it) })
            verifier.verify(issued.jwt.serialize()).onFailure { fail(it.message, it) }
        }

        (JWSAlgorithm.Family.SIGNATURE + JWSAlgorithm.Family.HMAC_SHA)
            .filter { it != JWSAlgorithm.ES256K }
            .forEach { algorithm ->
                test(createContext(algorithm))
                println("JWSAlgorithm $algorithm tested OK")
            }
    }
}

private val sdObject = sdJwt {
    iss("https://issuer.example.com")
    iat(1683000000)
    exp(1883000000)

    plain {
        put("vct", "https://bmi.bund.example/credential/pid/1.0")
        putJsonObject("cnf") {
            putJsonObject("jwk") {
                put("kty", "EC")
                put("crv", "P-256")
                put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
            }
        }
    }

    sd {
        put("given_name", "Erika")
        put("family_name", "Mustermann")
        put("birthdate", "1963-08-12")
    }
}

private data class Context(
    val jwk: JWK,
    val issuer: SdJwtIssuer<SignedJWT>,
    val verifier: JwtSignatureVerifier,
)

private fun createContext(algorithm: JWSAlgorithm): Context = run {
    val keyId = UUID.randomUUID().toString()
    val issuedAt = Date(Clock.systemDefaultZone().millis())

    when (algorithm) {
        in JWSAlgorithm.Family.EC -> {
            val curve = when (algorithm) {
                JWSAlgorithm.ES256 -> Curve.P_256
                JWSAlgorithm.ES256K -> Curve.SECP256K1
                JWSAlgorithm.ES384 -> Curve.P_384
                JWSAlgorithm.ES512 -> Curve.P_521
                else -> throw IllegalArgumentException("Unknown EC JWSAlgorithm $algorithm")
            }
            val jwk = ECKeyGenerator(curve)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                SdJwtIssuer.nimbus(signer = ECDSASigner(jwk), signAlgorithm = algorithm),
                ECDSAVerifier(jwk.toPublicJWK()).asJwtVerifier(),
            )
        }

        in JWSAlgorithm.Family.RSA -> {
            val jwk = RSAKeyGenerator(2048, false)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                SdJwtIssuer.nimbus(signer = RSASSASigner(jwk), signAlgorithm = algorithm),
                RSASSAVerifier(jwk.toPublicJWK()).asJwtVerifier(),
            )
        }

        in JWSAlgorithm.Family.ED -> {
            val jwk = OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                SdJwtIssuer.nimbus(signer = Ed25519Signer(jwk), signAlgorithm = algorithm),
                Ed25519Verifier(jwk.toPublicJWK()).asJwtVerifier(),
            )
        }

        in JWSAlgorithm.Family.HMAC_SHA -> {
            val jwk = OctetSequenceKeyGenerator(512)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                SdJwtIssuer.nimbus(signer = MACSigner(jwk), signAlgorithm = algorithm),
                MACVerifier(jwk).asJwtVerifier(),
            )
        }

        else -> throw IllegalArgumentException("Unknown JWSAlgorithm $algorithm")
    }
}
