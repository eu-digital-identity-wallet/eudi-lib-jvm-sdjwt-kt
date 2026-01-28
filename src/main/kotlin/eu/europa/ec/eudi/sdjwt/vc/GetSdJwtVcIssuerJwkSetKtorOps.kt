/*
 * Copyright (c) 2023-2026 European Commission
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

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

interface GetSdJwtVcIssuerJwkSetKtorOps : GetSdJwtVcIssuerMetadataOps {

    suspend fun HttpClient.getSdJwtIssuerKeySet(issuer: Url): JsonObject = coroutineScope {
        val metadata = getSdJwtVcIssuerMetadata(issuer)
        checkNotNull(metadata) { "Failed to obtain issuer metadata for $issuer" }
        val jwkSet = jwkSetOf(metadata)
        checkNotNull(jwkSet) { "Failed to obtain JWKSet from metadata" }
    }

    private suspend fun HttpClient.jwkSetOf(metadata: SdJwtVcIssuerMetadata): JsonObject? = coroutineScope {
        when {
            metadata.jwksUri != null -> getJWKSet(Url(metadata.jwksUri))
            else -> metadata.jwks
        }
    }

    private suspend fun HttpClient.getJWKSet(jwksUri: Url): JsonObject? {
        val httpResponse = get(jwksUri)
        return if (httpResponse.status.isSuccess()) httpResponse.body()
        else null
    }

    companion object : GetSdJwtVcIssuerJwkSetKtorOps
}
