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
import java.security.MessageDigest

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
 * Representations of multiple claims
 *
 */
typealias Claims = Map<String, JsonElement>

/**
 * Salt to be included in a [Disclosure] claim.
 * Check [SD-JWT][https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-05.html#name-creating-disclosures]
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

typealias UnsignedSdJwt = SdJwt.Issuance<JsonObject>

/**
 * A parameterized representation of the SD-JWT
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param KB_JWT the type representing the Holder Binding part of the SD
 */
sealed interface SdJwt<out JWT, out KB_JWT> {

    /**
     * The JWT part of the SD-JWT
     */
    val jwt: JWT

    /**
     * The disclosures of the SD-JWT
     */
    val disclosures: Set<Disclosure>

    /**
     * The SD-JWT as it is produced by the issuer and handed-over to the holder
     * @param jwt The JWT part of the SD-JWT
     * @param disclosures the full set of disclosures
     */
    data class Issuance<JWT>(
        override val jwt: JWT,
        override val disclosures: Set<Disclosure>,
    ) : SdJwt<JWT, Nothing>

    /**
     * The SD-JWT as it is produced by the holder and presented to the verifier
     * @param jwt the JWT part of the SD-JWT
     * @param disclosures the disclosures that holder decided to disclose to the verifier
     * @param keyBindingJwt optional Key Binding JWT
     */
    data class Presentation<JWT, KB_JWT>(
        override val jwt: JWT,
        override val disclosures: Set<Disclosure>,
        val keyBindingJwt: KB_JWT?,
    ) : SdJwt<JWT, KB_JWT>
}

/**
 * Drops the [key binding JWT][SdJwt.Presentation.keyBindingJwt]
 *
 * @receiver the presentation SD-JWT
 * @return a presentation SD-JWT that keeps the [JWT][SdJwt.Presentation.jwt] and the
 * [disclosures][SdJwt.Presentation.disclosures] of the original SD-JWT, without
 * keybinding JWT
 */
fun <JWT> SdJwt.Presentation<JWT, *>.noKeyBinding(): SdJwt.Presentation<JWT, Nothing> =
    SdJwt.Presentation(jwt, disclosures, null)

/**
 * Generates the hash digest of this [SdJwt.Presentation].
 *
 * @receiver the SD-JWT to hash
 * @param hashAlgorithm] the [HashAlgorithm] to use for generating the hash
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param serializeJwt a function to serialize the [JWT]
 * @return the base64url-encoded hash digest
 */
fun <JWT> SdJwt.Presentation<JWT, Nothing>.sdHash(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    serializeJwt: (JWT) -> String,
): Result<String> = runCatching {
    val digest = MessageDigest.getInstance(hashAlgorithm.alias.uppercase())
    JwtBase64.encode(digest.digest(serialize(serializeJwt).encodeToByteArray()))
}

/**
 * Adds a [key binding JWT][SdJwt.Presentation.keyBindingJwt]
 *
 * @receiver the presentation SD-JWT that contains no [key binding JWT][SdJwt.Presentation.keyBindingJwt]
 * @return a presentation SD-JWT that keeps the [JWT][SdJwt.Presentation.jwt] and the
 * [disclosures][SdJwt.Presentation.disclosures] of the original SD-JWT, and the provided
 * [key binding JWT][keyBindingJwt]
 */
fun <JWT, KB_JWT> SdJwt.Presentation<JWT, Nothing>.withKeyBinding(keyBindingJwt: KB_JWT): SdJwt.Presentation<JWT, KB_JWT> =
    SdJwt.Presentation(jwt, disclosures, keyBindingJwt)
