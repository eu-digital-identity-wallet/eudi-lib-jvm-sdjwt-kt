package niscy.eudiw.sdjwt

/**
 *  According to JSON Web Algorithms (JWA)
 */
sealed interface JwtAlgorithm {

    val name: String

    enum class Hmac : JwtAlgorithm {
        HS256, // HMAC using SHA-256 (Required)
        HS384, // HMAC using SHA-384
        HS512 // HMAC using SHA-512
    }

    enum class DigSig : JwtAlgorithm {
        RS256, // RSASSA-PKCS1-v1_5 using SHA-256 (Recommended)
        RS384, // RSASSA-PKCS1-v1_5 using SHA-384
        RS512, // RSASSA-PKCS1-v1_5 using SHA-512
        ES256, // ECDSA using P-256 and SHA-256 (Recommended)
        ES384, // ECDSA using P-384 and SHA-384
        ES512, // ECDSA using P-521 and SHA-512
        PS256, // RSASSA-PSS using SHA-256 and MGF1 with SHA-256
        PS384,
        PS512
    }


    companion object {
        fun jwtAlgorithm(name: String): JwtAlgorithm? = hmacAlgorithm(name) ?: digSigAlgorithm(name)

        fun hmacAlgorithm(name: String): Hmac? =
            Hmac.values().find { it.name == name }

        fun digSigAlgorithm(name: String): DigSig? =
            DigSig.values().find { it.name == name }
    }
}


fun String.toJwtAlgorithm(): JwtAlgorithm? = JwtAlgorithm.jwtAlgorithm(this)

