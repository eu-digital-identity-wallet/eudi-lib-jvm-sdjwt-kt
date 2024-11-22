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
package eu.europa.ec.eudi.sdjwt

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifier
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.test.Ignore
import kotlin.test.Test

class PidDevVerificationTest : Printer {

    val pid1 = """
            eyJ0eXAiOiJ2YytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJfc2QiOlsiQ3lKR3d0WDc2VmpJMVI4S0Z5a0JNcWw4aHNkYXRkckFWY2dXdE1FRXlrYyIsIkZDOThzQ2UxbkkxcllJV0pJSlBxVjB3V0xXVWhLbEo2SDE5aVhjajhwQVkiLCJKTmdOSlg0S3lwcnAyUTJzMHV6QlVhbXEtNnQxa2VHRVZfak5SSWFnZjMwIiwiVzRNemRnVmVRaTZTUHlRX2FyelV6WGEyZ2ZydU5ETXNhc0JXYjlHNW5jdyIsImZZeWszWUJqVDlYRVIxVHhtaE9WNW0yZzJJTm5LdTNTQ0hFU2xGZVI0MVkiLCJuR2o1MFVFczR0UnVOSUZlMnVPMDVHWTZQS0U5SXdXQU8yZ0FpbUFSQlZvIiwibmljMmhmM1JrRm1sV012WVpWOWJNOVZJbEwtZWJKV2h6eDQzQk9SM2RjYyIsInd2VDl4b05VczNHckZIRTZCTVJZV3h1ZjFISlpUM19KQ21qZ01adHdXVEkiXSwidmN0IjoiZXUuZXVyb3BhLmVjLmV1ZGkucGlkLjEiLCJfc2RfYWxnIjoic2hhMy0yNTYiLCJpc3MiOiJodHRwczovL2Rldi5pc3N1ZXItYmFja2VuZC5ldWRpdy5kZXYiLCJjbmYiOnsiandrIjp7Imt0eSI6IlJTQSIsImUiOiJBUUFCIiwidXNlIjoic2lnIiwia2lkIjoiZjhiOTQ0ZjYtZWY4OC00MWE5LWJkM2UtNjliNTQ3ZTI4ZTc2IiwiaWF0IjoxNzMyMTAxMjg1LCJuIjoidVV5V25TVkdPRGJNMGVOQUh3MXJmRDJXYW9wUHpES2UtTHJjbnBvYnlwWVRyQTliUmNsQVhsVUtYZW5Jck01VnZZWlFuWGpRUU9DYmo0dl9ZM2UzZzJkaGQzSkREamE4d3F2VnVwVHlXMndsUU9nTDdyQ3IyVUYwUzVWejEtVFBlXzBSd2s4dmpKM2ZvZHJCWUxva0J5UXl0T0hvRVFWVDYwZGNNRUM5bEJsXy1OU0s5UENjeExzakVUSWcyaWN6OTI1cWhkTFR1cG1uYmpqM2RuOS0tNUplcEs3cE9FcWktX2pBbTU3VkNLRDU4clMxTW1JZl85NFhGVnhNbm9hS3FTNmdKbk8tZ05yRXpGYzlWZXIyMlJnTEZvY2x2NC0waVRNMFhYbkJBQzJWTDhQZk5uaGZjYTNxcnBjd2x2MGRkYWtZOHlBeUlsbG9ldDJsNllaOG9RIn19LCJleHAiOjE3MzQ2OTMyODYsImlhdCI6MTczMjEwMTI4NiwiYWdlX2VxdWFsX29yX292ZXIiOnsiX3NkIjpbIkl1cS1YUzFCeUlyaUtSSkdwc2ZCd2FPQ2w2NVctWGd4X1p2cVFCbGNkWWMiXX19.8dWnQm64W11vsdY0IqJh6TUuneyOMcvxqvRMu1ZY5hPOiPyL0D7aGoPSZl7lbgpBWOFtvzmruNoKePgpT5IGMw~WyJTSWhZMnl3ay14SFJzV05xNWRHckV3IiwiaXNzdWFuY2VfZGF0ZSIsIjIwMjQtMTEtMjAiXQ~WyJ5NHRjQm9tX214QVI0N2czN25iOFdRIiwiZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJFcF9aVXBVVXhoSDI2Yk1YUE5CaGpRIiwiZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJWeGJNdDc3UU9BSDItck03YlpNbHVRIiwiYmlydGhkYXRlIiwiMTk1NS0wNC0xMiJd~WyJUSWFFRXhfTXh5OUdvbjJhSHlLSHB3IiwiMTgiLHRydWVd~WyJ2RVB2NjhZNGJDOUpSZnoyOGNpS2h3IiwiZ2VuZGVyIiwxXQ~WyJQcEVwNzgtOXRpRFVqUnRlVmQ4S0pRIiwiYWdlX2luX3llYXJzIiw3MF0~WyJ0UFlQWk9PM1ZLcEJHY3ZxY3ZFN1B3IiwiYmlydGhkYXRlX3llYXIiLCIxOTU1Il0~WyJxd0tqcjllY3ZwX2laOWktRWpHZE1RIiwiY291bnRyeSIsIkFUIl0~WyJWUXdjUXZLcWo3dGh6OFB3aXJIQ3JBIiwicmVnaW9uIiwiTG93ZXIgQXVzdHJpYSJd~WyJrTEN4OG1FR3RLZDM2UXBOaFg2Q3BnIiwibG9jYWxpdHkiLCJHZW1laW5kZSBCaWJlcmJhY2giXQ~WyIzZy13cmxaU0drZ1dYbm9qaFBDTk1BIiwicG9zdGFsX2NvZGUiLCIzMzMxIl0~WyI2TkFUZGVFTjdiZDZSR0MtU3ZnM0RBIiwic3RyZWV0X2FkZHJlc3MiLCIxMDEgVHJhdW5lciJd~WyI3VmRDYlA5b2lFR19YNjRUWlU4dnFBIiwiYWRkcmVzcyIseyJfc2QiOlsiYVUwd2lWY2JSNzA2ekZSZ1VkR1A5bWhqYnpnV1diR2xYYWJqbnROTDZXbyIsImV1WTN6OHFYT1hDMmYycTZTX21mV0ZWUU5ZcWxPd01XeWVjaVlaaGp1TkEiLCJrUnVKd2pPNlR5YlhoVnRYLXRZcFc0YWlpRVZKdzFVdG9rek4tYTNkNEdNIiwic2lIM1BRUkVsU2laTk9xcm9LNG0yN3Foc2dyRy1sb0p0NFdnZmtmZ1RZQSIsInk4RTRMZ1dyTVlIMFV2V2JzNmhDVEc0NzBGaml6Rm0wbGlVdTk2dDQtR2MiXX1d~
    """.trimIndent()

