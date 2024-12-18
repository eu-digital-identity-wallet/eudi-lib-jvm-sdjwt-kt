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

import eu.europa.ec.eudi.sdjwt.JwkSourceJWTProcessor
import eu.europa.ec.eudi.sdjwt.RFC7519
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import com.nimbusds.jose.JOSEObjectType as NimbusJOSEObjectType
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.jwk.Curve as NimbusCurve
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jose.jwk.JWKMatcher as NimbusJWKMatcher
import com.nimbusds.jose.jwk.JWKSelector as NimbusJWKSelector
import com.nimbusds.jose.jwk.JWKSet as NimbusJWKSet
import com.nimbusds.jose.jwk.KeyType as NimbusKeyType
import com.nimbusds.jose.jwk.KeyUse as NimbusKeyUse
import com.nimbusds.jose.jwk.source.JWKSource as NimbusJWKSource
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier as NimbusDefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier as NimbusJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.SecurityContext as NimbusSecurityContext
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier as NimbusDefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier as NimbusJWTClaimsSetVerifier

internal class SdJwtVcJwtProcessor<C : NimbusSecurityContext>(
    jwkSource: NimbusJWKSource<C>,
) : JwkSourceJWTProcessor<C>(typeVerifier(), claimSetVerifier(), jwkSource) {

    companion object {
        /**
         * Accepts both the [SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT]
         * and the deprecated [SdJwtVcSpec.MEDIA_SUBTYPE_VC_SD_JWT]
         */
        private fun <C : NimbusSecurityContext> typeVerifier(): NimbusJOSEObjectTypeVerifier<C> =
            NimbusDefaultJOSEObjectTypeVerifier(
                NimbusJOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_VC_SD_JWT),
                NimbusJOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT),
            )

        private fun <C : NimbusSecurityContext> claimSetVerifier(): NimbusJWTClaimsSetVerifier<C> =
            NimbusDefaultJWTClaimsVerifier(
                NimbusJWTClaimsSet.Builder().build(),
                setOf(RFC7519.ISSUER, SdJwtVcSpec.VCT),
            )

        /**
         * Gets a [NimbusJWKSource] for a DID Document.
         */
        fun <C : NimbusSecurityContext> didJwkSet(
            jwsHeader: NimbusJWSHeader,
            jwkSet: NimbusJWKSet,
        ): NimbusJWKSource<C> =
            DIDJWKSet<C>(jwsHeader, jwkSet)
    }
}

/**
 * [NimbusJWKSource] implementation for DID Documents.
 *
 * When [NimbusJWKSource.get] is invoked, it ignores the provided [NimbusJWKSet], and instead uses one that matches
 * all the properties of the provided [NimbusJWSHeader] besides the Key ID.
 */
private class DIDJWKSet<C : NimbusSecurityContext>(jwsHeader: NimbusJWSHeader, val jwkSet: NimbusJWKSet) :
    NimbusJWKSource<C> {
        private val jwkSelector: NimbusJWKSelector by lazy {
            // Create a JWKMatcher that considers all attributes of the JWK but the Key ID.
            // The matcher here doesn't support HMAC Secret Key resolution, since DID Document cannot contain private keys.
            // See also: JWKMatcher.forJWSHeader().
            val matcher = when (val algorithm = jwsHeader.algorithm) {
                in NimbusJWSAlgorithm.Family.RSA, in NimbusJWSAlgorithm.Family.EC ->
                    NimbusJWKMatcher.Builder()
                        .keyType(NimbusKeyType.forAlgorithm(algorithm))
                        .keyUses(NimbusKeyUse.SIGNATURE, null)
                        .algorithms(algorithm, null)
                        .x509CertSHA256Thumbprint(jwsHeader.x509CertSHA256Thumbprint)
                        .build()

                in NimbusJWSAlgorithm.Family.ED ->
                    NimbusJWKMatcher.Builder()
                        .keyType(NimbusKeyType.forAlgorithm(algorithm))
                        .keyUses(NimbusKeyUse.SIGNATURE, null)
                        .algorithms(algorithm, null)
                        .curves(NimbusCurve.forJWSAlgorithm(algorithm))
                        .build()

                else -> error("Unsupported JWSAlgorithm '$algorithm'")
            }
            NimbusJWKSelector(matcher)
        }

        override fun get(jwkSelector: NimbusJWKSelector, context: C?): MutableList<NimbusJWK> =
            this.jwkSelector.select(jwkSet)
    }
