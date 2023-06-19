package eu.europa.ec.eudi.sdjwt

import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisclosureOpsTest {

    @Nested
    @DisplayName("Flat disclosure test")
    class FlatDisclosure {

        @Test
        fun `no sd-jwt with illegal attribute names`() {
            val invalidClaims = listOf(
                "_sd" to JsonPrimitive("foo"), // invalid because claim name
                "foo" to buildJsonObject {
                    put("_sd", "bar")
                },

                "foo" to buildJsonArray {
                    add(buildJsonObject {
                        put("_sd", "bar")
                    })
                }
            )

            val hashAlgorithm = HashAlgorithm.SHA_256

            invalidClaims.forEach { claimToBeDisclosed ->
                val result = DisclosureOps.flatDisclose(
                    hashAlgorithm = hashAlgorithm,
                    saltProvider = SaltProvider.Default,
                    claimsToBeDisclosed = JsonObject(mapOf(claimToBeDisclosed)),
                    numOfDecoys = 0
                )
                assertFalse { result.isSuccess }
            }
        }

        @Test
        fun flatDisclosureOfJsonObjectClaim() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed =
                buildJsonObject {
                    putJsonObject("address") {
                        put("street_address", "Schulstr. 12")
                        put("locality", "Schulpforta")
                        put("region", "Sachsen-Anhalt")
                        put("country", "DE")
                    }
                }
            testFlatDisclosure(otherClaims, claimToBeDisclosed)
        }

        @Test
        fun flatDisclosureOfJsonPrimitive() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed =
                buildJsonObject {
                    put("street_address", "Schulstr. 12")
                }
            testFlatDisclosure(otherClaims, claimToBeDisclosed)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @Test
        fun flatDisclosureOfJsonArrayOfPrimitives() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed =
                buildJsonObject {
                    putJsonArray("countries") {
                        addAll(listOf("GR", "DE"))
                    }
                }
            testFlatDisclosure(otherClaims, claimToBeDisclosed)
        }

        @Test
        fun flatDisclosure() {
            val jwtVcPayload = """{
          "iss": "https://example.com",
          "jti": "http://example.com/credentials/3732",
          "nbf": 1541493724,
          "iat": 1541493724,
          "cnf": {
            "jwk": {
              "kty": "RSA",
              "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
              "e": "AQAB"
            }
          },
          "type": "IdentityCredential",
          "credentialSubject": {
            "given_name": "John",
            "family_name": "Doe",
            "email": "johndoe@example.com",
            "phone_number": "+1-202-555-0101",
            "address": {
              "street_address": "123 Main St",
              "locality": "Anytown",
              "region": "Anystate",
              "country": "US"
            },
            "birthdate": "1940-01-01",
            "is_over_18": true,
            "is_over_21": true,
            "is_over_65": true
       }
      }""".trimIndent()

            val jwtVcPayloadJson = Json.parseToJsonElement(jwtVcPayload).jsonObject

            val (otherClaims, claimToBeDisclosed) =
                jwtVcPayloadJson.extractClaim("credentialSubject")

            testFlatDisclosure(otherClaims, JsonObject(mapOf(claimToBeDisclosed!!)))
        }


        private fun testFlatDisclosure(
            otherClaims: JsonObject,
            claimsToBeDisclosed: JsonObject
        ): DisclosedJsonObject {

            val hashAlgorithm = HashAlgorithm.SHA_256
            val disclosedJsonObject = DisclosureOps.flatDisclose(
                hashAlgorithm,
                SaltProvider.Default,
                otherClaims,
                claimsToBeDisclosed,
                4
            ).getOrThrow()

            val (disclosures, jwtClaimSet) = disclosedJsonObject

            /**
             * Verifies the expected size of the jwt claim set
             */
            fun assertJwtClaimSetSize() {
                // otherClaims size +  "_sd_alg" + "_sd"
                val expectedJwtClaimSetSize = otherClaims.size + 1 + 1
                assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size, "Incorrect jwt payload attribute number")
            }


            fun assertDisclosures() {

                val expectedSize = claimsToBeDisclosed.size

                assertEquals(
                    expectedSize,
                    disclosures.size,
                    "Make sure the size of disclosures is equal to the number of address attributes"
                )




                disclosures.forEach { d ->
                    val (claimName, claimValue) = d.claim()
                    println("Found disclosure for $claimName -> $claimValue")
                    assertEquals(claimsToBeDisclosed[claimName], claimValue)
                }


            }

            assertJwtClaimSetSize()
            assertContainsPlainClaims(jwtClaimSet, otherClaims)
            assertHashFunctionClaimIsPresentIfDisclosures(jwtClaimSet, hashAlgorithm, disclosures)
            assertDisclosures()
            assertHashesNumberGreaterOrEqualToDisclosures(jwtClaimSet, disclosures)
            return disclosedJsonObject

        }
    }

    @DisplayName("Structured Disclosure")
    @Nested
    class StructuredDisclosure {

        @Test
        fun basic() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed = buildJsonObject {
                putJsonObject("address") {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }

            testStructured(otherClaims, claimToBeDisclosed)
        }

        private fun testStructured(
            otherClaims: JsonObject,
            claimToBeDisclosed: JsonObject
        ) {

            val hashAlgorithm = HashAlgorithm.SHA_256
            val disclosedJsonObject = DisclosureOps.structureDisclose(
                hashAlgorithm,
                SaltProvider.Default,
                otherClaims,
                claimToBeDisclosed,
                3
            ).getOrThrow()

            val (disclosures, jwtClaimSet) = disclosedJsonObject

            /**
             * Verifies the expected size of the jwt claim set
             */
            fun assertJwtClaimSetSize() {
                // otherClaims size +  "_sd_alg" + "_sd"
                val expectedJwtClaimSetSize = otherClaims.size + 1 + claimToBeDisclosed.size
                assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size, "Incorrect jwt payload attribute number")
            }

            assertJwtClaimSetSize()
            assertContainsPlainClaims(jwtClaimSet, otherClaims)
            assertHashFunctionClaimIsPresentIfDisclosures(jwtClaimSet, hashAlgorithm, disclosures)
            assertHashesNumberGreaterOrEqualToDisclosures(jwtClaimSet, disclosures)

        }

    }

    companion object {

        fun assertContainsPlainClaims(sdEncoded: JsonObject, plainClaims: JsonObject) {
            for ((k, v) in plainClaims) {
                assertEquals(v, sdEncoded[k], "Make sure that non selectively disclosable elements are present")
            }
        }

        fun assertHashFunctionClaimIsPresentIfDisclosures(
            jwtClaimSet: JsonObject,
            hashAlgorithm: HashAlgorithm,
            disclosures: Collection<Disclosure>
        ) {
            val sdAlgValue = jwtClaimSet["_sd_alg"]
            val expectedSdAlgValue = if (disclosures.isNotEmpty()) JsonPrimitive(hashAlgorithm.alias)
            else null
            assertEquals(expectedSdAlgValue, sdAlgValue)
        }

        fun assertHashesNumberGreaterOrEqualToDisclosures(
            sdEncoded: JsonObject,
            disclosures: Collection<Disclosure>
        ) {

            val hashes = sdEncoded.collectHashedDisclosures()
            // Hashes can be more than disclosures due to decoy
            if (disclosures.isNotEmpty()) assertTrue { hashes.size >= disclosures.size }
            else assertTrue(hashes.isEmpty())
        }

        private fun JsonObject.collectHashedDisclosures(): List<HashedDisclosure> =
            map { (attr, value) ->
                when {
                    attr == "_sd" && value is JsonArray -> value.jsonArray.map { v ->
                        HashedDisclosure.wrap(v.jsonPrimitive.content).getOrThrow()
                    }

                    value is JsonObject -> value.collectHashedDisclosures()
                    else -> emptyList()
                }
            }.flatten()


    }

}