    val pid2 = """
            eyJhbGciOiAiRVMyNTYiLCAidHlwIjogInZjK3NkLWp3dCJ9.eyJfc2QiOiBbIjZfRUYxcV9ERURsMTVyOTRCQm1TaTZNalFaLWpJMVhLbzNfdW1xQVhlb0UiLCAiRnJ3WDN3R2xlbWVhdUVQMnRsSDBtS1lZYU9LOTVISVFqYmI0WUU3aWVIQSIsICJLNmJNTnpGMU5PeHk1STN4TkdPcGozSEVYVEtac3FXM0NtQjZLOWVBN1NzIiwgImdmN0lyMUJ5SnRRQ0tERm85dm45eGJ4UmowX2ZaTnFXZTB6eDJsNHRVcUUiLCAicDJQRExuRDB0ZnlVVy1acThPeGJjeG50RVUtQTVtODJWTGNQUm9TY0pyVSIsICJxSkliZjYxRzVnTEF6bzlfa1NlcFB3bTh0MEpHbngyOXlqRDVmejhPUnJFIl0sICJpc3MiOiAiaHR0cHM6Ly9kZXYuaXNzdWVyLmV1ZGl3LmRldiIsICJqdGkiOiAiMGEzOTk5NTAtYzliOS00OTFhLWI0OGItYjY2NDUxMTM1ZTBkIiwgImlhdCI6IDIwMDQ3LCAiZXhwIjogMjAxMzcsICJzdGF0dXMiOiAidmFsaWRhdGlvbiBzdGF0dXMgVVJMIiwgInZjdCI6ICJ1cm46ZXUuZXVyb3BhLmVjLmV1ZGkucGlkLjEiLCAiX3NkX2FsZyI6ICJzaGEtMjU2IiwgImNuZiI6IHsiandrIjogeyJrdHkiOiAiRUMiLCAiY3J2IjogIlAtMjU2IiwgIngiOiAid1V1UDJPbHdIZWZlRS1ZMTZXajdQSEF6WjBKQVF5ZXZxV01mZDUtS21LWSIsICJ5IjogIllXLWI4TzNVazNOVXJrOW9acEFUMWxhUGVBZ2lOUXdEY290V2l3QkZRNkUifX19.6Ub00iVL7rSpCN82wRUkT4kv-OgvTzOdIrUHeFKX29IbXKX4GpeJWd5hUmYCU8tOnWvhFcffONiGQgsWJIwYuw~WyJGZl9sak93Z25WTVFYazctbGk4N3F3IiwgImZhbWlseV9uYW1lIiwgIkZhbWlseSBOYW1lIFRlc3RlciJd~WyJ0Wk9aaEtOYnUyWm9DWHlua2dfQmR3IiwgImdpdmVuX25hbWUiLCAiR2l2ZW4gTmFtZSBJc3N1ZXIiXQ~WyJISkJqd2JvbmdnVnJ1QkJFT0xkenpBIiwgImJpcnRoZGF0ZSIsICIxOTk5LTExLTIwIl0~WyI0emdlSUNQQngzLUc3RDNyc3RkM2N3IiwgImlzc3VpbmdfYXV0aG9yaXR5IiwgIlRlc3QgUElEIGlzc3VlciJd~WyIyS0VzSEhBMjFoVjg5TkllcVhwbUpRIiwgImlzc3VpbmdfY291bnRyeSIsICJGQyJd~WyJ6Vm9rQlBmS052SmpwRU5kbG1rdUFBIiwgImFnZV9lcXVhbF9vcl9vdmVyIiwgeyIxOCI6IHRydWV9XQ~
    """.trimIndent()

