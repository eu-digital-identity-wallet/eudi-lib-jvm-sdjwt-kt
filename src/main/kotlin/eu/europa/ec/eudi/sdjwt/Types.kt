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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * A claim is an attribute of an entity.
 * The claim name, or key, as it would be used in a regular JWT body.
 * The claim value, as it would be used in a regular JWT body.
 * The value MAY be of any type that is allowed in
 * JSON, including numbers, strings, booleans, arrays, and objects
 *
 */
typealias Claim = Pair<String, JsonElement>

/**
 * The claim name, or key, as it would be used in a regular JWT body
 * @return the name of the claim
 */
fun Claim.name(): String = first

/**
 * The claim value, as it would be used in a regular JWT body.
 *  The value MAY be of any type that is allowed in JSON
 * @return the value of the claim
 */
fun Claim.value(): JsonElement = second

/**
 * Representations of multiple claims
 *
 */
typealias Claims = Map<String, JsonElement>

/**
 * Salt to be included in a [Disclosure] claim.
 * Check [SD-JWT][https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt-04#section-5.1.1.1]
 */
typealias Salt = String

/**
 * Hashing algorithms, used to produce the [DisclosureDigest] of a [Disclosure]
 */
enum class HashAlgorithm(val alias: String) {
    SHA_256("sha-256"),
    SHA_384("sha-384"),
    SHA_512("sha-512"),
    SHA3_256("sha3-256"),
    SHA3_384("sha3-384"),
    SHA3_512("sha3-512"),
    ;

    companion object {

        /**
         * Gets the [HashAlgorithm] by its alias
         * @param s a string with the alias of the algorithm
         * @return either a matching [HashAlgorithm] or null
         */
        fun fromString(s: String): HashAlgorithm? = values().find { it.alias == s }
    }
}

/**
 * A domain specific language for describing the payload of an SD-JWT
 */
sealed interface SdJwtElement {
    data class Plain(val claims: Claims) : SdJwtElement
    data class FlatDisclosed(val claims: Claims) : SdJwtElement
    data class StructuredDisclosed(val claimName: String, val elements: List<SdJwtElement>) : SdJwtElement
    data class RecursivelyDisclosed(val claimName: String, val claims: Claims) : SdJwtElement
}

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 *
 * @param disclosures the disclosures calculated
 * @param claimSet the JSON object that contains the hashed disclosures and possible plain claims
 */
data class DisclosedClaims(val disclosures: Set<Disclosure>, val claimSet: JsonObject) {

    constructor(disclosures: Set<Disclosure>, buildAction: JsonObjectBuilder.() -> Unit) : this(
        disclosures,
        buildJsonObject(buildAction),
    )

    fun mapClaims(f: (JsonObject) -> JsonObject): DisclosedClaims = DisclosedClaims(disclosures, f(claimSet))

    companion object {

        /**
         * A []DisclosedClaims] with no disclosures adn with an empty claim set
         */
        val Empty: DisclosedClaims = DisclosedClaims(emptySet(), JsonObject(emptyMap()))

        /**
         * Adds two [DisclosedClaims] producing a new [DisclosedClaims] which contains
         * the combined set of disclosures and claim sets
         * @param a the first  [DisclosedClaims]
         * @param b the second  [DisclosedClaims]
         * @return a new [DisclosedClaims] which contains the combined set of disclosures and claim sets
         */
        fun add(a: DisclosedClaims, b: DisclosedClaims): DisclosedClaims {
            val disclosures = a.disclosures + b.disclosures
            val claimSet = JsonObject(a.claimSet + b.claimSet)
            return DisclosedClaims(disclosures, claimSet)
        }
    }
}

operator fun DisclosedClaims.plus(that: DisclosedClaims): DisclosedClaims = DisclosedClaims.add(this, that)

/**
 * Combined form for issuance
 * It is a string containing a standard JWT token followed by a set of [Disclosure]s divided by character `~`
 */
typealias CombinedIssuanceSdJwt = String
typealias Jwt = String

sealed interface SdJwt<JWT, HB_JWT> {

    val jwt: JWT
    val disclosures: Set<Disclosure>

    data class Issuance<JWT>(
        override val jwt: JWT,
        override val disclosures: Set<Disclosure>,
    ) : SdJwt<JWT, Nothing>
    data class Presentation<JWT, HB_JWT>(
        override val jwt: JWT,
        override val disclosures: Set<Disclosure>,
        val holderBindingJwt: HB_JWT?,
    ) : SdJwt<JWT, HB_JWT>
}

fun <JWT> SdJwt.Issuance<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): CombinedIssuanceSdJwt =
    "${serializeJwt(jwt)}${disclosures.concat()}}"

fun <JWT, HB_JWT> SdJwt.Presentation<JWT, HB_JWT>.serialize(
    serializeJwt: (JWT) -> String,
    serializeHolderBindingJwt: (HB_JWT) -> String,
): String =
    "${serializeJwt(jwt)}${disclosures.concat()}~${holderBindingJwt?.run(serializeHolderBindingJwt) ?: ""}"
