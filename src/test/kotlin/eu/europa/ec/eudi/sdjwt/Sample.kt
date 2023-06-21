/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.sdjwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.*

val hmacKey = "111111111111111111111111111111111111111111"

val jwtVcPayload = """{
  "iss": "https://example.com",
  "jti": "http://example.com/credentials/3732",
  "nbf": 1541493724,
  "iat": 1541493724,
  "cnf": {
    "jwk": {
      "kty": "RSA",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e": "AQAB"
    }
  },
  "type": "IdentityCredential",
  "credentialSubject": {
    "given_name": "John",
    "family_name": "Doe",
    "email": "johndoe@example.com",
    "phone_number": "+1-202-555-0101",
    "address": {
      "street_address": "123 Main St",
      "locality": "Anytown",
      "region": "Anystate",
      "country": "US"
    },
    "birthdate": "1940-01-01",
    "is_over_18": true,
    "is_over_21": true,
    "is_over_65": true
  }
}
""".trimIndent()

val format = Json { prettyPrint = true }

fun genRSAKeyPair(): RSAKey =
    RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key (optional)
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID (optional)
        .issueTime(Date()) // issued-at timestamp (optional)
        .generate()

fun main() {
    // this is the json we want to include in the JWT (not disclosed)
    val jwtVcJson: JsonObject = format.parseToJsonElement(jwtVcPayload).jsonObject
    val (jwtClaims, vcClaim) = jwtVcJson.extractClaim("credentialSubject")

    // Generate an RSA key pair
    val rsaJWK = genRSAKeyPair()
    val rsaPublicJWK = rsaJWK.toPublicJWK().also { println("\npublic key\n================\n$it") }

    val sdJwt: CombinedIssuanceSdJwt = SdJwt.structureDiscloseAndEncode(
        signer = RSASSASigner(rsaJWK),
        algorithm = JWSAlgorithm.RS256,
        hashAlgorithm = HashAlgorithm.SHA3_512,
        jwtClaims = jwtClaims,
        claimsToBeDisclosed = vcClaim,
        numOfDecoys = 0,
    ).getOrThrow()

    val (jwt, disclosures) = sdJwt.decompose().getOrThrow()

    println("\nJWT-VC payload\n================")
    println(jwtVcPayload)
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
