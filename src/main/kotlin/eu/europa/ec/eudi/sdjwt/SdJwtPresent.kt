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

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [pointer][JsonPointer] and the [disclosures][Disclosure]
 * required to revel the claim
 */
typealias DisclosuresPerClaim = Map<JsonPointer, List<Disclosure>>

/**
 * Gets each claim alongside the [Disclosures][Disclosure] that are required to disclose it.
 */
fun <JWT> SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> Claims): Pair<Claims, DisclosuresPerClaim> {
    val disclosuresPerClaim = mutableMapOf<JsonPointer, List<Disclosure>>()
    val visitor = SdClaimVisitor { path, disclosure ->
        if (disclosure != null) {
            require(path !in disclosuresPerClaim.keys) { "Disclosures for $path have already been calculated." }
        }
        val claimDisclosures = run {
            val containerPath = path.parent()
            val containerDisclosures = containerPath?.let { disclosuresPerClaim[it] }.orEmpty()
            disclosure
                ?.let { containerDisclosures + it }
                ?: containerDisclosures
        }
        disclosuresPerClaim.putIfAbsent(path, claimDisclosures)
    }
    val claims = recreateClaims(visitor, claimsOf)
    return claims to disclosuresPerClaim
}

fun <JWT> SdJwt.Issuance<JWT>.present(
    query: Set<JsonPointer>,
    claimsOf: (JWT) -> Claims,
): SdJwt.Presentation<JWT>? =
    present({ it in query }, claimsOf)

fun <JWT> SdJwt.Issuance<JWT>.present(
    query: (JsonPointer) -> Boolean,
    claimsOf: (JWT) -> Claims,
): SdJwt.Presentation<JWT>? {
    val (_, disclosuresPerClaim) = recreateClaimsAndDisclosuresPerClaim(claimsOf)
    val keys = disclosuresPerClaim.keys.filter(query)
    return if (keys.isEmpty()) null
    else {
        val ds = disclosuresPerClaim.filterKeys { it in keys }.values.flatten().toSet()
        SdJwt.Presentation(jwt, ds.toList())
    }
}

fun SdJwt.Issuance<JwtAndClaims>.present(
    query: Set<JsonPointer>,
): SdJwt.Presentation<JwtAndClaims>? = present(query) { (_, claims) -> claims }

fun SdJwt.Issuance<JwtAndClaims>.present(
    query: (JsonPointer) -> Boolean,
): SdJwt.Presentation<JwtAndClaims>? = present(query) { (_, claims) -> claims }
