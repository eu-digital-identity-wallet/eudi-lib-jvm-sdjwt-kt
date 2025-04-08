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
import kotlinx.datetime.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.util.*
import kotlin.onFailure
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

        (JWSAlgorithm.Family.SIGNATURE + JWSAlgorithm.Family.HMAC_SHA - JWSAlgorithm.Ed448)
            .forEach { algorithm ->
                test(createContext(algorithm))
                println("JWSAlgorithm $algorithm tested OK")
            }
    }
}

private val sdObject = sdJwt {
    claim("iss", "https://issuer.example.com")
    claim("iat", 1683000000)
    claim("exp", 1883000000)

    claim("vct", "https://bmi.bund.example/credential/pid/1.0")
    objClaim("cnf") {
        objClaim("jwk") {
            claim("kty", "EC")
            claim("crv", "P-256")
            claim("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
            claim("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
        }
    }

    sdClaim("given_name", "Erika")
    sdClaim("family_name", "Mustermann")
    sdClaim("birthdate", "1963-08-12")
}

private data class Context(
    val jwk: JWK,
    val issuer: SdJwtIssuer<SignedJWT>,
    val verifier: JwtSignatureVerifier<SignedJWT>,
)

private fun createContext(algorithm: JWSAlgorithm): Context = with(NimbusSdJwtOps) {
    val keyId = UUID.randomUUID().toString()
    val issuedAt = Date(Clock.System.now().toEpochMilliseconds())

    when (algorithm) {
        in JWSAlgorithm.Family.EC -> {
            val curve = when (algorithm) {
                JWSAlgorithm.ES256 -> Curve.P_256
                JWSAlgorithm.ES256K -> Curve.SECP256K1
                JWSAlgorithm.ES384 -> Curve.P_384
                JWSAlgorithm.ES512 -> Curve.P_521
                else -> throw IllegalArgumentException("Unknown EC JWSAlgorithm $algorithm")
            }
            val provider = BouncyCastleProvider()
            val jwk = ECKeyGenerator(curve)
                .provider(provider)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            val signer = ECDSASigner(jwk).apply { jcaContext.provider = provider }
            val verifier = ECDSAVerifier(jwk.toPublicJWK())
                .apply { jcaContext.provider = provider }
                .asJwtVerifier()
            Context(
                jwk,
                NimbusSdJwtOps.issuer(signer = signer, signAlgorithm = algorithm),
                verifier,
            )
        }

        in JWSAlgorithm.Family.RSA -> {
            val jwk = RSAKeyGenerator(2048, false)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                NimbusSdJwtOps.issuer(
                    signer = RSASSASigner(jwk),
                    signAlgorithm = algorithm,
                ),
                RSASSAVerifier(jwk.toPublicJWK()).asJwtVerifier(),
            )
        }

        in JWSAlgorithm.Family.ED -> {
            val curve = when (algorithm) {
                JWSAlgorithm.EdDSA, JWSAlgorithm.Ed25519 -> Curve.Ed25519
                else -> throw IllegalArgumentException("Unknown ED JWSAlgorithm $algorithm")
            }
            val jwk = OctetKeyPairGenerator(curve)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            val signer = when (algorithm) {
                JWSAlgorithm.EdDSA, JWSAlgorithm.Ed25519 -> Ed25519Signer(jwk)
                else -> throw IllegalArgumentException("Unknown ED JWSAlgorithm $algorithm")
            }
            val verifier = when (algorithm) {
                JWSAlgorithm.EdDSA, JWSAlgorithm.Ed25519 -> Ed25519Verifier(jwk.toPublicJWK()).asJwtVerifier()
                else -> throw IllegalArgumentException("Unknown ED JWSAlgorithm $algorithm")
            }
            Context(
                jwk,
                NimbusSdJwtOps.issuer(signer = signer, signAlgorithm = algorithm),
                verifier,
            )
        }

        in JWSAlgorithm.Family.HMAC_SHA -> {
            val jwk = OctetSequenceKeyGenerator(512)
                .keyID(keyId)
                .issueTime(issuedAt)
                .generate()
            Context(
                jwk,
                NimbusSdJwtOps.issuer(signer = MACSigner(jwk), signAlgorithm = algorithm),
                MACVerifier(jwk).asJwtVerifier(),
            )
        }

        else -> throw IllegalArgumentException("Unknown JWSAlgorithm $algorithm")
    }
}
