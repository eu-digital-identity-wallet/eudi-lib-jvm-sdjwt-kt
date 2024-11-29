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

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [path][ClaimPath] and the [disclosures][Disclosure]
 * required to revel the claim
 */
typealias DisclosuresPerClaimPath = Map<ClaimPath, List<Disclosure>>

/**
 * Recreates the claims, used to produce the SD-JWT and at the same time calculates [DisclosuresPerClaim]
 *
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 *
 * @see SdJwt.recreateClaims
 */
fun <JWT> SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> Claims): Pair<Claims, DisclosuresPerClaimPath> {
    val disclosuresPerClaim = mutableMapOf<ClaimPath, List<Disclosure>>()
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
 * Tries to create a presentation that discloses the [requested claims][query].
 * @param query a set of [ClaimPaths][ClaimPath] to include in the presentation. The [ClaimPaths][ClaimPath]
 * are relative to the unprotected JSON (not the JWT payload)
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @param JWT the type representing the JWT part of the SD-JWT
 * @return the presentation if possible to satisfy the [query]
 */
fun <JWT> SdJwt.Issuance<JWT>.present(
    query: Set<ClaimPath>,
    claimsOf: (JWT) -> Claims,
): SdJwt.Presentation<JWT>? {
    val disclosuresPerClaim = recreateClaimsAndDisclosuresPerClaim(claimsOf).second
    val keys = disclosuresPerClaim.keys.filter { claim ->
        query.any { requested ->
            claim matches requested
        }
    }
    return if (keys.isEmpty()) null
    else {
        val ds = disclosuresPerClaim.filterKeys { it in keys }.values.flatten().toSet()
        SdJwt.Presentation(jwt, ds.toList())
    }
}

/**
 * Creates a Presentation that discloses **ALL** the claims of this Issuance.
 */
fun <JWT> SdJwt.Issuance<JWT>.present(): SdJwt.Presentation<JWT> = SdJwt.Presentation(jwt, disclosures)
