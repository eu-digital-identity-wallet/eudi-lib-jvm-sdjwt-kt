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
package eu.europa.ec.eudi.sdjwt.examples

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jose.jwk.*
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.jsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

val claims: JsonObject = runBlocking {
    val issuerKeyPair: RSAKey = loadRsaKey("/examplesIssuerKey.json")
    val sdJwt: SdJwt.Issuance<NimbusSignedJWT> =
        signedSdJwt(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256) {
            plain {
                sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
                iss("https://example.com/issuer")
                iat(1516239022)
                exp(1735689661)
            }
            structured("address") {
                sd {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }
    sdJwt.recreateClaims { jwt -> jwt.jwtClaimsSet.jsonObject() }
}
