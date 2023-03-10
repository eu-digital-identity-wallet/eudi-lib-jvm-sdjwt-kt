package niscy.eudiw.sdjwt

import java.util.*

actual fun base64UrlCodec(): Base64UrlCodec = Base64UrlCodecJavaAdapter

object Base64UrlCodecJavaAdapter : Base64UrlCodec {
    val encoder: Base64.Encoder by lazy { Base64.getUrlEncoder() }
    val decoder: Base64.Decoder by lazy { Base64.getUrlDecoder() }
    override fun encode(value: ByteArray): ByteArray = encoder.encode(value)
    override fun encodeToString(value: ByteArray): String = encoder.encodeToString(value)
    override fun decode(value: ByteArray): ByteArray = decoder.decode(value)
    override fun decode(value: String): ByteArray = decoder.decode(value)
}


