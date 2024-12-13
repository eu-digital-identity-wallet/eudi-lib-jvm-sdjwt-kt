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
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.runBlocking

val issuedSdJwt: String = runBlocking {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val sdJwtSpec = sdJwt {
        claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        claim("iss", "https://example.com/issuer")
        claim("iat", 1516239022)
        claim("exp", 1735689661)
        objClaim("address") {
            sdClaim("street_address", "Schulstr. 12")
            sdClaim("locality", "Schulpforta")
            sdClaim("region", "Sachsen-Anhalt")
            sdClaim("country", "DE")
        }
    }
    with(NimbusSdJwtOps) {
        val issuer = issuer(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
        issuer.issue(sdJwtSpec).getOrThrow().serialize()
    }
}
