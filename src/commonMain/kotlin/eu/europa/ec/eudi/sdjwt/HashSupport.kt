package eu.europa.ec.eudi.sdjwt

interface HashSupport {
    fun of(algorithm: HashAlgorithm): (ByteArray) -> ByteArray
}

