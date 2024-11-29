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

import com.nimbusds.jose.jwk.RSAKey
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

internal class SdJwtVcIssuerMetaDataFetcherTest :
    MetadataOps {

    @Test
    fun `verify issuer jwks resolution fails when issuer is mismatched`() = runTest {
        val issuer = Url("https://example.com")
        val metadata = Url("https://example.com/.well-known/${SdJwtVcSpec.WELL_KNOWN_SUFFIX_JWT_VC_ISSUER}")

        var requests = 0
        val client = HttpClient { request ->
            when (request.url) {
                metadata -> {
                    requests += 1
                    respond(
                        issuerMetadata(Url("https://www.example.com"), issuerJwks, null).toString(),
                        HttpStatusCode.OK,
                        headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
                    )
                }

                else -> error("Unexpected request $request")
            }
        }

        assertFailsWith(IllegalStateException::class, "issuer does not match the expected value") {
            with(GetSdJwtVcIssuerMetadataOps) {
                client.getSdJwtVcIssuerMetadata(issuer, BySpec)
            }
        }
        assertEquals(1, requests)
    }

    @Test
    fun `verify issuer jwks resolution fails when both jwks and jwks_uri are present`() = runTest {
        val issuer = Url("https://example.com")
        val metadata = Url("https://example.com/.well-known/jwt-vc-issuer")
        val jwksUri = Url("https://example.com/keys.jwks")

        var requests = 0
        val client = HttpClient { request ->
            when (request.url) {
                metadata -> {
                    requests += 1
                    respond(
                        issuerMetadata(issuer, issuerJwks, jwksUri).toString(),
                        HttpStatusCode.OK,
                        headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
                    )
                }

                else -> error("Unexpected request $request")
            }
        }
        assertFailsWith(JsonConvertException::class, "either 'jwks' or 'jwks_uri' must be provided") {
            with(GetSdJwtVcIssuerMetadataOps) {
                client.getSdJwtVcIssuerMetadata(issuer, BySpec)
            }
        }
        assertEquals(1, requests)
    }

    @Test
    fun `verify issuer jwks is resolved by value`() = runTest {
        suspend fun test(issuer: Url, metadata: Url) {
            var requests = 0
            val client = HttpClient { request ->
                when (request.url) {
                    metadata -> {
                        requests += 1
                        respond(
                            issuerMetadata(issuer, issuerJwks, null).toString(),
                            HttpStatusCode.OK,
                            headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
                        )
                    }

                    else -> error("Unexpected request $request")
                }
            }

            val jwks = client.getJWKSetFromSdJwtVcIssuerMetadata(issuer)
            assertEquals(1, jwks.size())
            val jwk = assertNotNull(jwks.getKeyByKeyId("doc-signer-05-25-2022"))
            assertIs<RSAKey>(jwk)
            assertEquals(1, requests)
        }

        test(Url("https://example.com"), Url("https://example.com/.well-known/jwt-vc-issuer"))
        test(Url("https://example.com/tenant/1"), Url("https://example.com/.well-known/jwt-vc-issuer/tenant/1"))
    }

    @Test
    fun `verify issuer jwks is resolved by reference`() = runTest {
        suspend fun test(issuer: Url, metadata: Url) {
            val jwksUri = Url("https://example.com/keys.jwks")
            var requests = 0
            val client = HttpClient { request ->
                when (request.url) {
                    metadata -> {
                        requests += 1
                        respond(
                            issuerMetadata(issuer, null, jwksUri).toString(),
                            HttpStatusCode.OK,
                            headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
                        )
                    }

                    jwksUri -> {
                        requests += 1
                        respond(
                            issuerJwks.toString(),
                            HttpStatusCode.OK,
                            headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
                        )
                    }

                    else -> error("Unexpected request $request")
                }
            }

            val jwks = client.getJWKSetFromSdJwtVcIssuerMetadata(issuer)
            assertEquals(1, jwks.size())
            val jwk = assertNotNull(jwks.getKeyByKeyId("doc-signer-05-25-2022"))
            assertIs<RSAKey>(jwk)
            assertEquals(2, requests)
        }

        test(Url("https://example.com"), Url("https://example.com/.well-known/jwt-vc-issuer"))
        test(Url("https://example.com/tenant/1"), Url("https://example.com/.well-known/jwt-vc-issuer/tenant/1"))
    }

    @Suppress("TestFunctionName")
    private fun HttpClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
        }

    private val issuerJwks = Json.parseToJsonElement(
        """
            {
                  "keys":[
                     {
                        "kid":"doc-signer-05-25-2022",
                        "e":"AQAB",
                        "n":"${
            "nj3YJwsLUFl9BmpAbkOswCNVx17Eh9wMO-_AReZwBqfaWFcfGHrZXsIV2VMCNVNU8Tpb4obUaSXcRcQ-" +
                "VMsfQPJm9IzgtRdAY8NN8Xb7PEcYyklBjvTtuPbpzIaqyiUepzUXNDFuAOOkrIol3WmflPUUgMKULBN0E" +
                "Ud1fpOD70pRM0rlp_gg_WNUKoW1V-3keYUJoXH9NztEDm_D2MQXj9eGOJJ8yPgGL8PAZMLe2R7jb9TxOCPD" +
                "ED7tY_TU4nFPlxptw59A42mldEmViXsKQt60s1SLboazxFKveqXC_jpLUt22OC6GUG63p-REw-ZOr3r845z" +
                "50wMuzifQrMI9bQ"
        }",
                        "kty":"RSA"
                     }
                  ]
               }
        """.trimIndent(),
    ).jsonObject

    private fun issuerMetadata(issuer: Url, jwks: JsonObject?, jwksUri: Url?) = Json.parseToJsonElement(
        """
            {
               "issuer": "$issuer",
               "jwks": ${jwks?.toString()},
               "jwks_uri": ${jwksUri?.let { "\"$it\"" }}
            }
        """.trimIndent(),
    )
}