    @Test @Ignore
    fun testKotlin() = doTest(pid1, enableLogging = false)

    @Test @Ignore
    fun testPy() = doTest(pid2, enableLogging = false)

    private fun doTest(unverifiedSdJwtVc: String, enableLogging: Boolean = false) = runTest {
        val verifier = SdJwtVcVerifier.builder()
            .enableIssuerMetadataResolution { createHttpClient(enableLogging = enableLogging) }
            .enableX509CertificateTrust { _ -> true }
            .build()

        val issuedSdJwt = try {
            val tmp = verifier.verifyIssuance(unverifiedSdJwtVc).getOrThrow()
            SdJwt.Issuance(tmp.jwt.second, tmp.disclosures)
        } catch (e: Throwable) {
            printError(unverifiedSdJwtVc, e)
            throw e
        }

        prettyPrint(issuedSdJwt)

        // Recreate claims & calculate the disclosures map
        val (originalJson, disclosureMap) =
            run {
                val (originalClaims, disclosureMap) = issuedSdJwt.recreateClaimsAndDisclosuresPerClaim { it }
                JsonObject(originalClaims) to disclosureMap
            }

        printRecreatedClaims(originalJson)
        printDisclosureMap(disclosureMap)
    }
}

private interface Printer {

    fun prettyPrint(issuedSdJwt: SdJwt.Issuance<Claims>) {
        // Output the debug info
        printHeader("Debug info")
        issuedSdJwt.prettyPrint { it }
    }

    fun printRecreatedClaims(claims: JsonObject) {
        printHeader("Recreated claims")
        println(json.encodeToString(claims))
    }

    fun printDisclosureMap(disclosureMap: DisclosuresPerClaim) {
        printHeader("Disclosure map")
        disclosureMap.prettyPrint()
    }

    fun printHeader(s: String) {
        repeat(5) { println() }
        println("========== $s ==========")
    }

    fun printError(unverifiedSdJwtVc: String, e: Throwable) {
        println("Problem!!!!")
        try {
            val unverified = SdJwt.unverifiedIssuanceFrom(unverifiedSdJwtVc).getOrNull()
            if (unverified != null) {
                println(SignedJWT.parse(unverified.jwt.first).header)
                unverified.prettyPrint { it.second }
            }
        } catch (e1: Throwable) {
            //
        }
        throw e
    }
}

internal fun createHttpClient(enableLogging: Boolean = true): HttpClient = HttpClient(Java) {
    install(ContentNegotiation) {
        json(json)
    }
    install(HttpCookies)
    if (enableLogging) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
}
