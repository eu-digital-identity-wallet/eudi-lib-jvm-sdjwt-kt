import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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


val format = Json { prettyPrint = true }


fun sdJwtForVC(jwtVcJson: JsonObject, rsaKey: RSAKey): Result<CombinedIssuanceSdJwt> = runCatching {
    val credentialSubjectJson = jwtVcJson["credentialSubject"]!!.jsonObject
    val plainJwtAttributes = JsonObject(jwtVcJson.minus("credentialSubject"))

    flatDiscloseAndEncode(
        signer = com.nimbusds.jose.crypto.RSASSASigner(rsaKey),
        algorithm = com.nimbusds.jose.JWSAlgorithm.RS256,
        hashAlgorithm = HashAlgorithm.SHA3_512,
        jwtClaims = plainJwtAttributes,
        claimToBeDisclosed = "credentialSubject" to credentialSubjectJson
    ).getOrThrow()

}

fun genRSAKeyPair(): RSAKey =
    RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key (optional)
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID (optional)
        .issueTime(Date()) // issued-at timestamp (optional)
        .generate()

fun extractVerifiableCredentialsClaim(json: JsonObject): Pair<JsonObject, JsonObject> {
    val credentialSubjectJson = json["credentialSubject"]!!.jsonObject
    val plainJwtAttributes = JsonObject(json.minus("credentialSubject"))
    return plainJwtAttributes to credentialSubjectJson
}


fun main() {

    // this is the json we want to include in the JWT (not disclosed)
    val jwtVcJson: JsonObject = format.parseToJsonElement(jwt_vc_payload).jsonObject
    val (jwtClaims, vcClaim) = extractVerifiableCredentialsClaim(jwtVcJson)


    // Generate an RSA key pair
    val rsaJWK = genRSAKeyPair()
    val rsaPublicJWK = rsaJWK.toPublicJWK().also { println("\npublic key\n================\n$it") }

    val sdJwt: CombinedIssuanceSdJwt = flatDiscloseAndEncode(
        signer = com.nimbusds.jose.crypto.RSASSASigner(rsaJWK),
        algorithm = com.nimbusds.jose.JWSAlgorithm.RS256,
        hashAlgorithm = HashAlgorithm.SHA3_512,
        jwtClaims = jwtClaims,
        claimToBeDisclosed = "credentialSubject" to vcClaim
    ).getOrThrow()


    val (jwt, disclosures) = sdJwt.split().getOrThrow()

    println("\nJWT-VC payload\n================")
    println(jwt_vc_payload)
    println("\nVC as sd-jwt\n================")
    println(sdJwt)
    println("\nJwt\n================")
    println(jwt)
    println("\nDisclosures\n================")
    disclosures.forEach { println(it.claim()) }


    val claimSet = verify(jwt, disclosures, RSASSAVerifier(rsaPublicJWK)).getOrThrow()

    println("\nVerified Claim Set \n================")
    println(format.encodeToString(claimSet))
}

fun verify(jwt: Jwt, ds: List<Disclosure>, verifier: com.nimbusds.jose.JWSVerifier): Result<JsonObject> =
    runCatching {
        val signedJwt = SignedJWT.parse(jwt)
        check(signedJwt.verify(verifier)) { "Signature verification failed" }
        val sdAlg = signedJwt.jwtClaimsSet.getStringClaim("_sd_alg")
            ?: throw IllegalArgumentException("Missing _sd_alg attribute")
        val hashAlg = HashAlgorithm.fromString(sdAlg) ?: throw IllegalArgumentException("Unsupported hash alg $sdAlg")
        val calculatedHashes = ds.associateBy { HashedDisclosure.create(hashAlg, it).getOrThrow() }

        val str = signedJwt.jwtClaimsSet.toString(false)
        val claimSet = format.parseToJsonElement(str).jsonObject
        val ehds: List<HashedDisclosure> = extractDisclosureHashes(claimSet).getOrThrow()
        if (calculatedHashes.keys.any { !ehds.contains(it) }) throw IllegalArgumentException("Hash mismatch")

        claimSet


    }

fun extractDisclosureHashes(j: JsonObject): Result<List<HashedDisclosure>> = runCatching {
    val hds: List<HashedDisclosure> = j["_sd"]?.jsonArray?.map {
        check(it.jsonPrimitive.isString)
        HashedDisclosure.wrap(it.jsonPrimitive.content).getOrThrow()
    } ?: emptyList()


    hds + j.values.filterIsInstance<JsonObject>().flatMap { extractDisclosureHashes(it).getOrThrow() }
}