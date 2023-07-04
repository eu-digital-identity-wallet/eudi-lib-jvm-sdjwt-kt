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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisclosedClaimSetTest {

    @Nested
    @DisplayName("Flat disclosure test")
    inner class FlatDisclosure {

        @Test
        fun `no sd-jwt with illegal attribute names`() {
            val invalidClaims = listOf(
                sdJwt {
                    flat { put("_sd", "foo") }
                },
            )

            val disclosuresCreator = DisclosuresCreator(numOfDecoysLimit = 0)
            invalidClaims.forEach { sdJwt ->
                val result = disclosuresCreator.discloseSdJwt(sdJwt)
                assertFalse { result.isSuccess }
            }
        }

        @Test
        fun flatDisclosureOfJsonObjectClaim() {
            val plainClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimsToBeDisclosed =
                buildJsonObject {
                    putJsonObject("address") {
                        put("street_address", "Schulstr. 12")
                        put("locality", "Schulpforta")
                        put("region", "Sachsen-Anhalt")
                        put("country", "DE")
                    }
                }
            testFlatDisclosure(plainClaims, claimsToBeDisclosed)
        }

        @Test
        fun flatDisclosureOfJsonPrimitive() {
            val plainClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimsToBeDisclosed =
                buildJsonObject {
                    put("street_address", "Schulstr. 12")
                }
            testFlatDisclosure(plainClaims, claimsToBeDisclosed)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @Test
        fun flatDisclosureOfJsonArrayOfPrimitives() {
            val plainClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimsToBeDisclosed =
                buildJsonObject {
                    putJsonArray("countries") {
                        addAll(listOf("GR", "DE"))
                    }
                }
            testFlatDisclosure(plainClaims, claimsToBeDisclosed)
        }

        @Test
        fun flatDisclosure() {
            val jwtVcPayloadJson = Json.parseToJsonElement(jwtVcPayload).jsonObject
            val otherClaims = jwtVcPayloadJson.filterNot { it.key == "credentialSubject" }
            val claimsToBeDisclosed = jwtVcPayloadJson["credentialSubject"]!!.jsonObject
            testFlatDisclosure(otherClaims, claimsToBeDisclosed)
        }

        private fun testFlatDisclosure(
            plainClaims: Map<String, JsonElement>,
            claimsToBeDisclosed: Map<String, JsonElement>,
        ): DisclosedClaims {
            val hashAlgorithm = HashAlgorithm.SHA_256
            val sdJwtElements = sdJwt {
                plain(plainClaims)
                flat(claimsToBeDisclosed)
            }

            val disclosedJsonObject = DisclosuresCreator(
                hashAlgorithm,
                SaltProvider.Default,
                4,
            ).discloseSdJwt(sdJwtElements).getOrThrow()

            val (disclosures, jwtClaimSet) = disclosedJsonObject

            /**
             * Verifies the expected size of the jwt claim set
             */
            fun assertJwtClaimSetSize() {
                // otherClaims size +  "_sd_alg" + "_sd"
                val expectedJwtClaimSetSize = plainClaims.size + 1 + 1
                assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size, "Incorrect jwt payload attribute number")
            }

            fun assertDisclosures() {
                val expectedSize = claimsToBeDisclosed.size

                assertEquals(
                    expectedSize,
                    disclosures.size,
                    "Make sure the size of disclosures is equal to the number of address attributes",
                )

                disclosures.forEach { d ->
                    val (claimName, claimValue) = d.claim()
                    println("Found disclosure for $claimName -> $claimValue")
                    assertEquals(claimsToBeDisclosed[claimName], claimValue)
                }
            }

            assertJwtClaimSetSize()
            assertContainsPlainClaims(jwtClaimSet, plainClaims)
            assertHashFunctionClaimIsPresentIfDisclosures(jwtClaimSet, hashAlgorithm, disclosures)
            assertDisclosures()
            assertDigestNumberGreaterOrEqualToDisclosures(jwtClaimSet, disclosures)
            return disclosedJsonObject
        }
    }

    @DisplayName("Structured Disclosure")
    @Nested
    inner class StructuredDisclosure {

        @Test
        fun basic() {
            val plainClaims = buildJsonObject {
                put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                put("iss", "sample issuer")
            }
            val claimsToBeDisclosed = buildJsonObject {
                putJsonObject("address") {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }

            testStructured(plainClaims, claimsToBeDisclosed)
        }

        @Test
        fun advanced() {
            // this is the json we want to include in the JWT (not disclosed)
            val jwtVcJson: JsonObject = format.parseToJsonElement(jwtVcPayload).jsonObject
            val (plainClaims, claimsToBeDisclosed) = jwtVcJson.extractClaim("credentialSubject")

            testStructured(plainClaims, claimsToBeDisclosed)
        }

        private fun testStructured(
            plainClaims: Map<String, JsonElement>,
            claimsToBeDisclosed: Map<String, JsonElement>,
        ) {
            val hashAlgorithm = HashAlgorithm.SHA_256
            val sdJwtElements = sdJwt {
                plain(plainClaims)
                claimsToBeDisclosed.forEach { c -> structured(c.key) { flat(c.value.jsonObject) } }
            }
            val disclosedJsonObject = DisclosuresCreator(
                hashAlgorithm,
                SaltProvider.Default,
                3,

            ).discloseSdJwt(sdJwtElements).getOrThrow()

            val (disclosures, jwtClaimSet) = disclosedJsonObject

            /**
             * Verifies the expected size of the jwt claim set
             */
            fun assertJwtClaimSetSize() {
                // otherClaims size +  "_sd_alg" + "_sd"
                val expectedJwtClaimSetSize = plainClaims.size + 1 + claimsToBeDisclosed.size
                assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size, "Incorrect jwt payload attribute number")
            }

            assertJwtClaimSetSize()
            assertContainsPlainClaims(jwtClaimSet, plainClaims)
            assertHashFunctionClaimIsPresentIfDisclosures(jwtClaimSet, hashAlgorithm, disclosures)
            assertDigestNumberGreaterOrEqualToDisclosures(jwtClaimSet, disclosures)
        }
    }

    companion object {

        fun assertContainsPlainClaims(sdEncoded: Map<String, JsonElement>, plainClaims: Map<String, JsonElement>) {
            for ((k, v) in plainClaims) {
                assertEquals(v, sdEncoded[k], "Make sure that non selectively disclosable elements are present")
            }
        }

        fun assertHashFunctionClaimIsPresentIfDisclosures(
            jwtClaimSet: JsonObject,
            hashAlgorithm: HashAlgorithm,
            disclosures: Collection<Disclosure>,
        ) {
            val sdAlgValue = jwtClaimSet["_sd_alg"]
            val expectedSdAlgValue = if (disclosures.isNotEmpty()) {
                JsonPrimitive(hashAlgorithm.alias)
            } else {
                null
            }
            assertEquals(expectedSdAlgValue, sdAlgValue)
        }

        fun assertDigestNumberGreaterOrEqualToDisclosures(
            sdEncoded: JsonObject,
            disclosures: Collection<Disclosure>,
        ) {
            val hashes = disclosureDigests(sdEncoded)
            // Hashes can be more than disclosures due to decoy
            if (disclosures.isNotEmpty()) {
                assertTrue { hashes.size >= disclosures.size }
            } else {
                assertTrue(hashes.isEmpty())
            }
        }

    }
}
