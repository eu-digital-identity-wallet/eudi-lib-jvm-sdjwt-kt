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
import java.net.URI

/**
 * Gets the metadata of an SD-JWT VC issuer.
 */
interface GetSdJwtVcIssuerMetadataOps {

    suspend fun HttpClient.getSdJwtVcIssuerMetadata(issuer: Url): SdJwtVcIssuerMetadata? =
        alternatives.firstNotNullOfOrNull { formWellKnownUrl ->
            runCatching { getSdJwtVcIssuerMetadata(issuer, formWellKnownUrl) }.getOrNull()
        }

    companion object : GetSdJwtVcIssuerMetadataOps {
        internal val BySpec get() = FormWellKnownURL.prepend(SdJwtVcSpec.WELL_KNOWN_JWT_VC_ISSUER)
        internal val Alt get() = FormWellKnownURL.appendAtTheEnd(SdJwtVcSpec.WELL_KNOWN_JWT_VC_ISSUER)
        private val alternatives get() = listOf(BySpec, Alt)

        private fun SdJwtVcIssuerMetadata.ensureIssuerIs(expected: URI) {
            check(expected == issuer) { "Metadata do not contain expected ${SdJwtVcSpec.ISSUER}" }
        }

        internal suspend fun HttpClient.getSdJwtVcIssuerMetadata(
            issuer: Url,
            formWellKnownUrl: FormWellKnownURL,
        ): SdJwtVcIssuerMetadata? =
            coroutineScope {
                val expectedIssuer = issuer.toURI()

                val issuerMetadataUrl = formWellKnownUrl(issuer)
                val httpResponse = get(issuerMetadataUrl)
                when {
                    httpResponse.status.isSuccess() -> {
                        val metadata = httpResponse.body<SdJwtVcIssuerMetadata>()
                        metadata.apply { ensureIssuerIs(expectedIssuer) }
                    }

                    else -> null
                }
            }
    }
}

/**
 * Gets the JWKSet given a location
 */
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

internal fun interface FormWellKnownURL {
    /**
     * Forms a well-known [Url], given a [baseUrl]
     */
    operator fun invoke(baseUrl: Url): Url

    companion object {
        fun prepend(wellKnownPath: String): FormWellKnownURL =
            FormWellKnownURL { url ->
                val pathSegment =
                    buildString {
                        append("/${wellKnownPath.removePrefixAndSuffix("/")}")

                        val joinedSegments = url.segments.joinToString(separator = "/")
                        if (joinedSegments.isNotBlank()) {
                            append("/")
                        }
                        append(joinedSegments)
                    }
                URLBuilder(url).apply { path(pathSegment) }.build()
            }

        fun appendAtTheEnd(wellKnownPath: String): FormWellKnownURL =
            FormWellKnownURL { baseUrl: Url ->
                val segment = "/${wellKnownPath.removePrefixAndSuffix("/")}"
                URLBuilder(baseUrl).apply { appendPathSegments(segment, encodeSlash = false) }.build()
            }

        private fun String.removePrefixAndSuffix(s: CharSequence): String = removePrefix(s).removeSuffix(s)
    }
}
