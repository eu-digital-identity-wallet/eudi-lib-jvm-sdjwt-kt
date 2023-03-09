package niscy.eudiw.sdjwt

import kotlin.random.Random


fun interface SaltProvider {
    fun salt(): Salt

    companion object {

        val Default : SaltProvider by lazy {  randomSaltProvider(16) }

        private val secureRandom: Random = Random.Default

        fun randomSaltProvider(numberOfBytes: Int): SaltProvider =
            SaltProvider {
                val randomByteArray: ByteArray = ByteArray(numberOfBytes).also { secureRandom.nextBytes(it) }
                JwtBase64.encodeString(randomByteArray)
            }
    }
}

