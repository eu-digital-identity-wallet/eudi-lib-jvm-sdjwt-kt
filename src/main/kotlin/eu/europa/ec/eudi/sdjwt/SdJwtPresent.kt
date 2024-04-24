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

typealias JsonPath = String
typealias DisclosuresPerClaim = Map<JsonPath, List<Disclosure>>

/**
 * Gets the full [JsonPath] of each selectively disclosed claim alongside the [Disclosures][Disclosure] that are required
 * to disclose it.
 */
fun <JWT> SdJwt.Issuance<JWT>.disclosuresPerClaim(claimsOf: (JWT) -> Claims): DisclosuresPerClaim {
    val disclosures = mutableMapOf<JsonPath, List<Disclosure>>()
    val visitor = SdClaimVisitor { parent, current, disclosure ->
        require(current !in disclosures.keys) { "Disclosures for claim $current have already been calculated." }
        disclosures[current] = (disclosures[parent] ?: emptyList()) + disclosure
    }
    recreateClaims(visitor, claimsOf)
    return disclosures
}

fun UnsignedSdJwt.disclosuresPerClaim(): DisclosuresPerClaim = disclosuresPerClaim { it }

sealed interface Query {
    data object OnlyNonSelectivelyDisclosableClaims : Query
    data class ClaimInPath(val path: JsonPath, val filter: (Claim) -> Boolean = { true }) : Query
    data class Many(val claimsInPath: List<ClaimInPath>) : Query
    data object AllClaims : Query
}
fun <JWT>SdJwt.Issuance<JWT>.present(query: Query, claimsOf: (JWT) -> Claims): SdJwt.Presentation<JWT>? =
    Presenter(claimsOf, false).present(this, query)

private sealed interface Match {
    data object ByPlainClaims : Match // No disclosures needed. Claim is non-selectively disclosable
    data class BySdClaims(val disclosures: List<Disclosure>) : Match {
        init {
            require(disclosures.isNotEmpty())
        }
    }
}

private class Presenter<JWT>(
    private val claimsOf: (JWT) -> Claims,
    private val continueIfSomeNotMatch: Boolean = false,
) {

    fun present(sdJwt: SdJwt.Issuance<JWT>, query: Query): SdJwt.Presentation<JWT>? =
        when (val match = match(sdJwt, query)) {
            null -> null
            Match.ByPlainClaims -> SdJwt.Presentation(sdJwt.jwt, emptyList())
            is Match.BySdClaims -> {
                match.disclosures.forEach { disclosure ->
                    require(disclosure in sdJwt.disclosures) { "Unknown disclosure: $disclosure" }
                }
                SdJwt.Presentation(sdJwt.jwt, match.disclosures)
            }
        }

    fun match(sdJwt: SdJwt.Issuance<JWT>, query: Query): Match? {
        val disclosuresPerClaim by lazy { sdJwt.disclosuresPerClaim(claimsOf) }
        fun matchClaimInPath(q: Query.ClaimInPath): Match? =
            when (val ds = disclosuresPerClaim[q.path]) {
                null -> null
                else -> if (ds.isEmpty()) Match.ByPlainClaims else Match.BySdClaims(ds)
            }

        return when (query) {
            Query.AllClaims -> Match.BySdClaims(sdJwt.disclosures)
            is Query.ClaimInPath -> matchClaimInPath(query)
            is Query.Many -> {
                val disclosuresToPresent = mutableSetOf<Disclosure>()
                var misses = false
                for (q in query.claimsInPath) {
                    when (val m = matchClaimInPath(q)) {
                        null -> misses = true
                        Match.ByPlainClaims -> {}
                        is Match.BySdClaims -> { disclosuresToPresent.addAll(m.disclosures) }
                    }
                    if (misses && !continueIfSomeNotMatch) break
                }
                if (misses && !continueIfSomeNotMatch) null
                else {
                    if (disclosuresToPresent.isEmpty()) Match.ByPlainClaims
                    else Match.BySdClaims(disclosuresToPresent.toList())
                }
            }
            Query.OnlyNonSelectivelyDisclosableClaims -> Match.ByPlainClaims
        }
    }
}
