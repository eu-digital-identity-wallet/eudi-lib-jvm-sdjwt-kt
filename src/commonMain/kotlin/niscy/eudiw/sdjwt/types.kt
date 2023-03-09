package niscy.eudiw.sdjwt

import kotlinx.serialization.json.JsonElement

/**
 * A claim is an attribute of an entity.
 * It consists of a name (of the attribute) and its value.
 *
 * There is set of well-defined, yet optional, claims defined by JWT, which are
 * collectively named as <em>registered claims</em>
 */
typealias Claim = Pair<String, JsonElement>
fun Claim.name(): String = first
fun Claim.value(): JsonElement = second


/**
 * Salt to be included in a [Disclosure] claim.
 * It is defined in <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-02.html#name-selectively-disclosable-cla">Selectively Disclosable Claims</a>
 */
typealias Salt = String


enum class HashAlgorithm(val alias: String) {
    SHA_256 ("sha-256"),
    SHA_384 ("sha-384"),
    SHA_512 ("sha-512"),
    SHA3_256("sha3-256"),
    SHA3_384("sha3-384"),
    SHA3_512("sha3-512")
}

/**
 * Combined form for issuance
 * It is a string containing a standard JWT token followed by a set of [Disclosure]s divided by character `~`
 */
typealias CombinedIssuanceSdJwt = String
typealias Jwt              = String

fun CombinedIssuanceSdJwt.split(): Result<Pair<Jwt, List<Disclosure>>> = runCatching{
    val list = split("~")
    require(list.size>1)
    list[0] to list.takeLast(list.size-1).map { Disclosure.wrap(it).getOrThrow() }

}