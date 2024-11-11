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
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor
import java.security.Key
import java.text.ParseException

const val SD_JWT_VC_TYPE = "vc+sd-jwt"

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
    private val jwkSource: JWKSource<C>,
) : DefaultJWTProcessor<C>() {
    init {
        jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(JOSEObjectType(SD_JWT_VC_TYPE))
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().build(),
            setOf("iss"),
        )
    }

    override fun process(signedJWT: SignedJWT, context: C?): JWTClaimsSet {
        ensureInitialized()

        jwsTypeVerifier.verify(signedJWT.header.type, context)

        val claimsSet = signedJWT.jwtClaimSet()
        val jwkSelector = JWKSelector(JWKMatcher.forJWSHeader(signedJWT.header))

        val jwks = jwkSource.get(jwkSelector, context)
        if (jwks.isNullOrEmpty()) {
            throw BadJOSEException("Signed JWT rejected: Another algorithm expected, or no matching key(s) found")
        }

        for (jwk in jwks) {
            val verifier = jwsVerifierFor(signedJWT.header.algorithm, jwk)
            if (signedJWT.verify(verifier)) {
                jwtClaimsSetVerifier.verify(claimsSet, context)
                return claimsSet
            }
        }

        // No more keys to try out
        throw BadJOSEException("Signed JWT rejected: Invalid signature or no matching verifier(s) found")
    }

    private fun ensureInitialized() {
        if (jwsTypeVerifier == null) {
            throw BadJOSEException("Signed JWT rejected: No JWS header typ (type) verifier is configured")
        }

        if (jwtClaimsSetVerifier == null) {
            throw BadJOSEException("Signed JWT rejected: No JWTClaimSet verifier is configured")
        }
    }

    companion object {

        /**
         * Gets a [JWKSource] for a DID Document.
         */
        fun <C : SecurityContext> didJwkSet(jwsHeader: JWSHeader, jwkSet: JWKSet): JWKSource<C> = DIDJWKSet<C>(jwsHeader, jwkSet)
    }
}

private fun SignedJWT.jwtClaimSet(): JWTClaimsSet =
    try {
        getJWTClaimsSet()
    } catch (e: ParseException) {
        // Payload not a JSON object
        throw BadJWTException(e.message, e)
    }

private fun jwsVerifierFor(algorithm: JWSAlgorithm, jwk: JWK): JWSVerifier =
    when (algorithm) {
        in JWSAlgorithm.Family.HMAC_SHA -> MACVerifier(jwk.expectIs<OctetSequenceKey>())
        in JWSAlgorithm.Family.RSA -> RSASSAVerifier(jwk.expectIs<RSAKey>())
        in JWSAlgorithm.Family.EC -> ECDSAVerifier(jwk.expectIs<ECKey>())
        in JWSAlgorithm.Family.ED -> Ed25519Verifier(jwk.expectIs<OctetKeyPair>())
        else -> throw BadJOSEException("Unsupported JWS algorithm $algorithm")
    }

private inline fun <reified T> JWK.expectIs(): T =
    if (this is T) {
        this
    } else {
        throw BadJOSEException("Expected a JWK of type ${T::class.java.simpleName}")
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
