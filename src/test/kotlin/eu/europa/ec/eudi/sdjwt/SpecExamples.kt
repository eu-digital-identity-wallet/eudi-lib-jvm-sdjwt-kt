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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals

class SpecExamples {

    private val disclosuresCreator = DisclosuresCreator(numOfDecoysLimit = 0)

    @Test
    fun `Example1 SD-JWT`() = test("Example 1 SD-JWT", 10) {
        sdJwt {
            sub("user_42")
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            flat {
                put("given_name", "John")
                put("family_name", "Doe")
                put("email", "johndoe@example.com")
                put("phone_number", "+1-202-555-0101")
                put("phone_number_verified", true)
                putJsonObject("address") {
                    put("street_address", "123 Main St")
                    put("locality", "Anytown")
                    put("region", "Anystate")
                    put("country", "US")
                }
                put("birthdate", "1940-01-01")
                put("updated_at", 1570000000)
            }

            sdArray("nationalities") {
                sd("US")
                sd("DE")
            }
        }
    }

    @Test
    fun `Option 1 Flat SD-JWT`() = test("Option 1 Flat SD-JWT", 1) {
        sdJwt {
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            flat {
                putJsonObject("address") {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }
    }

    @Test
    fun `Option 2 Structured SD-JWT`() = test("Option 2 Structured SD-JWT", 4) {
        sdJwt {
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            structured("address") {
                flat {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }
    }

    @Test
    fun `Option 3 Recursively SD-JWT`() = test("Option 3 Recursively SD-JWT", 5) {
        sdJwt {
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            recursively("address") {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
            }
        }
    }

    @Test
    fun `Example 2 Handling Structured Claims`() = test("Example 2 Handling Structured Claims", 7) {
        sdJwt {
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            flat {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("given_name", "太郎")
                put("family_name", "山田")
                put("email", "\"unusual email address\"@example.jp")
                put("phone_number", "+81-80-1234-5678")
                putJsonObject("address") {
                    put("street_address", "東京都港区芝公園４丁目２−８")
                    put("locality", "東京都")
                    put("region", "港区")
                    put("country", "JP")
                }
                put("birthdate", "1940-01-01")
            }
        }
    }

    @Test
    fun example3() {
        val unverifiedSdJwt = """
            eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIi1hU3puSWQ5bVdNOG9jdVFvbENsbHN4V
            mdncTEtdkhXNE90bmhVdFZtV3ciLCAiSUticllObjN2QTdXRUZyeXN2YmRCSmpERFVfR
            XZRSXIwVzE4dlRScFVTZyIsICJvdGt4dVQxNG5CaXd6TkozTVBhT2l0T2w5cFZuWE9hR
            UhhbF94a3lOZktJIl0sICJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiL
            CAiaWF0IjogMTY4MzAwMDAwMCwgImV4cCI6IDE4ODMwMDAwMDAsICJ2ZXJpZmllZF9jb
            GFpbXMiOiB7InZlcmlmaWNhdGlvbiI6IHsiX3NkIjogWyI3aDRVRTlxU2N2REtvZFhWQ
            3VvS2ZLQkpwVkJmWE1GX1RtQUdWYVplM1NjIiwgInZUd2UzcmFISUZZZ0ZBM3hhVUQyY
            U14Rno1b0RvOGlCdTA1cUtsT2c5THciXSwgInRydXN0X2ZyYW1ld29yayI6ICJkZV9hb
            WwiLCAiZXZpZGVuY2UiOiBbeyIuLi4iOiAidFlKMFREdWN5WlpDUk1iUk9HNHFSTzV2a
            1BTRlJ4RmhVRUxjMThDU2wzayJ9XX0sICJjbGFpbXMiOiB7Il9zZCI6IFsiUmlPaUNuN
            l93NVpIYWFka1FNcmNRSmYwSnRlNVJ3dXJSczU0MjMxRFRsbyIsICJTXzQ5OGJicEt6Q
            jZFYW5mdHNzMHhjN2NPYW9uZVJyM3BLcjdOZFJtc01vIiwgIldOQS1VTks3Rl96aHNBY
            jlzeVdPNklJUTF1SGxUbU9VOHI4Q3ZKMGNJTWsiLCAiV3hoX3NWM2lSSDliZ3JUQkppL
            WFZSE5DTHQtdmpoWDFzZC1pZ09mXzlsayIsICJfTy13SmlIM2VuU0I0Uk9IbnRUb1FUO
            EptTHR6LW1oTzJmMWM4OVhvZXJRIiwgImh2RFhod21HY0pRc0JDQTJPdGp1TEFjd0FNc
            ERzYVUwbmtvdmNLT3FXTkUiXX19LCAiX3NkX2FsZyI6ICJzaGEtMjU2In0.Xtpp8nvAq
            22k6wNRiYHGRoRnkn3EBaHdjcaa0sf0sYjCiyZnmSRlxv_C72gRwfVQkSA36ID_I46QS
            TZvBrgm3g~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgInRpbWUiLCAiMjAxMi0wNC
            0yM1QxODoyNVoiXQ~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgeyJfc2QiOiBbIjl
            3cGpWUFd1RDdQSzBuc1FETDhCMDZsbWRnVjNMVnliaEh5ZFFwVE55TEkiLCAiRzVFbmh
            PQU9vVTlYXzZRTU52ekZYanBFQV9SYy1BRXRtMWJHX3djYUtJayIsICJJaHdGcldVQjY
            zUmNacTl5dmdaMFhQYzdHb3doM08ya3FYZUJJc3dnMUI0IiwgIldweFE0SFNvRXRjVG1
            DQ0tPZURzbEJfZW11Y1lMejJvTzhvSE5yMWJFVlEiXX1d~WyJlSThaV205UW5LUHBOUG
            VOZW5IZGhRIiwgIm1ldGhvZCIsICJwaXBwIl0~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YW
            pBIiwgImdpdmVuX25hbWUiLCAiTWF4Il0~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIi
            wgImZhbWlseV9uYW1lIiwgIk1cdTAwZmNsbGVyIl0~WyJ5MXNWVTV3ZGZKYWhWZGd3UG
            dTN1JRIiwgImFkZHJlc3MiLCB7ImxvY2FsaXR5IjogIk1heHN0YWR0IiwgInBvc3RhbF
            9jb2RlIjogIjEyMzQ0IiwgImNvdW50cnkiOiAiREUiLCAic3RyZWV0X2FkZHJlc3MiOi
            AiV2VpZGVuc3RyYVx1MDBkZmUgMjIifV0~
        """.trimIndent().replace("\n", "")

        val sdJwt = SdJwtVerifier.verifyPresentation(
            JwtSignatureVerifier.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            unverifiedSdJwt,
        ).getOrThrow()

        val claims = sdJwt.recreateClaims { it.second }
        json.encodeToString(claims).also { println(it) }
    }

    @Test
    fun `Example 3 Complex Structured`() = test("Example 3 Complex Structured", 12) {
        sdJwt {
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)

            flat {
                put("birth_middle_name", "Timotheus")
                put("salutation", "Dr.")
                put("msisdn", "49123456789")
            }
            structured("verified_claims") {
                structured("verification") {
                    plain {
                        put("trust_framework", "de_aml")
                    }
                    flat {
                        put("time", "2012-04-23T18:25Z")
                        put("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                    }
                    sdArray("evidence") {
                        sd(
                            buildJsonObject {
                                put("type", "document")
                                put("method", "pipp")
                                put("time", "2012-04-22T11:30Z")
                                putJsonObject("document") {
                                    put("type", "idcard")
                                    putJsonObject("issuer") {
                                        put("name", "Stadt Augsburg")
                                        put("country", "DE")
                                    }
                                    put("number", "53554554")
                                    put("date_of_issuance", "2010-03-23")
                                    put("date_of_expiry", "2020-03-22")
                                }
                            },
                        )
                    }
                }
                structured("claim") {
                    flat {
                        put("given_name", "Max")
                        put("family_name", "Müller")
                        putJsonArray("nationalities") {
                            add("DE")
                        }
                        put("birthdate", "1956-01-28")
                        putJsonObject("place_of_birth") {
                            put("country", "IS")
                            put("locality", "Þykkvabæjarklaustur")
                        }
                        putJsonObject("address") {
                            put("locality", "Maxstadt")
                            put("postal_code", "12344")
                            put("country", "DE")
                            put("street_address", "Weidenstraße 22")
                        }
                    }
                }
            }
        }
    }

    private fun test(
        descr: String,
        expectedDisclosuresNo: Int,
        sdJwtElements: () -> List<SdJwtElement>,
    ) {
        println(descr)
        val disclosedClaimsResult = disclosuresCreator.discloseSdJwt(sdJwtElements())
        val disclosedClaims = assertDoesNotThrow { disclosedClaimsResult.getOrThrow() }
        disclosedClaims.run { print() }
        assertEquals(expectedDisclosuresNo, disclosedClaims.disclosures.size)
        println("=====================================")
    }
}
