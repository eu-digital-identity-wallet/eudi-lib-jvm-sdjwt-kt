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
import kotlinx.serialization.json.*
import java.time.*
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

val verifiedEnvelopedSdJwt: SdJwt.Presentation<JwtAndClaims> = run {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val issuerSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()

    val holderKeyPair = loadRsaKey("/exampleHolderKey.json")
    val holderSignatureVerifier = RSASSAVerifier(holderKeyPair).asJwtVerifier()
        .and { claims ->
            claims["nonce"] == JsonPrimitive("nonce")
        }

    val unverifiedEnvelopedSdJwt = loadJwt("/exampleEnvelopedSdJwt.txt")

    SdJwtVerifier.verifyEnvelopedPresentation(
        sdJwtSignatureVerifier = issuerSignatureVerifier,
        envelopeJwtVerifier = holderSignatureVerifier,
        clock = Clock.systemDefaultZone(),
        iatOffset = 3650.days.toJavaDuration(),
        expectedAudience = "verifier",
        unverifiedEnvelopeJwt = unverifiedEnvelopedSdJwt,
    ).getOrThrow()
}
