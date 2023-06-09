import kotlinx.serialization.json.JsonObject
import niscy.eudiw.sdjwt.Claim


fun JsonObject.extractClaim(attributeName: String): Pair<JsonObject, Claim?> {

    val otherClaims = JsonObject(filterKeys { it != attributeName })
    val claimToBeDisclosed: Claim? = firstNotNullOfOrNull {
        if (it.key == attributeName) it.value
        else null
    }?.let { attributeName to it }
    return otherClaims to claimToBeDisclosed
}
