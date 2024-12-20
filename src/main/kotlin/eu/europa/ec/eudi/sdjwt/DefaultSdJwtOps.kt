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

import eu.europa.ec.eudi.sdjwt.vc.DefaultSdJwtVcFactory
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifierFactory
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object DefaultSdJwtOps :
    SdJwtVerifier<JwtAndClaims> by DefaultVerifier,
    SdJwtSerializationOps<JwtAndClaims> by DefaultSerializationOps,
    SdJwtPresentationOps<JwtAndClaims> by DefaultPresentationOps,
    UnverifiedIssuanceFrom<JwtAndClaims> by DefaultSdJwtUnverifiedIssuanceFrom {

    val NoSignatureValidation: JwtSignatureVerifier<JwtAndClaims> =
        JwtSignatureVerifier { unverifiedJwt ->
            val (_, claims, _) = jwtClaims(unverifiedJwt).getOrThrow()
            unverifiedJwt to claims
        }

    val KeyBindingVerifierMustBePresent: KeyBindingVerifier<JwtAndClaims> =
        KeyBindingVerifier.mustBePresent(NoSignatureValidation)

    val SdJwtVcVerifier: SdJwtVcVerifierFactory<JwtAndClaims> = DefaultSdJwtVcFactory
}

private val DefaultSerializationOps = SdJwtSerializationOps<JwtAndClaims>(
    serializeJwt = { (jwt, _) -> jwt },
    hashAlgorithm = { (_, claims) ->
        claims[SdJwtSpec.CLAIM_SD_ALG]?.jsonPrimitive?.contentOrNull
            ?.let { checkNotNull(HashAlgorithm.fromString(it)) { "Unknown hash algorithm $it" } }
    },
)

private val DefaultPresentationOps = SdJwtPresentationOps<JwtAndClaims> { (_, claims) -> claims }

private val DefaultVerifier: SdJwtVerifier<JwtAndClaims> = SdJwtVerifier { (_, claims) -> claims }

/**
 * A method for obtaining an [SdJwt] given an unverified SdJwt, without checking the signature
 * of the issuer.
 *
 * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verify] the SD-JWT and
 * wants to just re-obtain an instance of the [SdJwt] without repeating this verification
 *
 */
private val DefaultSdJwtUnverifiedIssuanceFrom: UnverifiedIssuanceFrom<JwtAndClaims> =
    UnverifiedIssuanceFrom { unverifiedSdJwt ->
        runCatching {
            val (unverifiedJwt, unverifiedDisclosures) = StandardSerialization.parseIssuance(unverifiedSdJwt)
            val (_, jwtClaims, _) = jwtClaims(unverifiedJwt).getOrThrow()
            val disclosures = verifyDisclosures(jwtClaims, unverifiedDisclosures).getOrThrow()
            SdJwt<JwtAndClaims>(unverifiedJwt to jwtClaims, disclosures)
        }
    }
