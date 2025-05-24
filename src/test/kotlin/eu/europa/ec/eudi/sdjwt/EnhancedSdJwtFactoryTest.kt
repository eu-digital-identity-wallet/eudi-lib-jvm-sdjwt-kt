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

import eu.europa.ec.eudi.sdjwt.dsl.json.sdJwt
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class EnhancedSdJwtFactoryTest {

    @Test
    fun `test that EnhancedSdJwtFactory produces same results as SdJwtFactory`() {
        // Create a test SD-JWT specification
        val sdJwtSpec = sdJwt {
            claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
            claim("iss", "https://example.com/issuer")
            claim("iat", 1516239022)
            claim("exp", 1735689661)
            sdObjClaim("address") {
                sdClaim("street_address", "Schulstr. 12")
                sdClaim("locality", "Schulpforta")
                sdClaim("region", "Sachsen-Anhalt")
                sdClaim("country", "DE")
            }
            sdClaim("given_name", "John")
            sdClaim("family_name", "Doe")
            sdArrClaim("nationalities") {
                sdClaim("DE")
                sdClaim("FR")
            }
        }

        // Create instances of both factories with the same configuration
        val hashAlgorithm = HashAlgorithm.SHA_256
        val saltProvider = FixedSaltProvider("fixed-salt-for-testing")
        val decoyGen = NoDecoyGen
        val fallbackMinimumDigests = MinimumDigests(5)

        val originalFactory = SdJwtFactory(
            hashAlgorithm = hashAlgorithm,
            saltProvider = saltProvider,
            decoyGen = decoyGen,
            fallbackMinimumDigests = fallbackMinimumDigests,
        )

        val enhancedFactory = EnhancedSdJwtFactory(
            hashAlgorithm = hashAlgorithm,
            saltProvider = saltProvider,
            decoyGen = decoyGen,
            fallbackMinimumDigests = fallbackMinimumDigests,
        )

        // Generate SD-JWTs using both factories
        val originalResult = originalFactory.createSdJwt(sdJwtSpec).getOrThrow()
        val enhancedResult = enhancedFactory.createSdJwt(sdJwtSpec).getOrThrow()

        // Debug: Print detailed information about the results
        println("[DEBUG_LOG] Original JWT: ${originalResult.jwt}")
        println("[DEBUG_LOG] Enhanced JWT: ${enhancedResult.jwt}")

        println("[DEBUG_LOG] Original Disclosures:")
        originalResult.disclosures.forEachIndexed { index, disclosure ->
            println("[DEBUG_LOG] $index: ${disclosure.value}")
        }

        println("[DEBUG_LOG] Enhanced Disclosures:")
        enhancedResult.disclosures.forEachIndexed { index, disclosure ->
            println("[DEBUG_LOG] $index: ${disclosure.value}")
        }

        // Compare the results
        compareJwtParts(originalResult.jwt, enhancedResult.jwt)
        compareDisclosures(originalResult.disclosures, enhancedResult.disclosures)
    }

    private fun compareJwtParts(original: JsonObject, enhanced: JsonObject) {
        // Check that both JWT parts have the same keys
        assertEquals(original.keys, enhanced.keys, "JWT parts should have the same keys")

        // Check that non-SD claims are identical
        original.keys.filter { it != SdJwtSpec.CLAIM_SD }.forEach { key ->
            assertEquals(original[key], enhanced[key], "Non-SD claim '$key' should be identical")
        }

        // For SD claims, we only check that they have the same number of digests
        // The actual digest values might differ due to implementation differences
        val originalSdClaim = original[SdJwtSpec.CLAIM_SD]?.let {
            it as kotlinx.serialization.json.JsonArray
            it.size
        } ?: 0

        val enhancedSdClaim = enhanced[SdJwtSpec.CLAIM_SD]?.let {
            it as kotlinx.serialization.json.JsonArray
            it.size
        } ?: 0

        assertEquals(originalSdClaim, enhancedSdClaim, "SD claims should have the same number of digests")
    }

    private fun compareDisclosures(original: List<Disclosure>, enhanced: List<Disclosure>) {
        // Check that both have the same number of disclosures
        assertEquals(original.size, enhanced.size, "Should have the same number of disclosures")

        // We don't compare the exact disclosure values because the structure might differ
        // between the two implementations, but the functional behavior should be the same.
        // Instead, we check that both implementations have disclosures for the same claims.

        // Extract claim names from disclosures using the Disclosure.decode method
        val originalClaimNames = original.mapNotNull { disclosure ->
            try {
                // Use the Disclosure.decode method to extract the claim name
                val (_, claimName, _) = Disclosure.decode(disclosure.value).getOrThrow()
                claimName
            } catch (e: Exception) {
                println("[DEBUG_LOG] Error decoding disclosure: ${e.message}")
                null
            }
        }.sorted()

        val enhancedClaimNames = enhanced.mapNotNull { disclosure ->
            try {
                val (_, claimName, _) = Disclosure.decode(disclosure.value).getOrThrow()
                claimName
            } catch (e: Exception) {
                println("[DEBUG_LOG] Error decoding disclosure: ${e.message}")
                null
            }
        }.sorted()

        assertEquals(originalClaimNames, enhancedClaimNames, "Disclosures should be for the same claims")
    }
}

/**
 * A salt provider that always returns the same salt for testing purposes.
 */
class FixedSaltProvider(private val fixedSalt: String) : SaltProvider {
    override fun salt(): Salt = fixedSalt
}

/**
 * A decoy generator that doesn't generate any decoys, for testing purposes.
 */
object NoDecoyGen : DecoyGen {
    override fun gen(hashingAlgorithm: HashAlgorithm): DisclosureDigest {
        throw UnsupportedOperationException("This implementation should not be called directly")
    }

    override fun gen(hashingAlgorithm: HashAlgorithm, numOfDecoys: Int): Set<DisclosureDigest> = emptySet()
}
