package niscy.eudiw.sdjwt

/**
 * The hash of a disclosure
 * @see <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-02.html#name-hashing-disclosures">Hashing Disclosures</a>
 */
@JvmInline
value class HashedDisclosure private constructor(val value: String) {
    companion object {

        /**
         * Wraps the given [string][s] into [HashedDisclosure]
         * It expects that the given input is base64-url encoded. If not an exception is thrown
         */
        fun wrap(s: String): Result<HashedDisclosure> = runCatching {
            JwtBase64.decode(s)
            HashedDisclosure(s)
        }

        /**
         * Calculates the hash of the given [disclosure][d] using the specified [hashing algorithm][hashingAlgorithm]
         */
        fun create(hashingAlgorithm: HashAlgorithm, d: Disclosure): Result<HashedDisclosure> = runCatching {
            fun base64UrlEncodedDigestOf(): String {
                val hashFunction = hashing().of(hashingAlgorithm)
                val digest = hashFunction(d.value.encodeToByteArray())
                return JwtBase64.encodeString(digest)
            }
            val value = base64UrlEncodedDigestOf()
            HashedDisclosure(value)
        }
    }
}