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

import eu.europa.ec.eudi.sdjwt.vc.*
import java.security.cert.X509Certificate
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

val DefaultSdJwtOps.SdJwtVcVerifier: SdJwtVcVerifierFactory<JwtAndClaims, NimbusJWK, List<X509Certificate>>
    get() = DefaultSdJwtVcFactory

internal object DefaultSdJwtVcFactory : SdJwtVcVerifierFactory<JwtAndClaims, NimbusJWK, List<X509Certificate>> {
    override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingIssuerMetadata(httpClientFactory).map(::nimbusToJwtAndClaims)

    override fun usingX5c(x509CertificateTrust: X509CertificateTrust<List<X509Certificate>>): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingX5c(x509CertificateTrust).map(::nimbusToJwtAndClaims)

    override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<NimbusJWK>): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingDID(didLookup).map(::nimbusToJwtAndClaims)

    override fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust<List<X509Certificate>>,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingX5cOrIssuerMetadata(x509CertificateTrust, httpClientFactory).map(::nimbusToJwtAndClaims)
}

internal fun nimbusToJwtAndClaims(signedJWT: NimbusSignedJWT): JwtAndClaims =
    checkNotNull(signedJWT.serialize()) to signedJWT.jwtClaimsSet.jsonObject()
