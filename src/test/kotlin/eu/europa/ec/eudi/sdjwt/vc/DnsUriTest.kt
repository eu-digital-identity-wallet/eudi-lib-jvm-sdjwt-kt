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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test cases for [DnsUri].
 *
 * Examples taken from [https://www.rfc-editor.org/rfc/rfc4501.txt](https://www.rfc-editor.org/rfc/rfc4501.txt).
 */
internal class DnsUriTest {

    @Test
    internal fun `parses valid dns uris successfully`() {
        validDnsUris.forEach { (uri, dnsName) ->
            println("testing $uri")
            val dnsUri = assertNotNull(DnsUri(uri))
            assertEquals(dnsName, dnsUri.dnsName())
        }
    }

    @Test
    internal fun `does not parse invalid dns uris`() {
        assertNull(DnsUri(""))
        assertNull(DnsUri("http://google.com"))
        assertNull(DnsUri("https://google.com"))
        assertNull(DnsUri("ftp://google.com"))
    }

    private val validDnsUris = listOf(
        "dns:www.example.org.?clAsS=IN;tYpE=A" to "www.example.org.",
        "dns:www.example.org" to "www.example.org",
        "dns:simon.example.org?type=CERT" to "simon.example.org",
        "dns://192.168.1.1/ftp.example.org?type=A" to "ftp.example.org",
        "dns:world%20wide%20web.example%5c.domain.org?TYPE=TXT" to "world wide web.example\\.domain.org",
        "dns://fw.example.org/*.%20%00.example?type=TXT" to "*. \u0000.example",
        "dns://fw.example.org" to "",
        "dns:" to "",
    )
}
