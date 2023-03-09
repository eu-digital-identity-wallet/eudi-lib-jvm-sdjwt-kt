import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import niscy.eudiw.sdjwt.*
import java.util.*


val hmacKey = "111111111111111111111111111111111111111111"

val jwt_vc_payload = "{\n" +
        "  \"iss\": \"https://example.com\",\n" +
        "  \"jti\": \"http://example.com/credentials/3732\",\n" +
        "  \"nbf\": 1541493724,\n" +
        "  \"iat\": 1541493724,\n" +
        "  \"cnf\": {\n" +
        "    \"jwk\": {\n" +
        "      \"kty\": \"RSA\",\n" +
        "      \"n\": \"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\n" +
        "      \"e\": \"AQAB\"\n" +
        "    }\n" +
        "  },\n" +
        "  \"type\": \"IdentityCredential\",\n" +
        "  \"credentialSubject\": {\n" +
        "    \"given_name\": \"John\",\n" +
        "    \"family_name\": \"Doe\",\n" +
        "    \"email\": \"johndoe@example.com\",\n" +
        "    \"phone_number\": \"+1-202-555-0101\",\n" +
        "    \"address\": {\n" +
        "      \"street_address\": \"123 Main St\",\n" +
        "      \"locality\": \"Anytown\",\n" +
        "      \"region\": \"Anystate\",\n" +
        "      \"country\": \"US\"\n" +
        "    },\n" +
        "    \"birthdate\": \"1940-01-01\",\n" +
        "    \"is_over_18\": true,\n" +
        "    \"is_over_21\": true,\n" +
        "    \"is_over_65\": true\n" +
        "  }\n" +
        "}"


val format = Json { prettyPrint=true }



fun sdJwtForVC(jwtVcJson: JsonObject, rsaKey: RSAKey): Result<CombinedIssuanceSdJwt> = runCatching {
    val credentialSubjectJson = jwtVcJson["credentialSubject"]!!.jsonObject
    val plainJwtAttributes = JsonObject( jwtVcJson.minus("credentialSubject"))

    SdJwtOps.flatDiscloseAndEncode(
        signer = com.nimbusds.jose.crypto.RSASSASigner(rsaKey),
        algorithm = com.nimbusds.jose.JWSAlgorithm.RS256,
        hashAlgorithm = HashAlgorithm.SHA3_512,
        jwtClaims = plainJwtAttributes,
        claimToBeDisclosed = "credentialSubject" to credentialSubjectJson
    ).getOrThrow()

}

fun genRSAKeyPair() : RSAKey  =
    RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key (optional)
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID (optional)
        .issueTime(Date()) // issued-at timestamp (optional)
        .generate()




fun main() {


    val jwtVcJson : JsonObject = format.parseToJsonElement(jwt_vc_payload).jsonObject

    val rsaJWK = genRSAKeyPair()


    val sdJwt : CombinedIssuanceSdJwt = sdJwtForVC(jwtVcJson, rsaJWK).getOrThrow()

    val (jwt, disclosures) = sdJwt.split().getOrThrow()

    println("\nJWT-VC payload\n================")
    println(jwt_vc_payload)
    println("\nVC as sd-jwt\n================")
    println(sdJwt)
    println("\nSD-JWT-VC\n================")
    println(jwt)
    println("\nDisclosures\n================")
    disclosures.forEach { println(it.claim()) }


    val claimSet = com.nimbusds.jwt.SignedJWT.parse(jwt).run {
        val rsaPublicJWK = rsaJWK.toPublicJWK().also{jwk-> println("\npublic key\n" +
                "================\n$jwk") }
        val verifier =  com.nimbusds.jose.crypto.RSASSAVerifier(rsaPublicJWK)
        require( verify(verifier))
        jwtClaimsSet.toString(false)
    }!!
    println("\nVerified Claim Set \n================")
    format.parseToJsonElement(claimSet).also { println(format.encodeToString(it)) }


}

