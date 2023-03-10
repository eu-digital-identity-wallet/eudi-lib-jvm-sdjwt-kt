package niscy.eudiw.sdjwt

interface HashSupport {
    fun of(algorithm: HashAlgorithm): (ByteArray) -> ByteArray
}

