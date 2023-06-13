package eu.europa.ec.eudi.sdjwt

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DisclosureTest {


    @Test
    fun encodeSimpleClaim() {
        val saltProvider  = fixedSaltProvider("_26bc4LT-ac6q2KI6cBW5es")
        val claim : Claim = "family_name" to JsonPrimitive("Möbius")

        val disclosure = Disclosure.encode(saltProvider, claim).getOrThrow()
        val expected = "WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsImZhbWlseV9uYW1lIiwiTcO2Yml1cyJd"

        assertEquals(expected, disclosure.value)
    }

    @Test
    fun decodeSimpleClaim() {

        val expectedSalt : Salt = "_26bc4LT-ac6q2KI6cBW5es"
        val expectedClaim: Claim = "family_name" to JsonPrimitive("Möbius")

        val (salt, claim) = Disclosure.decode("WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsImZhbWlseV9uYW1lIiwiTcO2Yml1cyJd").getOrThrow()

        assertEquals(expectedSalt, salt)
        assertEquals(expectedClaim, claim)

    }



    private fun fixedSaltProvider(s: String) : SaltProvider =
        SaltProvider { s }
}