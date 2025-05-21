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

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Fetches the Type Metadata of a VCT.
 */
fun interface TypeMetadataFetcher {
    suspend fun fetch(vct: Vct): Result<SdJwtVcTypeMetadata>
}

/**
 * Fetches the Type Metadata of a VCT that is an HTTPS URL using Ktor.
 */
class HttpsTypeMetadataFetcher(
    private val httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
) : TypeMetadataFetcher {
    override suspend fun fetch(vct: Vct): Result<SdJwtVcTypeMetadata> = runCatching {
        val url = Url(vct.value)
        require(URLProtocol.HTTPS == url.protocol) { "$vct is not an https url" }
        httpClientFactory().use { httpClient -> httpClient.retrieveTypeMetadata(url) }
    }

    private suspend fun HttpClient.retrieveTypeMetadata(url: Url): SdJwtVcTypeMetadata {
        val httpResponse = get(url) {
            headers {
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }

        check(httpResponse.status.isSuccess()) { "Failed to fetch Type Metadata from $url: ${httpResponse.status.description}" }
        return httpResponse.body<SdJwtVcTypeMetadata>()
    }
}
