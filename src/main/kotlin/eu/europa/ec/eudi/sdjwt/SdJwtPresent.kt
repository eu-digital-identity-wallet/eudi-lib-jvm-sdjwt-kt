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

import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.toClaimPath

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [pointer][JsonPointer] and the [disclosures][Disclosure]
 * required to revel the claim
 */
typealias DisclosuresPerClaim = Map<JsonPointer, List<Disclosure>>

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [path][ClaimPath] and the [disclosures][Disclosure]
 * required to revel the claim
 */
typealias DisclosuresPerClaimPath = Map<ClaimPath, List<Disclosure>>

fun DisclosuresPerClaim.usePath(): DisclosuresPerClaimPath =
    mapKeys { (jsonPointer, _) -> jsonPointer.toClaimPath(false) }

/**
 * Recreates the claims, used to produce the SD-JWT and at the same time calculates [DisclosuresPerClaim]
 *
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 *
 * @see SdJwt.recreateClaims
 */
fun <JWT> SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> Claims): Pair<Claims, DisclosuresPerClaim> {
    val disclosuresPerClaim = mutableMapOf<JsonPointer, List<Disclosure>>()
    val visitor = ClaimVisitor { path, disclosure ->
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

/**
 * Tries to create a presentation that discloses the claims that satisfy
 * [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @param JWT the type representing the JWT part of the SD-JWT
 * @return the presentation if possible to satisfy the [query]
 */
fun <JWT> SdJwt.Issuance<JWT>.presentJsonPointersMatching(
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

fun <JWT> SdJwt.Issuance<JWT>.present(
    query: Set<ClaimPath>,
    claimsOf: (JWT) -> Claims,
): SdJwt.Presentation<JWT>? {
    val (_, disclosuresPerClaimTmp) = recreateClaimsAndDisclosuresPerClaim(claimsOf)
    val disclosuresPerClaim = disclosuresPerClaimTmp.usePath()
    val keys = disclosuresPerClaim.keys.filter { disclosed ->
        query.any { requested ->
            disclosed in requested
        }
    }
    return if (keys.isEmpty()) null
    else {
        val ds = disclosuresPerClaim.filterKeys { it in keys }.values.flatten().toSet()
        SdJwt.Presentation(jwt, ds.toList())
    }
}
