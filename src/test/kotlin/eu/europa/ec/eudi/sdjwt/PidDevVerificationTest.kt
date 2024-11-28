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
            eyJraWQiOiJwaWQgZHMgLSAwMDYiLCJ0eXAiOiJ2YytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJwbGFjZV9vZl9iaXJ0aCI6eyJfc2QiOlsiWEZDLS1YMDZ3ZTlUYnFBZktNSWZRNHZGS1YzWG80eVU0TXdWN0pzUjVFVSIsInRVRWJnbS1FbS1UbXk5NlZybEVVekVCdTZPRHMwTTlBdTJfMEVMa2lRTWMiXX0sIl9zZCI6WyIwRllObUdRVVROekZmVVNSdlBxQXJmVk9TaHJmTGRzYzlSU1R2cjJYLUxnIiwiNVFNN3NRSlJRRGl5S2JyVXdEZlRKbUdwT3A2VmtGNXhiTFhIVTVtOHFYWSIsIjZJYWhsVFlmalJaeGczZzA1T3ZjME1PWUREa1pDVHQtbm0ySVlDNFNEeGMiLCI2Z0hkYzV2TWItVWYzMEpsYmRDWGoxQVF0MWhvVXRNQTV6TGM3eDRMQmFFIiwiQjJXM0xjR04wTV8xMFZNdGNvYTJlUERNTS0tTk9Ia0FUR19tUUhqWjhfZyIsIkljVFdGUUdsUFpIX05EeVEzVWQ4TXlGN25BdkoxQkU3UjBnUXdUSTF0cVkiLCJNN2I1NUxkbUlhWklCdzZqanBaaUNXVlNvTXVXdEZsX2w3T2JBZERUZ2tJIiwiUGFxWC1oVFhGLUJ0elkzWTN3MUc4QjhnNkxTaVFqS29iajlHRVp1TmI1byIsIlNiVFMzZXdGc2ZIOElKRUxvaTFKeHh0S0wxdW1Zc3Yta1NndldvRjNDVm8iLCJZU3BjSDVFUl9fV1ZzX0h6X3VOWDdmVVlVcFFMTmw5RGJfZDhPY1ZVVm9NIiwibnBkTzh6VWRfY19pTHRMVkR4YnhRR290dFE2RnRoeGJPYnFLbVdaeTl6WSIsInItTHJNaFRxSWRnNHYzOVQwNTBoX2RYMUhaUC1HWkZXVENQNmxCT0VCVlkiLCJ1eGdkZnFCYzlrbEJqX2hJSk5PQlZZNGtoamVwOF9zU2VmeU5MVW1tV0NZIiwid0tEN190M0t2VF85NEYyMmdVaEZhY3hiQ1NNSm1rNHJwRUNYdm1MRHFBWSJdLCJhZGRyZXNzIjp7Il9zZCI6WyI4QXA0cDR0SXA1WlJNRWlpakszMFFzY0wyYjZzMHc1S2FESmk1MXRWSk1rIiwiYXM5YXlSdHhyaUJpNkEwREh5cWtwVGs1dV8zNE9sN2hwVlVLdmhlM2paTSIsIm9lVURpNXFldmRxQ2llZ3NqQ1UtbkNkbHlkTGY5S3NQMEF5U1pZc1VZVzAiLCJxdjBycmVtTDJOMnVCVFJKYmpaeW9VdEl0bUlleFBDdUtQd2JYdnhGTG1ZIiwid2hFbEFwdlBOQzFIWF9XOGVjUEJESGJqM0RiOFJwMzBWMzA2azZZSmNCVSIsInhmRlpNd0ZNR0JiYkxEMTFCd3BSNGFPZlgyOEZkdlJkSnNtRElEcVhjZnMiXX0sInZjdCI6InVybjpldS5ldXJvcGEuZWMuZXVkaTpwaWQ6MSIsIl9zZF9hbGciOiJzaGEzLTI1NiIsImlzcyI6Imh0dHBzOi8vZGV2Lmlzc3Vlci1iYWNrZW5kLmV1ZGl3LmRldiIsImNuZiI6eyJqd2siOnsia3R5IjoiUlNBIiwiZSI6IkFRQUIiLCJ1c2UiOiJzaWciLCJraWQiOiI3YmUwZTE2YS05NDhkLTQwNDMtYTAyZS00OGVmYTljMTdjMTAiLCJpYXQiOjE3MzI4MjMzNzYsIm4iOiJtb243N2RYX2NlaGRnSVd1VmlQUVE3cUNQVHgzNWN6Umk4NHQ2T1FLSlVSTjNDT2hOb3lnUXJLVENqSVBETFZ3RDJoWEdUY1RSUHp1MU44bDBZX2VNRzBpS2RqYnZ1cjViUENaVWZlZzVNdnphMExlWnZjRmoyV2N1Z3BpY1BpQUlHRkJPUjFBc05RSjF6VGF3RXplbFZ6UW5qd3JsdEtyeHBNVGFubUg3R1M5S3JfMUNjT3BPWm9WQ3pmV1RpRDRIQldIaDVyeGtMMk5HR2UtWGhHZEZkMzM3MkpmLUZtLW42U3g0RjJ2azNRbHQ5SEVxaGYwVzhBT1c3QUhfeXFQblpvejdjTGpCOHYzdHV6SjEzZVJZc1Fia0xWdzVGemEzNE05X1FfQ3pCTk1nMW5sdXdMYmJGYmxycUdLcGdCcGphd1FHTUJwSHhzZVY3LTFUTFFCLXcifX0sImV4cCI6MTczNTQxNTM3NywiaWF0IjoxNzMyODIzMzc3LCJhZ2VfZXF1YWxfb3Jfb3ZlciI6eyJfc2QiOlsiLXJhLVRrRjMzWmlSTnN2QjRSSWhOa1BQRGFra3dFakViRHRZSzJ1NHJyayJdfX0.FHjlmLx8_E0ioEcRKiZeWLJRQsA1CfXDZorA_l5CFe0VW4v4nJi59nCND2FY_L2w59YiernOCvMUmnttujtaXA~WyI0VlpLeDQweGdZSTlCTVlXSW9ubXZRIiwiZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJUaF9sUjFUdGdBMGFRWjI4OXlpci1nIiwiZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJMNlZEVVA3d0NseVF2MEV6c00wamFnIiwiYmlydGhkYXRlIiwiMTk1NS0wNC0xMiJd~WyJLSlFrYlE3VGprWGdzYnVTLVExSjFBIiwiMTgiLHRydWVd~WyJ0LWlRNUQtV3VSeFRxLWFmTXFJMkdBIiwiYWdlX2luX3llYXJzIiw3MF0~WyJwNlZjMjZENy1DMG5uNjRzOThfMVhRIiwiYWdlX2JpcnRoX3llYXIiLCIxOTU1Il0~WyJYTzd1NU5jZU1ObnVpVTVHci1yb0dnIiwiYmlydGhfZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJoU2JaLU1kdEdvRE9Gd1RwcW1VakVRIiwiYmlydGhfZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJiSjZDSGNfakUxMnYyRmFkSGJReHhBIiwibG9jYWxpdHkiLCIxMDEgVHJhdW5lciJd~WyJONEswbm9BNTUxSjIwM2VpZ0xQMFBBIiwiY291bnRyeSIsIkFUIl0~WyJZMVlwQWE3WWxmNUczVXRzVUFnVFBnIiwiY291bnRyeSIsIkFUIl0~WyJoaEJQV0s3R2FSU0Y2cGR5QXJLV0VnIiwicmVnaW9uIiwiTG93ZXIgQXVzdHJpYSJd~WyJ5LXpIMC1VT2Qxa2txV0pyQWR1NnJBIiwibG9jYWxpdHkiLCJHZW1laW5kZSBCaWJlcmJhY2giXQ~WyJ2d1VhQ2pkMzE1LUdYUnQwMUhtME93IiwicG9zdGFsX2NvZGUiLCIzMzMxIl0~WyJGZTZ4VXIxQ0JsLTVMODVwSEtqS0F3Iiwic3RyZWV0X2FkZHJlc3MiLCJUcmF1bmVyIl0~WyJXRlZmWlY0bzMwLThtTldfQjdXdmdRIiwiaG91c2VfbnVtYmVyIiwiMTAxICJd~WyJVU0hFVnVjNEhnMmlVYzBVUWpwNW93IiwiZ2VuZGVyIiwibWFsZSJd~WyJkelZNdkViTjl3QkFLMTAzTXlET1RBIiwibmF0aW9uYWxpdGllcyIsWyJBVCJdXQ~WyJzdDFQOW9IaDNpbGM1U2lZRHdkVzRBIiwiaXNzdWluZ19hdXRob3JpdHkiLCJHUiBBZG1pbmlzdHJhdGl2ZSBhdXRob3JpdHkiXQ~WyJVYmh0eUN0ZngyaVhNd3BZTURtQ1pBIiwiZG9jdW1lbnRfbnVtYmVyIiwiY2UzZTg4MmQtOTcxZi00Njk0LWE4YzQtMGQ1NTcxOWZiNmVmIl0~WyJmYi1DeXJ4UGpqV25kTU9kc0pHWDdnIiwiYWRtaW5pc3RyYXRpdmVfbnVtYmVyIiwiMzZmNDMyNzEtYjcxNi00MTJiLTlhMDMtOWViYmM5ZTY5M2YzIl0~WyJSaHJVbXB3M3ZoYnFRRVU5QVk5Nk5RIiwiaXNzdWluZ19jb3VudHJ5IiwiR1IiXQ~WyJvN2c4LWU3WDJ4cjJsQzZtR01aTVZBIiwiaXNzdWluZ19qdXJpc2RpY3Rpb24iLCJHUi1JIl0~
    """.trimIndent()

    val pid2 = """
            eyJhbGciOiAiRVMyNTYiLCAidHlwIjogInZjK3NkLWp3dCJ9.eyJfc2QiOiBbIjZfRUYxcV9ERURsMTVyOTRCQm1TaTZNalFaLWpJMVhLbzNfdW1xQVhlb0UiLCAiRnJ3WDN3R2xlbWVhdUVQMnRsSDBtS1lZYU9LOTVISVFqYmI0WUU3aWVIQSIsICJLNmJNTnpGMU5PeHk1STN4TkdPcGozSEVYVEtac3FXM0NtQjZLOWVBN1NzIiwgImdmN0lyMUJ5SnRRQ0tERm85dm45eGJ4UmowX2ZaTnFXZTB6eDJsNHRVcUUiLCAicDJQRExuRDB0ZnlVVy1acThPeGJjeG50RVUtQTVtODJWTGNQUm9TY0pyVSIsICJxSkliZjYxRzVnTEF6bzlfa1NlcFB3bTh0MEpHbngyOXlqRDVmejhPUnJFIl0sICJpc3MiOiAiaHR0cHM6Ly9kZXYuaXNzdWVyLmV1ZGl3LmRldiIsICJqdGkiOiAiMGEzOTk5NTAtYzliOS00OTFhLWI0OGItYjY2NDUxMTM1ZTBkIiwgImlhdCI6IDIwMDQ3LCAiZXhwIjogMjAxMzcsICJzdGF0dXMiOiAidmFsaWRhdGlvbiBzdGF0dXMgVVJMIiwgInZjdCI6ICJ1cm46ZXUuZXVyb3BhLmVjLmV1ZGkucGlkLjEiLCAiX3NkX2FsZyI6ICJzaGEtMjU2IiwgImNuZiI6IHsiandrIjogeyJrdHkiOiAiRUMiLCAiY3J2IjogIlAtMjU2IiwgIngiOiAid1V1UDJPbHdIZWZlRS1ZMTZXajdQSEF6WjBKQVF5ZXZxV01mZDUtS21LWSIsICJ5IjogIllXLWI4TzNVazNOVXJrOW9acEFUMWxhUGVBZ2lOUXdEY290V2l3QkZRNkUifX19.6Ub00iVL7rSpCN82wRUkT4kv-OgvTzOdIrUHeFKX29IbXKX4GpeJWd5hUmYCU8tOnWvhFcffONiGQgsWJIwYuw~WyJGZl9sak93Z25WTVFYazctbGk4N3F3IiwgImZhbWlseV9uYW1lIiwgIkZhbWlseSBOYW1lIFRlc3RlciJd~WyJ0Wk9aaEtOYnUyWm9DWHlua2dfQmR3IiwgImdpdmVuX25hbWUiLCAiR2l2ZW4gTmFtZSBJc3N1ZXIiXQ~WyJISkJqd2JvbmdnVnJ1QkJFT0xkenpBIiwgImJpcnRoZGF0ZSIsICIxOTk5LTExLTIwIl0~WyI0emdlSUNQQngzLUc3RDNyc3RkM2N3IiwgImlzc3VpbmdfYXV0aG9yaXR5IiwgIlRlc3QgUElEIGlzc3VlciJd~WyIyS0VzSEhBMjFoVjg5TkllcVhwbUpRIiwgImlzc3VpbmdfY291bnRyeSIsICJGQyJd~WyJ6Vm9rQlBmS052SmpwRU5kbG1rdUFBIiwgImFnZV9lcXVhbF9vcl9vdmVyIiwgeyIxOCI6IHRydWV9XQ~
    """.trimIndent()

    @Test @Ignore
    fun testKotlin() = doTest(pid1, enableLogging = false)

    @Test @Ignore
    fun testPy() = doTest(pid2, enableLogging = false)

    private fun doTest(unverifiedSdJwtVc: String, enableLogging: Boolean = false) = runTest {
        val verifier = SdJwtVcVerifier.usingX5cOrIssuerMetadata(
            httpClientFactory = { createHttpClient(enableLogging = enableLogging) },
            x509CertificateTrust = { _ -> true },
        )

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
