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
 * A URI that represents a DNS Name as per RFC-4501.
 */
@JvmInline
internal value class DnsUri private constructor(val value: Url) {

    /**
     * Gets the DNS name of this RFC-4501 DNS Uri.
     *
     * @return the DNS name
     */
    fun dnsName(): String = checkNotNull(dnsName(value))

    companion object {

        /**
         * Creates a new DNS Uri.
         *
         * @return a new DNS Uri or null in case [value] is not a valid RFC-4501 DNS Uri.
         */
        operator fun invoke(value: Url): DnsUri? = value.takeIf { dnsName(it) != null }?.let { DnsUri(it) }

        /**
         * Creates a new DNS Uri.
         *
         * @return a new DNS Uri or null in case [value] is not a valid RFC-4501 DNS Uri.
         */
        operator fun invoke(value: String): DnsUri? =
            runCatching {
                val uri = Url(value)
                uri.takeIf { dnsName(it) != null }?.let { DnsUri(it) }
            }.getOrNull()
    }
}

/**
 * The dns URI schema.
 */
internal const val DNS_URI_SCHEME = "dns"

/**
 * Gets the DNS name of the provided RFC-4501 DNS Uri.
 *
 * @return the DNS name or null in case [uri] is not a valid RFC-4501 DNS Uri
 */
private fun dnsName(uri: Url): String? =
    when (uri.protocol.name) {
        DNS_URI_SCHEME -> {
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
