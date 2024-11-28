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

/**
 * Fetches the metadata of an SD-JWT VC issuer.
 */
interface GetSdJwtVcIssuerMetadataOps {

    suspend fun HttpClient.getSdJwtVcIssuerMetadata(issuer: Url): SdJwtVcIssuerMetadata? = coroutineScope {
        val issuerMetadataUrl = issuerMetadataUrl(issuer)
        val httpResponse = get(issuerMetadataUrl)
        when {
            httpResponse.status.isSuccess() -> {
                httpResponse.body<SdJwtVcIssuerMetadata>()
                    .also { metadata ->
                        check(issuer == Url(metadata.issuer)) { "Issuer does not match the expected value" }
                    }
            }
            else -> null
        }
    }

    companion object : GetSdJwtVcIssuerMetadataOps {

        private fun issuerMetadataUrl(issuer: Url): Url =
            URLBuilder(issuer).apply {
                path("/.well-known/${SdJwtVcSpec.WELL_KNOWN_SUFFIX_JWT_VC_ISSUER}${issuer.pathSegments.joinToString("/")}")
            }.build()
    }
}

interface GetJwkSetKtorOps {

    suspend fun HttpClient.getJWKSet(jwksUri: Url): JsonObject? = getJWKSetAs(jwksUri)

    companion object : GetJwkSetKtorOps {
        suspend inline fun <reified JWK> HttpClient.getJWKSetAs(jwksUri: Url): JWK? = coroutineScope {
            val httpResponse = get(jwksUri)
            if (httpResponse.status.isSuccess()) httpResponse.body()
            else null
        }
    }
}
