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

import kotlinx.serialization.json.JsonElement

typealias JsonPath = String

typealias DisclosuresPerClaim = Map<SingleClaimJsonPath, List<Disclosure>>

/**
 * Gets the full [JsonPath] of each selectively disclosed claim alongside the [Disclosures][Disclosure] that are required
 * to disclose it.
 */
fun <JWT> SdJwt.Issuance<JWT>.disclosuresPerClaim(claimsOf: (JWT) -> Claims): DisclosuresPerClaim =
    recreateClaimsAndDisclosuresPerClaim(claimsOf).second

fun UnsignedSdJwt.disclosuresPerClaim(): DisclosuresPerClaim = disclosuresPerClaim { it }

fun <JWT> SdJwt.Issuance<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> Claims): Pair<Claims, DisclosuresPerClaim> {
    val disclosuresPerClaim = mutableMapOf<SingleClaimJsonPath, List<Disclosure>>()
    val visitor = SdClaimVisitor { current, disclosure ->
        require(current !in disclosuresPerClaim.keys) { "Disclosures for claim $current have already been calculated." }
        disclosuresPerClaim[current] = (disclosuresPerClaim[current.partOf()] ?: emptyList()) + disclosure
    }
    val claims = recreateClaims(visitor, claimsOf)
    return claims to disclosuresPerClaim
}

sealed interface Query {
    data object OnlyNonSelectivelyDisclosableClaims : Query
    data class ClaimInPath(val path: JsonPath, val filter: (JsonElement) -> Boolean = { true }) : Query
    data class Many(val claimsInPath: List<ClaimInPath>) : Query
    data object AllClaims : Query
}

fun <JWT> SdJwt.Issuance<JWT>.present(query: Query, claimsOf: (JWT) -> Claims): SdJwt.Presentation<JWT>? =
    Presenter(claimsOf, false).present(this, query)

private sealed interface Match {
    data object NotMatched : Match // No disclosures needed. Claim is non-selectively disclosable
    data class Matched(val disclosures: List<Disclosure>) : Match
}

private class Presenter<JWT>(
    private val claimsOf: (JWT) -> Claims,
    private val continueIfSomeNotMatch: Boolean = false,
    private val f: (JsonPath) -> ((SingleClaimJsonPath) -> Boolean) = { jsonPath ->
        {
                single ->
            single.asJsonPath() == jsonPath
        }
    },
) {

    fun present(sdJwt: SdJwt.Issuance<JWT>, query: Query): SdJwt.Presentation<JWT>? =
        when (val match = match(sdJwt, query)) {
            Match.NotMatched -> null
            is Match.Matched -> SdJwt.Presentation(sdJwt.jwt, match.disclosures)
        }

    fun match(sdJwt: SdJwt.Issuance<JWT>, query: Query): Match {
        val (unprotected, disclosuresPerClaim) = sdJwt.recreateClaimsAndDisclosuresPerClaim(claimsOf)
        fun unprotectedClaimAt(path: JsonPath): JsonElement? {
            TODO()
        }

        fun matchClaimInPath(q: Query.ClaimInPath): Match {
            val predicate = f(q.path)
            val ds = disclosuresPerClaim.filterKeys(predicate).values.flatten()
            return when {
                ds.isEmpty() -> Match.NotMatched
                else -> {
//                    val claimValue = checkNotNull(unprotectedClaimAt(q.path)) { "Missing value for ${q.path}" }
//                    if (q.filter(claimValue)) Match.Matched(ds)
//                    else Match.NotMatched

                    Match.Matched(ds)
                }
            }
        }

        fun matchMany(many: Query.Many): Match {
            val disclosuresToPresent = mutableSetOf<Disclosure>()
            var misses = false
            for (q in many.claimsInPath) {
                when (val m = matchClaimInPath(q)) {
                    Match.NotMatched -> misses = true
                    is Match.Matched -> {
                        disclosuresToPresent.addAll(m.disclosures)
                    }
                }
                if (misses && !continueIfSomeNotMatch) break
            }
            return if (misses && !continueIfSomeNotMatch) Match.NotMatched
            else Match.Matched(disclosuresToPresent.toList())
        }
        return when (query) {
            Query.AllClaims -> Match.Matched(sdJwt.disclosures)
            is Query.ClaimInPath -> matchClaimInPath(query)
            is Query.Many -> matchMany(query)
            Query.OnlyNonSelectivelyDisclosableClaims -> Match.Matched(emptyList())
        }
    }
}
