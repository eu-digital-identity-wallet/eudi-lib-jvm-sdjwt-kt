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

import io.ktor.http.*

/**
 * The dns URI schema.
 */
internal const val SCHEMA_DNS = "dns"

/**
 * Gets the DNS name of the provided RFC-4501 DNS Uri.
 *
 * @return the DNS name or null in case [uri] is not a valid RFC-4501 DNS Uri
 */
internal fun dnsName(uri: Url): String? =
    when (uri.protocol.name) {
        SCHEMA_DNS -> {
            when {
                uri.fullPath.isBlank() -> ""
                uri.fullPath.isNotBlank() -> {
                    runCatching {
                        val parsed = Url(uri.fullPath.removePrefix("/"))
                        parsed.pathSegments.firstOrNull()
                    }.getOrNull()
                }

                else -> null
            }
        }

        else -> null
    }

/**
 * Gets the DNS name of the provided RFC-4501 DNS Uri.
 *
 * @return the DNS name or null in case [uri] is not a valid RFC-4501 DNS Uri
 */
internal fun dnsName(uri: String): String? = runCatching { dnsName(Url(uri)) }.getOrNull()
