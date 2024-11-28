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

import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.jwk.JWKSet as NimbusJWKSet

/**
 * Fetches the metadata of an SD-JWT VC issuer.
 */
internal class SdJwtVcIssuerMetaDataFetcher(private val httpClient: HttpClient) {

    suspend fun fetchMetaData(issuer: Url): SdJwtVcIssuerMetadata? = coroutineScope {
        metadata(issuer)?.also { metadata ->
            check(issuer == Url(metadata.issuer)) { "Issuer does not match the expected value" }
        }
    }

    suspend fun fetchJWKSetFromMetaData(issuer: Url): NimbusJWKSet? = coroutineScope {
        fetchMetaData(issuer)?.let { metadata ->
            val jwkSet = jwkSetOf(metadata)
            checkNotNull(jwkSet) { "Failed to obtain JWKSet from metadata" }
        }
    }

    private suspend fun metadata(issuer: Url): SdJwtVcIssuerMetadata? = coroutineScope {
        val issuerMetadataUrl = issuerMetadataUrl(issuer)
        val httpResponse = httpClient.get(issuerMetadataUrl)
        if (httpResponse.status.isSuccess()) httpResponse.body()
        else null
    }

    private suspend fun jwkSetOf(metadata: SdJwtVcIssuerMetadata): NimbusJWKSet? = coroutineScope {
        val jwks = when {
            metadata.jwksUri != null -> fetchJwkSet(Url(metadata.jwksUri))
            else -> metadata.jwks
        }
        jwks?.let {
            val jwksJsonString = it.toString()
            runCatching { NimbusJWKSet.parse(jwksJsonString) }.getOrNull()
        }
    }

    private suspend fun fetchJwkSet(jwksUri: Url): JsonObject? = coroutineScope {
        val httpResponse = httpClient.get(jwksUri)
        if (httpResponse.status.isSuccess()) httpResponse.body<JsonObject>()
        else null
    }
}

private fun issuerMetadataUrl(issuer: Url): Url =
    URLBuilder(issuer).apply {
        path("/.well-known/${SdJwtVcSpec.WELL_KNOWN_SUFFIX_JWT_VC_ISSUER}${issuer.pathSegments.joinToString("/")}")
    }.build()
