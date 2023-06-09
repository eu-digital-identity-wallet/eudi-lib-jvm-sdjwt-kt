import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import niscy.eudiw.sdjwt.*
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
                    claimToBeDisclosed = claimToBeDisclosed,
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
            val claimToBeDisclosed = "address" to buildJsonObject {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
            }
            testFlatDisclosure(otherClaims, claimToBeDisclosed)
        }

        @Test
        fun flatDisclosureOfJsonPrimitive() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed = "street_address" to JsonPrimitive("Schulstr. 12")
            testFlatDisclosure(otherClaims, claimToBeDisclosed)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @Test
        fun flatDisclosureOfJsonArrayOfPrimitives() {
            val otherClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimToBeDisclosed = "countries" to buildJsonArray { addAll(listOf("GR", "DE")) }
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

            testFlatDisclosure(otherClaims, claimToBeDisclosed!!)
        }


        private fun testFlatDisclosure(
            otherClaims: JsonObject,
            claimToBeDisclosed: Claim
        ): DisclosedJsonObject {

            val hashAlgorithm = HashAlgorithm.SHA_256
            val disclosedJsonObject = DisclosureOps.flatDisclose(
                hashAlgorithm,
                SaltProvider.Default,
                otherClaims,
                claimToBeDisclosed,
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


            /**
             * Verifies that all plain claims should be present in jwt claim set
             */
            fun assertContainsPlainClaims() {
                for ((k, v) in otherClaims) {
                    assertEquals(v, jwtClaimSet[k], "Make sure that non selectively disclosable elements are present")
                }
            }

            fun assertHashFunctionClaimIsPresentIfDisclosures() {
                val sdAlgValue = jwtClaimSet["_sd_alg"]
                val expectedSdAlgValue = if (disclosures.isNotEmpty()) JsonPrimitive(hashAlgorithm.alias)
                else null
                assertEquals(expectedSdAlgValue, sdAlgValue)
            }


            fun assertHashedDisclosures() {
                val hashes = jwtClaimSet["_sd"]?.jsonArray
                // Hashes can be more than disclosures due to decoy
                if (disclosures.isNotEmpty()) assertTrue { (hashes?.size ?: 0) >= disclosures.size }
                else assertTrue(hashes.isNullOrEmpty())
            }

            fun assertDisclosures() {

                val claimValue = claimToBeDisclosed.value()
                val expectedSize = when (claimValue) {
                    is JsonObject -> claimValue.size
                    is JsonArray -> 1
                    is JsonPrimitive -> 1
                    JsonNull -> 0
                }

                assertEquals(
                    expectedSize,
                    disclosures.size,
                    "Make sure the size of disclosures is equal to the number of address attributes"
                )

                fun assertSingle() {
                    println("Found disclosure for ${claimToBeDisclosed.name()} -> ${claimToBeDisclosed.value()}")
                    assertEquals(claimToBeDisclosed, disclosures.first().claim())
                }

                when (claimValue) {
                    is JsonObject -> disclosures.forEach {
                        val (k, v) = it.claim()
                        println("Found disclosure for $k -> $v")
                        assertEquals(claimValue[k], v)
                    }

                    is JsonArray -> assertSingle()
                    is JsonPrimitive -> assertSingle()

                }
            }

            assertJwtClaimSetSize()
            assertContainsPlainClaims()
            assertHashFunctionClaimIsPresentIfDisclosures()
            assertDisclosures()
            assertHashedDisclosures()
            return disclosedJsonObject

        }
    }
}