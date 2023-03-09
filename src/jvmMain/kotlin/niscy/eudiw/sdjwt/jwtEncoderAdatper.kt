package niscy.eudiw.sdjwt

import kotlinx.serialization.json.JsonObject

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSObject as NimbusJWSObject
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.Payload as NimbusPayload
import com.nimbusds.jose.crypto.ECDSASigner as NimbusECDSASigner
import com.nimbusds.jose.crypto.MACSigner as NimbusMACSigner
import com.nimbusds.jose.crypto.RSASSASigner as NimbusRSASSASigner


actual fun jwtOps(): JwtEncoder = NimbusJwtEncoder

private object NimbusJwtEncoder : JwtEncoder {

    override fun encode(
        key: String,
        algorithm: JwtAlgorithm,
        jwtClaimSet: JsonObject
    ): Result<Jwt> = runCatching {

        val header = NimbusJWSHeader(algorithm.toNimbus())
        val payload = NimbusPayload(jwtClaimSet.asBytes())

        with(NimbusJWSObject(header, payload)) {
            val signer = signer(key, algorithm)
            sign(signer)
            serialize()
        }
    }


    private fun signer(key: String, alg: JwtAlgorithm): NimbusJWSSigner = when (alg) {
        JwtAlgorithm.Hmac.HS256 -> NimbusMACSigner(key)
        JwtAlgorithm.Hmac.HS384 -> NimbusMACSigner(key)
        JwtAlgorithm.Hmac.HS512 -> NimbusMACSigner(key)
        JwtAlgorithm.DigSig.RS256 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.RS384 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.RS512 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.PS256 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.PS384 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.PS512 -> NimbusRSASSASigner(parsePrivateKey(key, Family.RSA))
        JwtAlgorithm.DigSig.ES256 -> NimbusECDSASigner(parsePrivateKey(key, Family.ECDSA) as ECPrivateKey)
        JwtAlgorithm.DigSig.ES384 -> NimbusECDSASigner(parsePrivateKey(key, Family.ECDSA) as ECPrivateKey)
        JwtAlgorithm.DigSig.ES512 -> NimbusECDSASigner(parsePrivateKey(key, Family.ECDSA) as ECPrivateKey)
    }


    private enum class Family(val alias: String) {
        RSA("RSA"),
        ECDSA("EC"),
        EdDSA("EdDSA")
    }

    private fun parsePrivateKey(key: String, keyAlgo: Family): PrivateKey {
        val spec = PKCS8EncodedKeySpec(parseKey(key))
        return KeyFactory.getInstance(keyAlgo.alias).generatePrivate(spec)
    }


    private fun parseKey(key: String): ByteArray = JwtBase64.decodeNonSafe(
        key
            .replace("-----BEGIN (.*)-----", "")
            .replace("-----END (.*)-----", "")
            .replace("\r\n", "")
            .replace("\n", "")
            .trim()
    )

    private fun JwtAlgorithm.toNimbus(): NimbusJWSAlgorithm = when (this) {
        JwtAlgorithm.Hmac.HS256 -> NimbusJWSAlgorithm.HS256
        JwtAlgorithm.Hmac.HS384 -> NimbusJWSAlgorithm.HS384
        JwtAlgorithm.Hmac.HS512 -> NimbusJWSAlgorithm.HS512
        JwtAlgorithm.DigSig.RS256 -> NimbusJWSAlgorithm.RS256
        JwtAlgorithm.DigSig.RS384 -> NimbusJWSAlgorithm.RS384
        JwtAlgorithm.DigSig.RS512 -> NimbusJWSAlgorithm.RS512
        JwtAlgorithm.DigSig.ES256 -> NimbusJWSAlgorithm.ES256
        JwtAlgorithm.DigSig.ES384 -> NimbusJWSAlgorithm.ES384
        JwtAlgorithm.DigSig.ES512 -> NimbusJWSAlgorithm.ES512
        JwtAlgorithm.DigSig.PS256 -> NimbusJWSAlgorithm.PS256
        JwtAlgorithm.DigSig.PS384 -> NimbusJWSAlgorithm.PS384
        JwtAlgorithm.DigSig.PS512 -> NimbusJWSAlgorithm.PS512
    }
}

private fun JsonObject.asBytes(): ByteArray = toString().encodeToByteArray()

fun SdJwtOps.flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: JsonObject = JsonObject(emptyMap()),
    claimToBeDisclosed: Pair<String, JsonObject>
): Result<CombinedIssuanceSdJwt> = runCatching {

    val (ds, jwtClaimSet) = DislosureOps.flatDisclose(
        hashAlgorithm,
        saltProvider,
        jwtClaims,
        claimToBeDisclosed
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }
    jwt + encodeDisclosures(ds)
}