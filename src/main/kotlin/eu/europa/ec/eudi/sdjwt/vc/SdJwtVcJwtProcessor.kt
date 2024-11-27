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
package eu.europa.ec.eudi.sdjwt.vc

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier
import com.nimbusds.jwt.proc.JWTProcessor
import eu.europa.ec.eudi.sdjwt.JwkSourceJWTProcessor
import java.security.Key

/**
 * [JWTProcessor] that supports [RSAKey], [ECKey], [OctetKeyPair], and [OctetSequenceKey] signature verification.
 *
 * It overrides the default behavior of [DefaultJWTProcessor] and instead of using [JWSVerificationKeySelector] to
 * select the verification [Key], and [DefaultJWSVerifierFactory] to instantiate a [JWSVerifier], it instantiates
 * the appropriate [JWSVerifier] directly, based on the type of the selected verification [JWK] that has been
 * selected using a [JWKSelector] instead.
 *
 * This allows for full support of [OctetKeyPair] which otherwise cannot be supported due the lack of
 * [OctetKeyPair.toKeyPair], [OctetKeyPair.toPublicKey], and [OctetKeyPair.toPrivateKey] implementations required
 * by [JWSVerificationKeySelector].
 *
 * **Note:** The optional dependency 'com.google.crypto.tink:tink' is required when support for [OctetKeyPair] is required.
 */
internal class SdJwtVcJwtProcessor<C : SecurityContext>(
    jwkSource: JWKSource<C>,
) : JwkSourceJWTProcessor<C>(typeVerifier(), claimSetVerifier(), jwkSource) {

    companion object {
        private fun <C : SecurityContext> typeVerifier(): JOSEObjectTypeVerifier<C> =
            DefaultJOSEObjectTypeVerifier(JOSEObjectType(SdJwtVcSpec.SD_JWT_VC_TYPE))

        private fun <C : SecurityContext> claimSetVerifier(): JWTClaimsSetVerifier<C> = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().build(),
            setOf("iss"),
        )

        /**
         * Gets a [JWKSource] for a DID Document.
         */
        fun <C : SecurityContext> didJwkSet(jwsHeader: JWSHeader, jwkSet: JWKSet): JWKSource<C> =
            DIDJWKSet<C>(jwsHeader, jwkSet)
    }
}

/**
 * [JWKSource] implementation for DID Documents.
 *
 * When [JWKSource.get] is invoked, it ignores the provided [JWKSelector], and instead uses one that matches
 * all the properties of the provided [JWSHeader] besides the Key ID.
 */
private class DIDJWKSet<C : SecurityContext>(jwsHeader: JWSHeader, val jwkSet: JWKSet) : JWKSource<C> {
    private val jwkSelector: JWKSelector by lazy {
        // Create a JWKMatcher that considers all attributes of the JWK but the Key ID.
        // The matcher here doesn't support HMAC Secret Key resolution, since DID Documents cannot contain private keys.
        // See also: JWKMatcher.forJWSHeader().
        val matcher = when (val algorithm = jwsHeader.algorithm) {
            in JWSAlgorithm.Family.RSA, in JWSAlgorithm.Family.EC ->
                JWKMatcher.Builder()
                    .keyType(KeyType.forAlgorithm(algorithm))
                    .keyUses(KeyUse.SIGNATURE, null)
                    .algorithms(algorithm, null)
                    .x509CertSHA256Thumbprint(jwsHeader.x509CertSHA256Thumbprint)
                    .build()

            in JWSAlgorithm.Family.ED ->
                JWKMatcher.Builder()
                    .keyType(KeyType.forAlgorithm(algorithm))
                    .keyUses(KeyUse.SIGNATURE, null)
                    .algorithms(algorithm, null)
                    .curves(Curve.forJWSAlgorithm(algorithm))
                    .build()

            else -> error("Unsupported JWSAlgorithm '$algorithm'")
        }
        JWKSelector(matcher)
    }

    override fun get(jwkSelector: JWKSelector, context: C?): MutableList<JWK> = this.jwkSelector.select(jwkSet)
}
