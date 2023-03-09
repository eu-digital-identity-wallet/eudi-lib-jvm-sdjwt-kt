package niscy.eudiw.sdjwt

import java.security.MessageDigest

actual fun hashing(): HashSupport =
    object : HashSupport {
        override fun of(algorithm: HashAlgorithm): (ByteArray) -> ByteArray {
            val hashFunction = MessageDigest.getInstance(algorithm.alias.uppercase())
            return { input: ByteArray -> hashFunction.digest(input)!! }
        }

    }