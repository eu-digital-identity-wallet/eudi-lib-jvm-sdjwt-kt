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

interface DefaultSdJwtOps :
    SdJwtVerifier,
    SdJwtSerializationOps<JwtAndClaims>,
    SdJwtPresentationOps<JwtAndClaims> {

    override fun SdJwt<JwtAndClaims>.serialize(): String =
        with(serializationOps) { serialize() }

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

    companion object : DefaultSdJwtOps
}

private val serializationOps = SdJwtSerializationOps<JwtAndClaims>({ (jwt, _) -> jwt })
private val presentationOps = SdJwtPresentationOps<JwtAndClaims>({ (_, claims) -> claims })
