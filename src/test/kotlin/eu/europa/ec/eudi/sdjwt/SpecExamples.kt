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

fun main() {
    listOf(
        option1FlatSdJwt(),
        option2StructuredSdJwt(),
        example2A(),
        reallyComplex(),
    ).forEach { it.disclose() }
}

private fun Set<SdJwtElement<JsonElement>>.disclose() {
    DefaultDisclosuresCreatorFactory.create(
        HashAlgorithm.SHA_256,
        SaltProvider.Default,
        0,
    ).discloseSdJwt(this).getOrThrow().also { it.print() }
}

val json = Json { prettyPrint = true }
fun DisclosedClaims<JsonObject>.print() {
    disclosures.forEach { println(it.claim()) }
    claimSet.also { println(json.encodeToString(it)) }
}

fun option1FlatSdJwt() =
    sdJwt {
        plain {
            put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
            put("iss", "https://example.com/issuer")
            put("iat", 1516239022)
            put("exp", 1735689661)
        }
        flat {
            putJsonObject("address") {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
            }
        }
    }

fun option2StructuredSdJwt() =
    sdJwt {
        plain {
            put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
            put("iss", "https://example.com/issuer")
            put("iat", 1516239022)
            put("exp", 1735689661)
        }
        structured("address") {
            flat {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
            }
        }
    }

fun example2A() =
    sdJwt {
        plain {
            put("iss", "https://example.com/issuer")
            put("iat", 1516239022)
            put("exp", 1735689661)
        }
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

fun reallyComplex() =
    sdJwt {
        plain {
            put("iss", "https://example.com/issuer")
            put("iat", 1516239022)
            put("exp", 1735689661)
        }
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
