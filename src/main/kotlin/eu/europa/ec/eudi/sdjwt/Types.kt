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

import eu.europa.ec.eudi.sdjwt.HashAlgorithm.entries
import kotlinx.serialization.json.JsonElement
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A claim is an attribute of an entity.
 * The claim name, or key, as it would be used in a regular JWT body.
 * The claim value, as it would be used in a regular JWT body.
 * The value MAY be of any type allowed in
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
 *  The value MAY be of any type allowed in JSON
 * @return the value of the claim
 */
fun Claim.value(): JsonElement = second

/**
 * Salt to be included in a [Disclosure] claim.
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
        fun fromString(s: String): HashAlgorithm? = entries.find { it.alias == s }
    }
}

typealias Jwt = String

/**
 * A parameterized representation of the SD-JWT
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 */
data class SdJwt<out JWT>(
    /**
     * The JWT part of the SD-JWT
     */
    val jwt: JWT,

    /**
     * The disclosures of the SD-JWT
     */
    val disclosures: List<Disclosure>,

)

inline fun <JWT, JWT1> SdJwt<JWT>.map(f: (JWT) -> JWT1): SdJwt<JWT1> {
    contract {
        callsInPlace(f, InvocationKind.AT_MOST_ONCE)
    }
    return SdJwt<JWT1>(f(jwt), disclosures)
}

/**
 * A parameterized representation of a presented SD-JWT
 * with a [keyBindingJwt]
 *
 * @param sdJwt the SD-JWT
 * @param keyBindingJwt a KB-JWT, associated with the [sdJwt]
 * @param JWT  the type representing the JWT
 */
data class SdJwtAndKbJwt<out JWT>(val sdJwt: SdJwt<JWT>, val keyBindingJwt: JWT)

inline fun <JWT, JWT1> SdJwtAndKbJwt<JWT>.map(f: (JWT) -> JWT1): SdJwtAndKbJwt<JWT1> {
    contract {
        callsInPlace(f, InvocationKind.UNKNOWN)
    }
    return SdJwtAndKbJwt<JWT1>(sdJwt.map(f), keyBindingJwt.let(f))
}
