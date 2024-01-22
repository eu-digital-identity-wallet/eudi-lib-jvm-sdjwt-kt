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

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DisclosureTest {

    @Test
    fun encodeSimpleClaim() {
        val saltProvider = fixedSaltProvider("_26bc4LT-ac6q2KI6cBW5es")
        val claim: Claim = "family_name" to JsonPrimitive("Möbius")

        val disclosure = Disclosure.objectProperty(saltProvider, claim).getOrThrow()
        val expected = "WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsImZhbWlseV9uYW1lIiwiTcO2Yml1cyJd"

        assertEquals(expected, disclosure.value)
    }

    @Test
    fun decodeSimpleClaim() {
        val expectedSalt: Salt = "_26bc4LT-ac6q2KI6cBW5es"
        val expectedClaim: Claim = "family_name" to JsonPrimitive("Möbius")

        val (salt, name, value) = Disclosure.decode("WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsImZhbWlseV9uYW1lIiwiTcO2Yml1cyJd").getOrThrow()

        assertEquals(expectedSalt, salt)
        assertNotNull(name)
        assertEquals(expectedClaim, name to value)
    }

    private fun fixedSaltProvider(s: String): SaltProvider =
        SaltProvider { s }
}
