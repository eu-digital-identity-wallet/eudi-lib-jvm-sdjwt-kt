package niscy.eudiw.sdjwt

import java.security.MessageDigest

actual fun hashing(): HashSupport = HashSupportJavaAdapter

object HashSupportJavaAdapter : HashSupport {
    override fun of(algorithm: HashAlgorithm): (ByteArray) -> ByteArray {
        val hashFunction = MessageDigest.getInstance(algorithm.alias.uppercase())
        return { hashFunction.digest(it)!! }
    }
}