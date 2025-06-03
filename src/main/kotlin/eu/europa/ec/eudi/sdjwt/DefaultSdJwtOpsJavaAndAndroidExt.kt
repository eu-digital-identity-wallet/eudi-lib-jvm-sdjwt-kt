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
import kotlinx.serialization.json.JsonObject
import java.security.cert.X509Certificate
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

val DefaultSdJwtOps.SdJwtVcSignatureVerifier: SdJwtVcSignatureVerifierFactory<JwtAndClaims, NimbusJWK, List<X509Certificate>>
    get() = NimbusSdJwtVcSignatureVerifierFactory.transform(
        convertJwt = ::nimbusToJwtAndClaims,
        convertJwk = { it },
        convertX509Chain = { it },
    )

val DefaultSdJwtOps.SdJwtVcVerifier: SdJwtVcVerifierFactory<JwtAndClaims>
    get() = NimbusSdJwtVcVerifierFactory.transform(::jwtAndClaimsToNimbus, ::nimbusToJwtAndClaims)

internal fun nimbusToJwtAndClaims(signedJWT: NimbusSignedJWT): JwtAndClaims =
    checkNotNull(signedJWT.serialize()) to signedJWT.jwtClaimsSet.jsonObject()

internal fun jwtAndClaimsToNimbus(jwtAndClaims: JwtAndClaims): NimbusSignedJWT =
    NimbusSignedJWT.parse(jwtAndClaims.first)

operator fun X509CertificateTrust.Companion.invoke(
    trust: suspend (List<X509Certificate>, JsonObject) -> Boolean,
): X509CertificateTrust<List<X509Certificate>> = X509CertificateTrust { chain, claimSet -> trust(chain, claimSet) }

fun X509CertificateTrust.Companion.usingVct(
    trust: suspend (List<X509Certificate>, String) -> Boolean,
): X509CertificateTrust<List<X509Certificate>> = usingVct(trust)
