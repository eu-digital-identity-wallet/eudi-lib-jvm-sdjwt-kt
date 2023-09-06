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


import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter

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
    val type: List<Type>,
    val id: Id,
    val issuer: Issuer,
    val issuanceDate: Instant,
    val expirationDate: Instant?,
    val credentialSubject: CredentialSubject<CLAIMS>,
)

fun main() {
    val vc = VerifiableCredential<JsonObject>(
        context = listOf(
            "https://www.w3.org/2018/credentials/v1",
            "https://www.w3.org/2018/credentials/examples/v1",
        ).map { URI.create(it) },
        id = URI.create("http://example.com/credentials/4643"),
        type = listOf("VerifiableCredential"),
        issuer = URI.create("https://example.com/issuers/14"),
        issuanceDate = Instant.parse("2018-02-24T05:28:04Z"),
        expirationDate = null,
        credentialSubject = CredentialSubject(
            id = URI.create("did:example:abcdef1234567"),
            claims = buildJsonObject {
                put("name", "Jane Doe")
            },
        ),
    )
    val jwtCS = jwtClaimSetOf(vc, listOf("aud"), true) { it }
    println(json.encodeToString(jwtCS))
}

fun <CLAIMS> jwtClaimSetOf(
    vc: VerifiableCredential<CLAIMS>,
    aud: List<String>,
    duplicate: Boolean = false,
    f: (CLAIMS) -> JsonObject,
): JsonObject {
    return buildJsonObject {
        vc.expirationDate?.let { exp(it.epochSecond) }
        iss(vc.issuer.toString())
        nbf(vc.issuanceDate.epochSecond)
        jti(vc.id.toString())
        sub(vc.credentialSubject.id.toString())
        aud(*aud.toTypedArray())
        putJsonObject("vc") {
            if (duplicate) {
                put("id", vc.id.toString())
                vc.expirationDate?.let {
                    put("expirationDate", DateTimeFormatter.ISO_DATE_TIME.format(it))
                }

                put("issuer", vc.issuer.toString())
                put("issuanceDate", DateTimeFormatter.ISO_ZONED_DATE_TIME.format(vc.issuanceDate))
            }
            val cs = f(vc.credentialSubject.claims).toMutableMap()
            if (duplicate) {
                cs["id"] = JsonPrimitive(vc.credentialSubject.id.toString())
            }
            put("credentialSubject", JsonObject(cs))
        }
    }
}
