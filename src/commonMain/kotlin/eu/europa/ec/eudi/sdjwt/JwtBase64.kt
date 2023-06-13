package eu.europa.ec.eudi.sdjwt


interface Base64UrlCodec {
    fun encode(value: ByteArray): ByteArray
    fun encodeToString(value: ByteArray): String
    fun decode(value: ByteArray): ByteArray
    fun decode(value: String): ByteArray
}


object JwtBase64 {

    private val base64UrlCodec: Base64UrlCodec = base64UrlCodec()

    fun encode(value: ByteArray): ByteArray = base64UrlCodec.encode(value)
    fun decode(value: ByteArray): ByteArray = base64UrlCodec.decode(value)

    fun encode(value: String): ByteArray = encode(value.encodeToByteArray())
    fun decode(value: String): ByteArray = base64UrlCodec.decode(value)

    // Since the complement character "=" is optional,
    // we can remove it to save some bits in the HTTP header
    fun encodeString(value: ByteArray): String = base64UrlCodec.encodeToString(value).replace("=", "")
    fun decodeString(value: ByteArray): String = decode(value).decodeToString()

    fun encodeString(value: String): String = encodeString(value.encodeToByteArray())
    fun decodeString(value: String): String = decodeString(value.encodeToByteArray())

}