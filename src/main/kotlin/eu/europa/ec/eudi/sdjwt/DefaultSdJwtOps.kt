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

import kotlinx.serialization.json.JsonObject

interface DefaultSdJwtOps : SdJwtVerifier, SdJwtSerializationOps<JwtAndClaims> {

    override fun SdJwt<JwtAndClaims>.serialize(): String =
        with(defaultSdJwtSerializationOps) { serialize() }
    override fun SdJwt<JwtAndClaims>.asJwsJsonObject(option: JwsSerializationOption, kbJwt: Jwt?): JsonObject =
        with(defaultSdJwtSerializationOps) { asJwsJsonObject(option, kbJwt) }

    companion object : DefaultSdJwtOps
}

private val defaultSdJwtSerializationOps = SdJwtSerializationOps<JwtAndClaims>({ (jwt, _) -> jwt })
