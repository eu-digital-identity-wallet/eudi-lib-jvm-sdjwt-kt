package niscy.eudiw.sdjwt

expect fun hashing(): HashSupport
expect fun base64UrlCodec() : Base64UrlCodec
expect fun base64Codec(): Base64Codec
expect fun jwtOps(): JwtEncoder