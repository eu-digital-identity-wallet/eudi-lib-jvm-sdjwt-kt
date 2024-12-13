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
import kotlinx.serialization.json.JsonObject

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [path][ClaimPath] and the [disclosures][Disclosure]
 * required to revel the claim
 */
typealias DisclosuresPerClaimPath = Map<ClaimPath, List<Disclosure>>

interface SdJwtPresentationOps<JWT> : SdJwtRecreateClaimsOps<JWT> {
    /**
     * Recreates the claims, used to produce the SD-JWT and at the same time calculates [DisclosuresPerClaimPath]
     *
     * @param JWT the type representing the JWT part of the SD-JWT
     *
     * @see SdJwt.recreateClaims
     */
    fun SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(): Pair<JsonObject, DisclosuresPerClaimPath> {
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
        val claims = recreateClaims(visitor)
        return claims to disclosuresPerClaim
    }

    /**
     * Tries to create a presentation that discloses the [requested claims][query].
     * @param query a set of [ClaimPaths][ClaimPath] to include in the presentation. The [ClaimPaths][ClaimPath]
     * are relative to the unprotected JSON (not the JWT payload)
     * @receiver The issuance SD-JWT upon which the presentation will be based
     * @param JWT the type representing the JWT part of the SD-JWT
     * @return the presentation if possible to satisfy the [query]
     */
    fun SdJwt.Issuance<JWT>.present(query: Set<ClaimPath>): SdJwt.Presentation<JWT>? {
        val (_, disclosuresPerClaim) = recreateClaimsAndDisclosuresPerClaim()
        infix fun ClaimPath.matches(other: ClaimPath): Boolean =
            (value.size == other.value.size) && (this in other)

        val keys = disclosuresPerClaim.keys.filter { claimFound ->
            query.any { requested -> claimFound matches requested }
        }
        return if (keys.isEmpty()) null
        else {
            val ds = disclosuresPerClaim.filterKeys { it in keys }.values.flatten().toSet()
            SdJwt.Presentation(jwt, ds.toList())
        }
    }

    companion object {
        operator fun <JWT> invoke(claimsOf: (JWT) -> JsonObject): SdJwtPresentationOps<JWT> =
            object : SdJwtPresentationOps<JWT>, SdJwtRecreateClaimsOps<JWT> by SdJwtRecreateClaimsOps(claimsOf) {}
    }
}

/**
 * Creates a Presentation that discloses **ALL** the claims of this Issuance.
 */
fun <JWT> SdJwt.Issuance<JWT>.present(): SdJwt.Presentation<JWT> = SdJwt.Presentation(jwt, disclosures)
