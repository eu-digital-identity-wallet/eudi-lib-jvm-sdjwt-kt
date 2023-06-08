package niscy.eudiw.sdjwt

import kotlinx.serialization.json.JsonElement

/**
 * A claim is an attribute of an entity.
 * The claim name, or key, as it would be used in a regular JWT body.
 * The claim value, as it would be used in a regular JWT body.
 * The value MAY be of any type that is allowed in
 * JSON, including numbers, strings, booleans, arrays, and objects
 *
 */
typealias Claim = Pair<String, JsonElement>

/**
 * The claim name, or key, as it would be used in a regular JWT body
 * @return the name of the claim
 */
fun Claim.name(): String = first

/**
 * The claim value, as it would be used in a regular JWT body.
 *  The value MAY be of any type that is allowed in JSON
 * @return the value of the claim
 */
fun Claim.value(): JsonElement = second


/**
 * Salt to be included in a [Disclosure] claim.
 * Check [SD-JWT][https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt-04#section-5.1.1.1]
 */
typealias Salt = String

/**
 * Hashing algorithms, used to produce the [HashedDisclosure] of a [Disclosure]
 */
enum class HashAlgorithm(val alias: String) {
    SHA_256("sha-256"),
    SHA_384("sha-384"),
    SHA_512("sha-512"),
    SHA3_256("sha3-256"),
    SHA3_384("sha3-384"),
    SHA3_512("sha3-512");

    companion object {

        /**
         * Gets the [HashAlgorithm] by its alias
         * @param s a string with the alias of the algorithm
         * @return either a matching [HashAlgorithm] or null
         */
        fun fromString(s: String): HashAlgorithm? = values().find { it.alias == s }
    }
}

/**
 * Combined form for issuance
 * It is a string containing a standard JWT token followed by a set of [Disclosure]s divided by character `~`
 */
typealias CombinedIssuanceSdJwt = String
typealias Jwt = String

fun CombinedIssuanceSdJwt.split(): Result<Pair<Jwt, List<Disclosure>>> = runCatching {
    val list = split("~")
    require(list.size > 1) { "Neither JWT nor disclosures were found" }
    list[0] to Disclosure.run {
        list.takeLast(list.size - 1).map { wrap(it).getOrThrow() }
    }
}

