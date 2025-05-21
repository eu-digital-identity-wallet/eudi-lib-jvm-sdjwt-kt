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
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Alias of a method that creates a [HttpClient]
 */
typealias KtorHttpClientFactory = () -> HttpClient

/**
 * Factory which produces a [Ktor Http client][HttpClient]
 *
 * The actual engine will be peeked up by whatever
 * it is available in classpath
 *
 * @see [Ktor Client]("https://ktor.io/docs/client-dependencies.html#engine-dependency)
 */
val DefaultHttpClientFactory: KtorHttpClientFactory = {
    HttpClient {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }
}

/**
 * Performs a Get request to fetch Json data from a Url. In case the request fails with 404, it returns null.
 */
internal suspend inline fun <reified T> HttpClient.getJsonOrNull(url: Url): T? =
    try {
        get(url) {
            expectSuccess = true
            headers {
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }.body()
    } catch (error: ClientRequestException) {
        when (error.response.status) {
            HttpStatusCode.NotFound -> null
            else -> throw error
        }
    }
