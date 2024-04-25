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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.fasterxml.jackson.databind.JsonNode as JacksonJsonNode
import com.fasterxml.jackson.databind.ObjectMapper as JacksonObjectMapper
import com.nfeld.jsonpathkt.JsonPath as ExternalJsonPath

typealias JsonPath = String

typealias DisclosuresPerClaim = Map<SingleClaimJsonPath, List<Disclosure>>

/**
 * Gets each claim alongside the [Disclosures][Disclosure] that are required to disclose it.
 */
fun <JWT> SdJwt<JWT>.disclosuresPerClaim(claimsOf: (JWT) -> Claims): DisclosuresPerClaim =
    recreateClaimsAndDisclosuresPerClaim(claimsOf).second

fun UnsignedSdJwt.disclosuresPerClaim(): DisclosuresPerClaim = disclosuresPerClaim { it }

fun <JWT> SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> Claims): Pair<Claims, DisclosuresPerClaim> {
    val disclosuresPerClaim = mutableMapOf<SingleClaimJsonPath, List<Disclosure>>()
    val visitor = SdClaimVisitor { path, disclosure ->
        if (disclosure != null) {
            require(path !in disclosuresPerClaim.keys) { "Disclosures for claim $path have already been calculated." }
        }
        disclosuresPerClaim.putIfAbsent(
            path,
            disclosuresPerClaim[path.partOf()].orEmpty() + disclosure?.let { listOf(it) }.orEmpty(),
        )
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
    private val filter: (JsonPath) -> ((SingleClaimJsonPath) -> Boolean) = { jsonPath ->
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
        val unprotectedJson: String by lazy { Json.encodeToString(JsonObject(unprotected)) }

        fun unprotectedClaimAt(path: SingleClaimJsonPath): JsonElement? =
            JsonPathOps.getJsonAtPath(path.asJsonPath(), unprotectedJson)
                ?.let { Json.decodeFromString<JsonElement>(it) }

        fun matchClaimInPath(q: Query.ClaimInPath): Match {
            val predicate = filter(q.path)
            val keys = disclosuresPerClaim.keys.filter(predicate)
            return if (keys.isEmpty()) Match.NotMatched
            else {
                val matches = disclosuresPerClaim.filterKeys { it in keys }
                    .filterKeys {
                        val value =
                            checkNotNull(unprotectedClaimAt(it)) { "Couldn't find value of claim '${it.asJsonPath()}'" }
                        q.filter(value)
                    }
                return if (matches.isEmpty()) Match.NotMatched
                else Match.Matched(matches.values.flatten())
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

/**
 * JSON Path related operations
 */
internal object JsonPathOps {

    /**
     * Checks that the provided [path][String] is JSON Path
     */
    internal fun isValid(path: String): Boolean = path.toJsonPath().isSuccess

    /**
     * Extracts from given [JSON][jsonString] the content
     * at [path][jsonPath]. Returns the value found at the path, if found
     */
    internal fun getJsonAtPath(jsonPath: JsonPath, jsonString: String): String? =
        ExternalJsonPath(jsonPath)
            .readFromJson<JacksonJsonNode>(jsonString)
            ?.toJsonString()

    private fun String.toJsonPath(): Result<ExternalJsonPath> = runCatching {
        ExternalJsonPath(this)
    }

    private fun JacksonJsonNode.toJsonString(): String = objectMapper.writeValueAsString(this)

    /**
     * Jackson JSON support
     */
    private val objectMapper: JacksonObjectMapper by lazy { JacksonObjectMapper() }
}
