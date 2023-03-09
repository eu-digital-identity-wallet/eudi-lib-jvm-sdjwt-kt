package niscy.eudiw.sdjwt

import kotlin.jvm.JvmInline

/**
 * The hash of a disclosure
 * @see <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-02.html#name-hashing-disclosures">Hashing Disclosures</a>
 */
@JvmInline
value class HashedDisclosure private constructor(val value: String) {
    companion object {

        fun create(hashingAlgorithm: HashAlgorithm, d: Disclosure): Result<HashedDisclosure> {

            fun base64UrlEncodedDigestOf(value: String): String {
                val hashFunction = hashing().of(hashingAlgorithm)
                val digest = hashFunction(value.encodeToByteArray())
                return JwtBase64.encodeString(digest)
            }
            return runCatching { HashedDisclosure(base64UrlEncodedDigestOf(d.value)) }
        }


    }
}