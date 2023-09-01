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

// import com.danubetech.verifiablecredentials.CredentialSubject
// import com.danubetech.verifiablecredentials.VerifiableCredential
// import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialContexts
// import foundation.identity.jsonld.JsonLDUtils
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant

typealias JsonLdContext = URI
typealias Id = URI
typealias Type = String
typealias Issuer = URI

data class CredentialSubject<out CLAIMS>(
    val id: Id,
    val claims: CLAIMS,
)
data class VerifiableCredential<out CLAIMS>(
    val context: List<JsonLdContext>,
    val type: Type,
    val id: Id,
    val issuer: Issuer,
    val issuanceDate: Instant,
    val expirationDate: Instant,
    val credentialSubject: CredentialSubject<CLAIMS>,
)

fun <CLAIMS>jwtClaimSetOf(
    vc: VerifiableCredential<CLAIMS>,
    f: (CLAIMS) -> JsonElement,
): Pair<JsonObject, JsonObject> {
    val jwsHeader = buildJsonObject {
        put("typ", "JWT")
    }

    val jwtClaimSet = buildJsonObject {
        exp(vc.expirationDate.epochSecond)
        iss(vc.issuer.toString())

        putJsonObject("vc") {
        }
    }
    return jwsHeader to jwtClaimSet
}
