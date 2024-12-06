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

import eu.europa.ec.eudi.sdjwt.vc.toJsonPointer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

/**
 * Tries to create a presentation that discloses the claims that satisfy
 * [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching(query) { (_, claims) -> claims }"),
    level = DeprecationLevel.WARNING,
)
@JvmName("presentMatching")
fun SdJwt.Issuance<JwtAndClaims>.present(
    query: (JsonPointer) -> Boolean,
): SdJwt.Presentation<JwtAndClaims>? = presentJsonPointersMatching(query) { (_, claims) -> claims }

/**
 *  Tries to create a presentation that discloses the claims are in [query]
 *  @param query a set of [JsonPointer] relative to the unprotected JSON (not the JWT payload). Pointers for
 *  * claims that are always disclosable can be omitted
 *  @receiver The issuance SD-JWT upon which the presentation will be based
 *  @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching({ pointer: JsonPointer-> pointer in query }) { (_, claims) -> claims }"),
    level = DeprecationLevel.WARNING,
)
@JvmName("presentMatching")
fun SdJwt.Issuance<JwtAndClaims>.present(
    query: Set<JsonPointer>,
): SdJwt.Presentation<JwtAndClaims>? =
    presentJsonPointersMatching({ pointer: JsonPointer -> pointer in query }) { (_, claims) -> claims }

/**
 * Tries to create a presentation that discloses the claims are in [query]
 * @param query a set of [JsonPointer] relative to the unprotected JSON (not the JWT payload). Pointers for
 * claims that are always disclosable can be omitted
 * @param claimsOf a function to obtain the claims of the [SdJwt.jwt]
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @param JWT the type representing the JWT part of the SD-JWT
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching({ pointer: JsonPointer-> pointer in query }, claimsOf)"),
    level = DeprecationLevel.WARNING,
)
@JvmName("presentMatchingSetOfPointers")
fun <JWT> SdJwt.Issuance<JWT>.present(
    query: Set<JsonPointer>,
    claimsOf: (JWT) -> JsonObject,
): SdJwt.Presentation<JWT>? =
    presentJsonPointersMatching({ pointer: JsonPointer -> pointer in query }, claimsOf)

/**
 * Tries to create a presentation that discloses the claims that satisfy
 * [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @param claimsOf a function to obtain the claims of the [SdJwt.jwt]
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @param JWT the type representing the JWT part of the SD-JWT
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching(query, claimsOf)"),
    level = DeprecationLevel.WARNING,
)
@JvmName("presentMatching")
fun <JWT> SdJwt.Issuance<JWT>.present(
    query: (JsonPointer) -> Boolean,
    claimsOf: (JWT) -> JsonObject,
): SdJwt.Presentation<JWT>? =
    presentJsonPointersMatching(query, claimsOf)

/**
 * Tries to create a presentation that discloses the claims are in [query]
 * @param query a set of [JsonPointer] relative to the unprotected JSON (not the JWT payload). Pointers for
 * claims that are always disclosable can be omitted
 * @receiver The issuance SD-JWT upon which the presentation will be based
 *
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching { it in query }"),
    level = DeprecationLevel.WARNING,
)
@JvmName("presentMatchingSetOfPointers")
fun SdJwt.Issuance<NimbusSignedJWT>.present(query: Set<JsonPointer>): SdJwt.Presentation<NimbusSignedJWT>? =
    presentJsonPointersMatching { it in query }

/**
 *  Tries to create a presentation that discloses the claims that satisfy
 *  [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release",
    replaceWith = ReplaceWith("presentJsonPointersMatching(query)"),
    level = DeprecationLevel.WARNING,
)
@JvmName("nimbusPresentMatching")
fun SdJwt.Issuance<NimbusSignedJWT>.present(query: (JsonPointer) -> Boolean): SdJwt.Presentation<NimbusSignedJWT>? =
    presentJsonPointersMatching(query)

/**
 *  Tries to create a presentation that discloses the claims that satisfy
 *  [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release. Use ClaimPath.",
    level = DeprecationLevel.WARNING,
)
fun SdJwt.Issuance<NimbusSignedJWT>.presentJsonPointersMatching(query: (JsonPointer) -> Boolean): SdJwt.Presentation<NimbusSignedJWT>? =
    presentJsonPointersMatching(query) { it.jwtClaimsSet.jsonObject() }

/**
 * Tries to create a presentation that discloses the claims that satisfy
 * [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @param claimsOf a function to obtain the claims of the [SdJwt.jwt]
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @param JWT the type representing the JWT part of the SD-JWT
 * @return the presentation if possible to satisfy the [query]
 */
@Deprecated(
    message = "It will be removed from a future release. Use ClaimPath",
    level = DeprecationLevel.WARNING,
)
fun <JWT> SdJwt.Issuance<JWT>.presentJsonPointersMatching(
    query: (JsonPointer) -> Boolean,
    claimsOf: (JWT) -> JsonObject,
): SdJwt.Presentation<JWT>? {
    val disclosuresPerClaim = recreateClaimsAndDisclosuresPerClaim(claimsOf)
        .second
        .mapKeys { it.key.toJsonPointer().getOrThrow() }

    val keys = disclosuresPerClaim.keys.filter(query)
    return if (keys.isEmpty()) null
    else {
        val ds = disclosuresPerClaim.filterKeys { it in keys }.values.flatten().toSet()
        SdJwt.Presentation(jwt, ds.toList())
    }
}

/**
 * Represents a map which contains all the claims - selectively disclosable or not -
 * found in a SD-JWT.
 * Each entry contains the [pointer][JsonPointer] and the [disclosures][Disclosure]
 * required to revel the claim
 */
@Deprecated(
    message = "It will be removed from a future release. Use DisclosuresPerClaimPath.",
    level = DeprecationLevel.WARNING,
)
typealias DisclosuresPerClaim = Map<JsonPointer, List<Disclosure>>

/**
 * Representations of multiple claims
 *
 */
@Deprecated(
    message = "Deprecated and will be removed in a future version",
    replaceWith = ReplaceWith("JsonObject"),
    level = DeprecationLevel.WARNING,
)
typealias Claims = Map<String, JsonElement>

/**
 * An adapter that transforms the [payload][NimbusJWTClaimsSet] of a [Nimbus JWT][NimbusJWT]
 * to a KotlinX Serialization compatible representation
 */
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith("jsonObject()"),
)
fun NimbusJWTClaimsSet.asClaims(): JsonObject = jsonObject()
