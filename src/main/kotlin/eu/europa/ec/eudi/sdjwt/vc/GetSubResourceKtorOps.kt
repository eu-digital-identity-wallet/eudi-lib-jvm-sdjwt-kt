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

import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream

internal class GetSubResourceKtorOps(
    private val sriValidator: SRIValidator?,
) {
    suspend inline fun <reified T> HttpClient.getJsonOrNull(url: Url, expectedIntegrity: DocumentIntegrity?): T? =
        if (sriValidator == null || expectedIntegrity == null) {
            getJsonOrNull<T>(url)
        } else {
            val document = getJsonOrNull<ByteArray>(url)

            if (document != null) {
                require(sriValidator.isValid(expectedIntegrity, document)) {
                    "Integrity check failed for document at URL '$url'."
                }
            }

            ByteArrayInputStream(document).use { bytes ->
                Json.decodeFromStream<T>(bytes)
            }
        }
}
