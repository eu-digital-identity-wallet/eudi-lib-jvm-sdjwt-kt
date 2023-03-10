package niscy.eudiw.sdjwt


import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*


/**
 * A disclosure
 * @see <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-02.html#name-selectively-disclosable-cla">Selectively Disclosable Claims</a>
 */
@JvmInline
value class Disclosure private constructor(val value: String) {

    /**
     * The disclosed claim
     */
    fun claim(): Claim =
        decode(value).getOrThrow().second

    companion object {

        /**
         * Directly wraps a string representing into a [Disclosure]
         * Validates the given string is a base64-url encoded json array that contains
         * a json string (salt)
         * a json string (claim name)
         * a json element (claim value)
         */
        internal fun wrap(s: String): Result<Disclosure> = decode(s).map { Disclosure(s) }


        /**
         * Encodes a [Claim] into [Disclosure] using the provided [saltProvider]
         */
        internal fun encode(
            saltProvider: SaltProvider = SaltProvider.Default,
            claim: Claim
        ): Result<Disclosure> {

            // Make sure that claim name is not _sd
            fun isValidAttributeName(attribute: String): Boolean = attribute != "_sd"

            // Make sure that claim value doesn't contain an attribute named _sd
            // is not Json null
            fun isValidJsonElement(json: JsonElement): Boolean =
                when (json) {
                    is JsonPrimitive -> json !is JsonNull
                    is JsonArray -> json.all { isValidJsonElement(it) }
                    is JsonObject -> json.entries.all { isValidAttributeName(it.key) && isValidJsonElement(it.value) }
                }
            return runCatching {
                if (!isValidAttributeName(claim.name())) {
                    throw IllegalArgumentException("Given claim should not contain an attribute named _sd")
                }
                if (!isValidJsonElement(claim.value())) {
                    throw IllegalArgumentException("Claim should not contain an attribute named _sd")
                }
                // Create a Json Array [salt, claimName, claimValue]
                val jsonArray = buildJsonArray {
                    add(JsonPrimitive(saltProvider.salt())) // salt
                    add(claim.name()) // claim name
                    add(claim.value()) //claim value
                }
                val jsonArrayStr = jsonArray.toString()
                // Base64-url-encoded
                val encoded = JwtBase64.encodeString(jsonArrayStr)
                Disclosure(encoded)
            }
        }

        /**
         * Decodes the given [string][disclosure]  into a pair of [salt][Salt] and [claim][Claim]
         */
        fun decode(disclosure: String): Result<Pair<Salt, Claim>> = runCatching {
            val base64Decoded = JwtBase64.decodeString(disclosure)
            val array = Json.decodeFromString<JsonArray>(base64Decoded)
            if (array.size != 3) {
                throw IllegalArgumentException("Was expecting an json array of 3 elements")
            }
            val salt = array[0].jsonPrimitive.content
            val claimName = array[1].jsonPrimitive.content
            val claimValue = array[2]
            salt to (claimName to claimValue)
        }

        fun concat(ds: Iterable<Disclosure>): String =
            ds.fold("") { acc, disclosure -> "$acc~${disclosure.value}" }
    }
}

fun Iterable<Disclosure>.concat(): String = Disclosure.concat(this)