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

import eu.europa.ec.eudi.sdjwt.DisclosedClaimSetTest.assertContainsPlainClaims
import eu.europa.ec.eudi.sdjwt.DisclosedClaimSetTest.assertDigestNumberGreaterOrEqualToDisclosures
import eu.europa.ec.eudi.sdjwt.DisclosedClaimSetTest.assertHashFunctionClaimIsPresentIfDisclosures
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredDisclosure {

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
            plainClaims.forEach { claim(it.key, it.value) }
            claimsToBeDisclosed.forEach { objClaim(it.key) { sdClaim(it.key, it.value) } }
        }
        val disclosedJsonObject = SdJwtFactory(
            hashAlgorithm,
            SaltProvider.Default,
            DecoyGen.Default,
            null,

        ).createSdJwt(sdJwtElements).getOrThrow()

        val (jwtClaimSet, disclosures) = disclosedJsonObject

        /**
         * Verifies the expected size of the jwt claim set
         */
        fun assertJwtClaimSetSize() {
            // otherClaims size +  "_sd_alg" + "_sd"
            val expectedJwtClaimSetSize =
                plainClaims.size + 1 + claimsToBeDisclosed.size
            assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size, "Incorrect jwt payload attribute number")
        }

        assertJwtClaimSetSize()
        assertContainsPlainClaims(jwtClaimSet, plainClaims)
        assertHashFunctionClaimIsPresentIfDisclosures(jwtClaimSet, hashAlgorithm, disclosures)
        assertDigestNumberGreaterOrEqualToDisclosures(jwtClaimSet, disclosures)
    }
}

class FlatDisclosure {

    @Test
    fun `no sd-jwt with illegal attribute names`() {
        val invalidClaims = listOf(
            sdJwt {
                sdClaim("_sd", "foo")
            },
        )

        val sdJwtFactory = SdJwtFactory.Default
        invalidClaims.forEach { sdJwt ->
            val result = sdJwtFactory.createSdJwt(sdJwt)
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
    ): SdJwt<JsonObject> {
        val hashAlgorithm = HashAlgorithm.SHA_256
        val sdJwtElements = sdJwt {
            plainClaims.forEach { claim(it.key, it.value) }
            claimsToBeDisclosed.forEach { sdClaim(it.key, it.value) }
        }

        val disclosedJsonObject = SdJwtFactory(
            hashAlgorithm,
            SaltProvider.Default,
            DecoyGen.Default,
            4.atLeastDigests(),
        ).createSdJwt(sdJwtElements).getOrThrow()

        val (jwtClaimSet, disclosures) = disclosedJsonObject

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
                when (d) {
                    is Disclosure.ObjectProperty -> {
                        val (claimName, claimValue) = d.claim()
                        println("Found disclosure for $claimName -> $claimValue")
                        assertEquals(claimsToBeDisclosed[claimName], claimValue)
                    }

                    else -> TODO()
                }
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

private object DisclosedClaimSetTest {

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
        val hashes = collectDigests(sdEncoded)
        // Hashes can be more than disclosures due to decoy
        if (disclosures.isNotEmpty()) {
            assertTrue { hashes.size >= disclosures.size }
        } else {
            assertTrue(hashes.isEmpty())
        }
    }
}
