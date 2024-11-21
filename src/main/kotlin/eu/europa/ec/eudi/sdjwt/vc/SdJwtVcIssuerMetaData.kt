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

import com.nimbusds.jose.jwk.JWKSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.net.URI

/**
 * The metadata of an SD-JWT VC issuer.
 */
internal data class SdJwtVcIssuerMetaData(
    val issuer: URI,
    val jwkSet: JWKSet,
)

/**
 * Fetches the metadata of an SD-JWT VC issuer.
 */
internal class SdJwtVcIssuerMetaDataFetcher(private val httpClient: HttpClient) {

    suspend fun fetchMetaData(issuer: Url): SdJwtVcIssuerMetaData? = coroutineScope {
        metadata(issuer)?.let { metadata ->
            check(issuer == Url(metadata.issuer)) { "Issuer does not match the expected value" }
            val jwkSet = jwkSetOf(metadata)
            checkNotNull(jwkSet) { "Failed to obtain JWKSet from metadata" }
            SdJwtVcIssuerMetaData(issuer.toURI(), jwkSet)
        }
    }

    private suspend fun metadata(issuer: Url): SdJwtVcIssuerMetadataTO? = coroutineScope {
        val issuerMetadataUrl = issuerMetadataUrl(issuer)
        val httpResponse = httpClient.get(issuerMetadataUrl)
        if (httpResponse.status.isSuccess()) httpResponse.body<SdJwtVcIssuerMetadataTO>()
        else null
    }
    private suspend fun jwkSetOf(metadata: SdJwtVcIssuerMetadataTO): JWKSet? = coroutineScope {
        check((metadata.jwks != null) xor (metadata.jwksUri != null)) {
            "either 'jwks' or 'jwks_uri' must be provided"
        }
        val jwksJsonString = if (metadata.jwks != null) {
            metadata.jwks.toString()
        } else {
            requireNotNull(metadata.jwksUri)
            fetchJwkSet(metadata.jwksUri).toString()
        }
        runCatching { JWKSet.parse(jwksJsonString) }.getOrNull()
    }

    private suspend fun fetchJwkSet(jwksUri: String): JsonObject? = coroutineScope {
        val httpResponse = httpClient.get(jwksUri)
        if (httpResponse.status.isSuccess()) httpResponse.body<JsonObject>()
        else null
    }
}

private fun issuerMetadataUrl(issuer: Url): Url =
    URLBuilder(issuer).apply {
        path("/.well-known/jwt-vc-issuer${issuer.pathSegments.joinToString("/")}")
    }.build()

@Serializable
private data class SdJwtVcIssuerMetadataTO(
    @SerialName("issuer") val issuer: String,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("jwks") val jwks: JsonObject? = null,
)
