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

import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

interface DefaultSdJwtOps :
    SdJwtVerifier<JwtAndClaims>,
    SdJwtSerializationOps<JwtAndClaims>,
    SdJwtPresentationOps<JwtAndClaims>,
    SdJwtRecreateClaimsOps<JwtAndClaims> {

    override fun SdJwt<JwtAndClaims>.serialize(): String =
        with(serializationOps) { serialize() }

    override suspend fun SdJwt.Presentation<JwtAndClaims>.serializeWithKeyBinding(
        buildKbJwt: BuildKbJwt,
    ): Result<String> = with(serializationOps) { serializeWithKeyBinding(buildKbJwt) }

    override fun SdJwt<JwtAndClaims>.asJwsJsonObject(
        option: JwsSerializationOption,
    ): JsonObject = with(serializationOps) { asJwsJsonObject(option) }

    override fun SdJwt.Presentation<JwtAndClaims>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        kbJwt: Jwt,
    ): JsonObject = with(serializationOps) { asJwsJsonObjectWithKeyBinding(option, kbJwt) }

    override fun SdJwt<JwtAndClaims>.recreateClaimsAndDisclosuresPerClaim(): Pair<JsonObject, DisclosuresPerClaimPath> =
        with(presentationOps) { recreateClaimsAndDisclosuresPerClaim() }

    override fun SdJwt.Issuance<JwtAndClaims>.present(
        query: Set<ClaimPath>,
    ): SdJwt.Presentation<JwtAndClaims>? = with(presentationOps) { present(query) }

    override suspend fun SdJwt.Presentation<JwtAndClaims>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        buildKbJwt: BuildKbJwt,
    ): Result<JsonObject> = with(serializationOps) { asJwsJsonObjectWithKeyBinding(option, buildKbJwt) }

    override fun SdJwt<JwtAndClaims>.recreateClaims(visitor: ClaimVisitor?): JsonObject =
        with(presentationOps) { recreateClaims(visitor) }

    override suspend fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        unverifiedSdJwt: String,
    ): Result<SdJwt.Issuance<JwtAndClaims>> =
        with(verifierOps) { verifyIssuance(jwtSignatureVerifier, unverifiedSdJwt) }

    override suspend fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwt.Issuance<JwtAndClaims>> =
        with(verifierOps) { verifyIssuance(jwtSignatureVerifier, unverifiedSdJwt) }

    override suspend fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        keyBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: String,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> =
        with(verifierOps) { verifyPresentation(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt) }

    override suspend fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier<JwtAndClaims>,
        keyBindingVerifier: KeyBindingVerifier<JwtAndClaims>,
        unverifiedSdJwt: JsonObject,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> =
        with(verifierOps) { verifyPresentation(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt) }

    companion object : DefaultSdJwtOps, UnverifiedIssuanceFrom by PlatformSdJwtUnverifiedIssuanceFrom {
        val NoSignatureValidation: JwtSignatureVerifier<JwtAndClaims> =
            JwtSignatureVerifier.noSignatureValidation { unverifiedJwt ->
                val (_, claims, _) = jwtClaims(unverifiedJwt).getOrThrow()
                unverifiedJwt to claims
            }
        val KeyBindingVerifierMustBePresent: KeyBindingVerifier<JwtAndClaims> =
            KeyBindingVerifier.mustBePresent(NoSignatureValidation)
    }
}

private val serializationOps = SdJwtSerializationOps<JwtAndClaims>(
    serializeJwt = { (jwt, _) -> jwt },
    hashAlgorithm = { (_, claims) ->
        claims[SdJwtSpec.CLAIM_SD_ALG]?.jsonPrimitive?.contentOrNull
            ?.let { checkNotNull(HashAlgorithm.fromString(it)) { "Unknown hash algorithm $it" } }
    },
)

private val presentationOps = SdJwtPresentationOps<JwtAndClaims>({ (_, claims) -> claims })

private val verifierOps: SdJwtVerifier<JwtAndClaims> = SdJwtVerifier({ (_, claims) -> claims })

@Suppress("unused")
fun interface UnverifiedIssuanceFrom {
    /**
     * A method for obtaining an [SdJwt.Issuance] given an [unverifiedSdJwt], without checking the signature
     * of the issuer.
     *
     * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verifyIssuance] the SD-JWT and
     * wants to just re-obtain an instance of the [SdJwt.Issuance] without repeating this verification
     *
     */
    fun unverifiedIssuanceFrom(unverifiedSdJwt: String): Result<SdJwt.Issuance<JwtAndClaims>>
}

/**
 * A method for obtaining an [SdJwt.Issuance] given an unverified SdJwt, without checking the signature
 * of the issuer.
 *
 * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verifyIssuance] the SD-JWT and
 * wants to just re-obtain an instance of the [SdJwt.Issuance] without repeating this verification
 *
 */
internal val PlatformSdJwtUnverifiedIssuanceFrom: UnverifiedIssuanceFrom = UnverifiedIssuanceFrom { unverifiedSdJwt ->
    runCatching {
        val (unverifiedJwt, unverifiedDisclosures) = StandardSerialization.parseIssuance(unverifiedSdJwt)
        val (_, jwtClaims, _) = jwtClaims(unverifiedJwt).getOrThrow()
        val disclosures = verifyDisclosures(jwtClaims, unverifiedDisclosures).getOrThrow()
        SdJwt.Issuance<JwtAndClaims>(unverifiedJwt to jwtClaims, disclosures)
    }
}
