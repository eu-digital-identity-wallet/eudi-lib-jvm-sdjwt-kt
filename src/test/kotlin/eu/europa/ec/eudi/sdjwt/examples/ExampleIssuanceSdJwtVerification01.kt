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
package eu.europa.ec.eudi.sdjwt.examples

import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.*

val verifiedIssuanceSdJwt: SdJwt<JwtAndClaims> = runBlocking {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val jwtSignatureVerifier =
        RSASSAVerifier(issuerKeyPair).asJwtVerifier().map(::nimbusToJwtAndClaims)

    val unverifiedIssuanceSdJwt = loadSdJwt("/exampleIssuanceSdJwt.txt")
    DefaultSdJwtOps.verifyIssuance(
        jwtSignatureVerifier = jwtSignatureVerifier,
        unverifiedSdJwt = unverifiedIssuanceSdJwt,
    ).getOrThrow()
}
