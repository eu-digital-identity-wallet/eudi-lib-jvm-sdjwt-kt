import kotlinx.serialization.json.*
import niscy.eudiw.sdjwt.DisclosureOps
import niscy.eudiw.sdjwt.HashAlgorithm
import niscy.eudiw.sdjwt.SaltProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DisclosureOpsTest {


    @Test
    fun flatDisclosure() {
        val otherClaims = buildJsonObject {
            put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        }

        val address = buildJsonObject {
            put("street_address", "Schulstr. 12")
            put("locality", "Schulpforta")
            put("region", "Sachsen-Anhalt")
            put("country", "DE")
        }
        val addressClaim = "address" to address

        val hashAlgorithm = HashAlgorithm.SHA_256
        val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
            hashAlgorithm,
            SaltProvider.Default,
            otherClaims,
            addressClaim
        ).getOrThrow()

        println(jwtClaimSet)



        assertEquals(address.size, disclosures.size)

        // otherClaims size +  "_sd_alg" + "_sd"
        val expectedJwtClaimSetSize = otherClaims.size + 1 + 1


        assertEquals(expectedJwtClaimSetSize, jwtClaimSet.size)

        // Make sure that jwtClaimSet contains plain subject claim
        assertEquals(JsonPrimitive("6c5c0a49-b589-431d-bae7-219122a9ec2c"), jwtClaimSet["sub"])

        assertEquals(JsonPrimitive(hashAlgorithm.alias), jwtClaimSet["_sd_alg"])

        val sdClaims = jwtClaimSet["_sd"]?.jsonArray
        assertEquals(address.size, sdClaims?.size ?: 0)

    }
}