package eu.europa.ec.eudi.sdjwt

/**
 * The hash of a [disclosure][Disclosure]
 *
 */
@JvmInline
value class HashedDisclosure private constructor(val value: String) {
    companion object {

        /**
         * Wraps the given [string][s] into [HashedDisclosure]
         * It expects that the given input is base64-url encoded. If not an exception is thrown
         *
         * @param s the value to wrap
         * @return the [HashedDisclosure] if the given input represents a valid base64 encoded string
         */
        internal fun wrap(s: String): Result<HashedDisclosure> = runCatching {
            JwtBase64.decode(s)
            HashedDisclosure(s)
        }

        /**
         * Calculates the hash of the given [disclosure][d] using the specified [hashing algorithm][hashingAlgorithm]
         *
         * @param hashingAlgorithm the hashing algorithm to use
         * @param d the disclosure to hash
         *
         * @return the [HashedDisclosure] of the given [disclosure][d]
         */
        fun create(hashingAlgorithm: HashAlgorithm, d: Disclosure): Result<HashedDisclosure> = runCatching {
            val value = base64UrlEncodedDigestOf(hashingAlgorithm, d.value)
            HashedDisclosure(value)
        }

        internal fun base64UrlEncodedDigestOf(hashingAlgorithm: HashAlgorithm, disclosureValue: String): String {
            val hashFunction = hashing().of(hashingAlgorithm)
            val digest = hashFunction(disclosureValue.encodeToByteArray())
            return JwtBase64.encodeString(digest)
        }
    }
}