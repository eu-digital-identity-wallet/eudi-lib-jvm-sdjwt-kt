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
            eyJ4NWMiOlsiTUlJRExUQ0NBcktnQXdJQkFnSVVMOHM1VHM2MzVrNk9oclJGTWxzU1JBU1lvNll3Q2dZSUtvWkl6ajBFQXdJd1hERWVNQndHQTFVRUF3d1ZVRWxFSUVsemMzVmxjaUJEUVNBdElGVlVJREF4TVMwd0t3WURWUVFLRENSRlZVUkpJRmRoYkd4bGRDQlNaV1psY21WdVkyVWdTVzF3YkdWdFpXNTBZWFJwYjI0eEN6QUpCZ05WQkFZVEFsVlVNQjRYRFRJME1URXlPVEV4TWpnek5Wb1hEVEkyTVRFeU9URXhNamd6TkZvd2FURWRNQnNHQTFVRUF3d1VSVlZFU1NCU1pXMXZkR1VnVm1WeWFXWnBaWEl4RERBS0JnTlZCQVVUQXpBd01URXRNQ3NHQTFVRUNnd2tSVlZFU1NCWFlXeHNaWFFnVW1WbVpYSmxibU5sSUVsdGNHeGxiV1Z1ZEdGMGFXOXVNUXN3Q1FZRFZRUUdFd0pWVkRCWk1CTUdCeXFHU000OUFnRUdDQ3FHU000OUF3RUhBMElBQkFXYTlVYXI3b1AxWmJHRmJzRkE0ZzMxUHpOR1pjd2gydlI3UENrazBZaUFMNGNocnNsZzljajFrQnlueVppN25acllnUE9KN3gwYXRSRmRreGZYanRDamdnRkRNSUlCUHpBTUJnTlZIUk1CQWY4RUFqQUFNQjhHQTFVZEl3UVlNQmFBRkxOc3VKRVhITmVrR21ZeGgwTGhpOEJBekpVYk1DY0dBMVVkRVFRZ01CNkNIR1JsZGk1cGMzTjFaWEl0WW1GamEyVnVaQzVsZFdScGR5NWtaWFl3RWdZRFZSMGxCQXN3Q1FZSEtJR01YUVVCQmpCREJnTlZIUjhFUERBNk1EaWdOcUEwaGpKb2RIUndjem92TDNCeVpYQnliMlF1Y0d0cExtVjFaR2wzTG1SbGRpOWpjbXd2Y0dsa1gwTkJYMVZVWHpBeExtTnliREFkQmdOVkhRNEVGZ1FVOGVIQS9NWHZreUNGNFExaW91WFAwc3BpTVVnd0RnWURWUjBQQVFIL0JBUURBZ2VBTUYwR0ExVWRFZ1JXTUZTR1VtaDBkSEJ6T2k4dloybDBhSFZpTG1OdmJTOWxkUzFrYVdkcGRHRnNMV2xrWlc1MGFYUjVMWGRoYkd4bGRDOWhjbU5vYVhSbFkzUjFjbVV0WVc1a0xYSmxabVZ5Wlc1alpTMW1jbUZ0WlhkdmNtc3dDZ1lJS29aSXpqMEVBd0lEYVFBd1pnSXhBSmpLU0EzQTdrWU9CWXdKQ09PY3JjYVJDRGVWVGZjdllZQ1I4QWp5blVpMjVJL3Rrc0RDRkE1K21hQ0xmbWtVS1FJeEFPVmpHc2dsdVF3VE41MG85N1dtaWxIYmxXNE44K3FBcm1zQkM4alRJdXRuS2ZjNHlaM3U1UTF1WllJbGJ0S1NyZz09Il0sImtpZCI6IjI3Mjg1NDYwOTcyMTEyMDczMjkzODg2ODI5ODc5OTI0NTAzNDE3NDEwMjkzODUzNCIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJwbGFjZV9vZl9iaXJ0aCI6eyJfc2QiOlsiQWpHU2NZNlJfaG5ZelJyNjU1eHh2YmpBNno5Y1pZU19hU1hkdm1ncnVBOCIsInl2THQyaU9RY0s5dTNUUkpuVFJTUXZkenNENEpPcUtNamhVV3FtWE9MTmsiXX0sIl9zZCI6WyI2RkdNYTllZk9CaVFFZ2JVNTZaVGhscDFfRklDZWpsdGdwV1d5NEZPYXlFIiwiOGJxMDE4NjZRZWZmR1V6UXo0dEJzUWRIVWRwUlhyLU5ZR0ZCT2swZ3BiMCIsIkxBRUc4WGNRdGcxY214VnhfMjViN0x1VE9YNUZLZ2pYRTZGVGF6SnFMV2ciLCJOdlVhQnEzUXNzWjMydV96RzJGZDNxY1N1Y0h5Ri1BdWVCTk5fbG1QZ3RvIiwiUjBQTkpySklmY0ZLNFRtOHMxNDJnTElfbnFyQXdySHFuWFlRRVUxX0RsZyIsIlhZUm82SzFZTk1xUVdMVzlHeGdsM0wtaTdmYkFqeXBFRkV3UkJmR1RJR00iLCJlb2JTTUw0QmdyT3NWZWlid25TNjFsZTk3MkR6VWdWYzVaeTBfZUM3Zl9NIiwiaEZKd2wxRkJkY0JWbjBuajhxNGY4SDk2LVNQaS1fREtQREpmYXJJWjZJbyIsImx2bFFGb2ViODhVVFJaLTFQel8zNFpBUFpGcE42UXZrUkhRdTVqMzQ0d2siLCJ0eTZWdlU3b3YyRkhKNVpDRzBkTDdYdGJ0TmRCdmlJSHZrZTFERlZoX0tnIiwidXVHUTl6ZXl6U1UtbFl4Y0tqbUhzN3hFR3lYUUV1eWRhSnB3SWdEVXR1OCIsInhDOHJycGhxNGM1MzhlLThpak8ybVE4djhjMkdiSjQ1UnRURGZCUnM1bG8iLCJ4aTA2TFlabFFpXzktNXd3dmtiNm1lNjktYVFuUVBhdDBHdGdOQkh0R0dVIiwienlmeHg3WEI4YW5NcmViVnI5NzN0S2FIX2VRdXJWeUVhOGhCTEU1Y1h4OCJdLCJhZGRyZXNzIjp7Il9zZCI6WyIxZzFDaVk0Yk1GamQ4SEJKcVBlbXQzVTk5Tnh6Q1BWamFySDduQWxOVEF3IiwiM2p6RlFmellseTliLVctMVA3NWRVM3FrZkhrVkR4N2ZTRGVBNjZrcksxbyIsIjRSTFZJREJTXzIxR1E1Zl9wcEdUSHdhalFqbGNyZlZCbUFIeFc0WkpVbTQiLCI4SXNSWEFoOW5pR3ZPZ3ZlMmp5aEFWTWI0WjZJRUN5U2JrZG1GaXpsYzkwIiwiWmQtUHpKUHE2M1E2SFdzS0hJRmI3YXAyeTNQUUJ4eE90eEUyc29hZy15dyIsInZxTmtRcUk1UXRqRnVYM2hlWEtNUUN3WEY0aU11NU5KTmhzR2lwMnpUcm8iXX0sInZjdCI6InVybjpldS5ldXJvcGEuZWMuZXVkaTpwaWQ6MSIsIl9zZF9hbGciOiJzaGEzLTI1NiIsImlzcyI6Imh0dHBzOi8vZGV2Lmlzc3Vlci1iYWNrZW5kLmV1ZGl3LmRldiIsImNuZiI6eyJqd2siOnsia3R5IjoiUlNBIiwiZSI6IkFRQUIiLCJ1c2UiOiJzaWciLCJraWQiOiJlZTFiMDgwMC02NDk1LTRlMDQtOTU2YS1kZWU0NDcxOTRiYjAiLCJpYXQiOjE3MzMxNDI0MTQsIm4iOiJqSFBwTWV6VEZPd2NfbVJGNm1iclNJbVRCR04zZHZyaEtHV3Vad1JILXVUYXJGOG5sTDE0cGFKd2lCV0RGSTg1OGstNzVXd2FhcnVSek96RkJsUFB1YUNyZDRzNU1PU3RsVDhmUUZLWFB2amxTdElOM04wVEN0N3ktT295WjBPaXpxN2p2bl9tOHN4bkZPZ19nSjdETlJSWC1FYnlaajNaVENSa05kd1lTMEREemxoUVpsVFBRUi12Z0NqOUdRWGhtdWs1VTB1dFQ5V0hiU2kzTGVuVHo5U19aYmZTTXg4ZXVQdVczZk5YWGpiWWtXT212YnpBT0JDQWg4WlNwYVJIaXluV21zdUhhUmxvdHVVbU9XTG55ZjZaTHBrZUE2UUlJeGxyWWF1NFhJb3hKYkRROFpyOXducWNHN3kzbk83dEROZzlGZnFDTEZiTkZRTzVYVVN4SFEifX0sImV4cCI6MTczNTczNDQxNSwiaWF0IjoxNzMzMTQyNDE1LCJhZ2VfZXF1YWxfb3Jfb3ZlciI6eyJfc2QiOlsiMUhOTjFVWmxDdmgtMG8wQWRVTDVFZkc5Rng1RTg4TmRfVUhYNXBJRF96dyJdfX0.tZ_yodEktENT7eTP6bnGfSkQTGSjt8PuqNzaovopFVFRUuzTMdy0yqv4AxPgD1rq_S5pVdRS-WllbHzDl5MMnQ~WyJxdjhzalN2MWNXZnNyc1JQckdTcHV3IiwiZmFtaWx5X25hbWUiLCJOZWFsIl0~WyI0dk9XNjJkU0Z1ak5hSThsV0pidHJ3IiwiZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJSeTRPQ2VTMXZPdTBpOHpzMGN0bHRBIiwiYmlydGhkYXRlIiwiMTk1NS0wNC0xMiJd~WyJzZ1M5YkRwbUtxbTFhVTI2dDdndUJRIiwiMTgiLHRydWVd~WyJxdExPa2NkTkhhbGdtUzhHd2ZYWllRIiwiYWdlX2luX3llYXJzIiw3MF0~WyJBdlhoNDdMT25xX1NORnlZTGV1a1B3IiwiYWdlX2JpcnRoX3llYXIiLCIxOTU1Il0~WyJXWVRkbk9Qak1kXzc0XzV1Tk0yRWpBIiwiYmlydGhfZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJDU2NLTnYxMXFrTmlwemtqR3RBcFJnIiwiYmlydGhfZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyI1djFvak1kbVJ0endBYkZWYktZQXN3IiwibG9jYWxpdHkiLCIxMDEgVHJhdW5lciJd~WyJISWkyVnFuQVdEeVplTGY5WDlMck9nIiwiY291bnRyeSIsIkFUIl0~WyJzcXBLYWFDc0k1ZUJVR3NUY3UwX2l3IiwiY291bnRyeSIsIkFUIl0~WyJTNzhnejVJZ0RuN1NlV0xpa0s3anR3IiwicmVnaW9uIiwiTG93ZXIgQXVzdHJpYSJd~WyJVTnZmdVZzT1N2SlkwY1AyU2o3V0ZRIiwibG9jYWxpdHkiLCJHZW1laW5kZSBCaWJlcmJhY2giXQ~WyJ4Ny05UTF0RGFsYkxwNjBHUnZPb1RRIiwicG9zdGFsX2NvZGUiLCIzMzMxIl0~WyJxU08wUlFUQlN6eTBHd2VOMUJiMWNBIiwic3RyZWV0X2FkZHJlc3MiLCJUcmF1bmVyIl0~WyJpM3UxYkV4SVNpUE1hdGlpSWZWbWR3IiwiaG91c2VfbnVtYmVyIiwiMTAxICJd~WyJfZlozQnQyLTdWc1VYUDVEMlEwVlV3IiwiZ2VuZGVyIiwibWFsZSJd~WyJXU2NqTmJiTjMxZmN2cXo5Z19YVFNBIiwibmF0aW9uYWxpdGllcyIsWyJBVCJdXQ~WyJLdWRQN1dNVTNFN2NJZTVQcTZFbDhRIiwiaXNzdWluZ19hdXRob3JpdHkiLCJHUiBBZG1pbmlzdHJhdGl2ZSBhdXRob3JpdHkiXQ~WyJpLThZdUlFc3djeGFfcjZwUnhWV3pnIiwiZG9jdW1lbnRfbnVtYmVyIiwiZmM2NjI1OTUtNGIwOC00ZDVmLThmYWUtNDVjZTNkYjkzNTNhIl0~WyJwWWhLRk1ONS1kMUNKTDhfMnJJb01nIiwiYWRtaW5pc3RyYXRpdmVfbnVtYmVyIiwiMWQxMTlhMjQtNGZjNy00MWY1LWE3YmQtZTkxYmU5ZTI2NzY0Il0~WyJ5TXQ4VmVzbUk4dTVLNEp1V24yR0tRIiwiaXNzdWluZ19jb3VudHJ5IiwiR1IiXQ~WyJJLWhYRDhVOTBPdDBfaGhJVVdoeHN3IiwiaXNzdWluZ19qdXJpc2RpY3Rpb24iLCJHUi1JIl0~
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

    fun printDisclosureMap(disclosureMap: DisclosuresPerClaimPath) {
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
