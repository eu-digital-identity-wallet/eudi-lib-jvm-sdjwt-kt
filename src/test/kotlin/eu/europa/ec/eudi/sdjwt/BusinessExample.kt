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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class Address(
    val country: String,
    @SerialName("street_address") val streetAddress: String,
    val locality: String? = null,
    val region: String,
)

@Serializable
data class FooClaim(
    @SerialName("sub") val subject: String,
    @SerialName("iat") val issuedAt: Int,
    @SerialName("home_address") val homeAddress: Address,
    @SerialName("work_address") val workAddress: Address,
)

fun FooClaim.usingDsl(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
    saltProvider: SaltProvider = SaltProvider.Default,
): DisclosedClaimSet {
    val jsonObject = json.encodeToJsonElement(this).jsonObject

    // Here we define that home & work address are to be selectively disclosable
    val plainClaims = JsonObject(jsonObject.filterNot { it.key == "home_address" || it.key == "work_address" })

    fun Address.disclose(claimName: String, plainClaims: (Claim) -> Boolean = { false }) =
        SdJwtDsl.structured(claimName, json.encodeToJsonElement<Address>(this).jsonObject, plainClaims)

    val dsl = SdJwtDsl.SdJwt(
        plainClaims = SdJwtDsl.plain(plainClaims),
        structuredClaims = setOf(
            homeAddress.disclose("home_address"),
            workAddress.disclose("work_address") { it.name() == "country" },
        ),
    )

    return DisclosedClaimSet.disclose(hashAlgorithm, saltProvider, 2, dsl).getOrThrow()
}

fun FooClaim.usignBuilder(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    numOfDecoys: Int = 0,
): DisclosedClaimSet {
    val jsonObject = json.encodeToJsonElement(this).jsonObject

    // Here we define that home & work address are to be selectively disclosable
    val plainClaims = JsonObject(jsonObject.filterNot { it.key == "home_address" || it.key == "work_address" })
    val sdJwtDsl = sdJwt {
        plain(plainClaims)
        structured("work_address") {
            jsonObject["work_address"]!!
                .jsonObject
                .forEach { x -> flat(x.toPair()) }
        }

        structured("home_address") {
            flat {
                put("country", "GR")
                put("steet", "Markopoulo")
            }
        }
    }
    return DisclosedClaimSet.disclose(hashAlgorithm, saltProvider, numOfDecoys, sdJwtDsl).getOrThrow()
}

private val json = Json { prettyPrint = true }

fun main() {
    val homeAddress =
        Address(country = "GR", streetAddress = "12 Foo Str", locality = null, region = "Attica")
    val workAddress =
        Address(country = "DE", streetAddress = "Schulstr. 12", locality = "Schulpforta", region = "Sachsen-Anhalt")
    val fooClaim = FooClaim(
        subject = "subject",
        issuedAt = 10,
        homeAddress = homeAddress,
        workAddress = workAddress,
    )

//    println("Advanced  example. Using DSL")
//    println("All attributes of both work and home address are selectively disclosable")
//    println("For work address country is plain")
//    fooClaim.usingDsl().also { it.print() }
//
//    println("Advanced  example. Using builder")
//
//    fooClaim.usignBuilder().also { it.print() }

    foo().disclose()
}

private fun SdJwtDsl.SdJwt.disclose() {
    val result = DisclosedClaimSet.disclose(
        HashAlgorithm.SHA_256,
        SaltProvider.Default, 0, this
    )
    val succes = result.getOrThrow()
    succes.print()

}

fun DisclosedClaimSet.print() {
    disclosures.forEach { println(it.claim()) }
    claimSet.also { println(json.encodeToString(it)) }
}

fun flatDisclosureExample() =
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

fun structuredDisclosureExample() =
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

fun complexExample() =
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

val foo = """
{
  "verified_claims": {
    "verification": {
      "trust_framework": "de_aml",
      "time": "2012-04-23T18:25Z",
      "verification_process": "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7",
      "evidence": [
        {
          "type": "document",
          "method": "pipp",
          "time": "2012-04-22T11:30Z",
          "document": {
            "type": "idcard",
            "issuer": {
              "name": "Stadt Augsburg",
              "country": "DE"
            },
            "number": "53554554",
            "date_of_issuance": "2010-03-23",
            "date_of_expiry": "2020-03-22"
          }
        }
      ]
    },
    "claims": {
      "given_name": "Max",
      "family_name": "Müller",
      "nationalities": [
        "DE"
      ],
      "birthdate": "1956-01-28",
      "place_of_birth": {
        "country": "IS",
        "locality": "Þykkvabæjarklaustur"
      },
      "address": {
        "locality": "Maxstadt",
        "postal_code": "12344",
        "country": "DE",
        "street_address": "Weidenstraße 22"
      }
    }
  },
  "birth_middle_name": "Timotheus",
  "salutation": "Dr.",
  "msisdn": "49123456789"
}
""".trimIndent()

fun foo() =
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



