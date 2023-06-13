package eu.europa.ec.eudi.sdjwt

import kotlin.random.Random

/**
 * An interface for generating [Salt] values.
 */
fun interface SaltProvider {

    /**
     * Provides a new [Salt]
     * @return a new [Salt] value
     */
    fun salt(): Salt

    companion object {

        /**
         * A default implementation of [SaltProvider].
         * It produces random [Salt] values of size 16 bytes (128 bits)
         */
        val Default: SaltProvider by lazy { randomSaltProvider(16) }

        private val secureRandom: Random = Random.Default

        /**
         * Creates a salt provider which generates random [Salt] values
         *
         * @param numberOfBytes the size of salt in bytes
         *
         * @return a salt provider which generates random [Salt] values
         */
        fun randomSaltProvider(numberOfBytes: Int): SaltProvider =
            SaltProvider {
                val randomByteArray: ByteArray = ByteArray(numberOfBytes).also { secureRandom.nextBytes(it) }
                JwtBase64.encodeString(randomByteArray)
            }
    }
}

