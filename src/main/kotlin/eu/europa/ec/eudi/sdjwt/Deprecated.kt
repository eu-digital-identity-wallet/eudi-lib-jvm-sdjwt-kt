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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
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

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT.
 *
 * @param jwtSerializer function used to serialize the [Presentation JWT][SdJwt.Presentation.jwt]
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
@Deprecated(message = "Will be removed", level = DeprecationLevel.ERROR)
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBinding(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
): String = runBlocking {
    with(SdJwtSerializationOps(jwtSerializer)) {
        val kbJwtBuilder = KbJwtBuilder(keyBindingSigner, claimSetBuilderAction)
        serializeWithKeyBinding(hashAlgorithm, kbJwtBuilder, JsonObject(emptyMap()))
    }.getOrThrow()
}

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT in JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 * the result JSON contains an unprotected header which includes
 * an array with the disclosures of the [SdJwt] and the key binding JWT
 *
 * @param jwtSerializer function used to serialize the [Presentation JWT][SdJwt.Presentation.jwt]
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param option
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
@Deprecated(message = "Will be removed", level = DeprecationLevel.ERROR)
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBindingAsJwsJson(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject =
    runBlocking {
        with(SdJwtSerializationOps<JWT>(jwtSerializer)) {
            val kbJwtBuilder = KbJwtBuilder(keyBindingSigner, claimSetBuilderAction)
            asJwsJsonObjectWithKeyBinding(option, hashAlgorithm, kbJwtBuilder, JsonObject(emptyMap()))
        }.getOrThrow()
    }

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT  in JWS JSON
 *
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
@Deprecated(message = "Will be removed", level = DeprecationLevel.ERROR)
fun SdJwt.Presentation<NimbusSignedJWT>.serializeWithKeyBindingAsJwsJson(
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
): JsonObject =
    runBlocking {
        with(NimbusSdJwtOps) {
            asJwsJsonObjectWithKeyBinding(
                option = JwsSerializationOption.Flattened,
                hashAlgorithm,
                keyBindingSigner,
                claimSetBuilderAction,
            )
        }.getOrThrow()
    }

/**
 * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 *  the result JSON contains an unprotected header which includes
 *  an array with the disclosures of the [SdJwt]
 *
 * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
 * representation as defined in RFC7515
 * @receiver the [SdJwt] to serialize
 *
 * @return a JSON object either general or flattened according to RFC7515 having an additional
 * disclosures array as per SD-JWT extension
 */
@Deprecated(
    message = "Deprecated",
    replaceWith = ReplaceWith("with(NimbusSdJwtSerializationOps) { asJwsJsonObject(option = option, kbJwt = null) }"),
)
fun SdJwt<NimbusSignedJWT>.serializeAsJwsJson(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject =
    with(NimbusSdJwtOps) {
        when(this@serializeAsJwsJson) {
            is SdJwt.Issuance<NimbusSignedJWT> -> asJwsJsonObject(option)
            is SdJwt.Presentation<NimbusSignedJWT> -> asJwsJsonObject(option, kbJwt = null)
        }

    }

/**
 * Serializes a [SdJwt] without a key binding part.
 *
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT
 */
@Deprecated(
    message = "Deprecated and will be removed",
    replaceWith = ReplaceWith(" with(NimbusSdJwtSerializationOps){serialize()}"),
)
fun SdJwt<NimbusSignedJWT>.serialize(): String =
    with(NimbusSdJwtOps) { serialize() }

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT.
 *
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
@Deprecated(
    message = "Use the suspended method of NimbusSdJwtSerializationOps",
    level = DeprecationLevel.ERROR,
)
fun SdJwt.Presentation<NimbusSignedJWT>.serializeWithKeyBinding(
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
): String =
    runBlocking {
        with(NimbusSdJwtOps) {
            serializeWithKeyBinding(
                hashAlgorithm,
                keyBindingSigner,
                claimSetBuilderAction,
            )
        }.getOrThrow()
    }

/**
 * Serializes an [SdJwt] in combined format without key binding
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to serialize
 * @return the serialized format of the SD-JWT
 */
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith("with(SdJwtSerializationOps<JWT>(serializeJwt)) { serialize() }"),
)
fun <JWT> SdJwt<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String = with(SdJwtSerializationOps<JWT>(serializeJwt)) { serialize() }

/**
 * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 *  the result JSON contains an unprotected header which includes
 *  an array with the disclosures of the [SdJwt] and optionally the key binding JWT
 *
 * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
 *   representation as defined in RFC7515
 * @param kbJwt the key binding JWT for the SD-JWT.
 * @param getParts a function to extract out of the [jwt][SdJwt.jwt]  of the SD-JWT
 * the three JWS parts: protected header, payload and signature.
 * Each part is base64 encoded
 * @receiver the [SdJwt] to serialize
 *
 * @return a JSON object either general or flattened according to RFC7515 having an additional
 * disclosures array and possibly the KB-JWT in an unprotected header as per SD-JWT extension
 */
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith(
        "with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(\".\")})) { asJwsJsonObject(option, kbJwt) }",
    ),
)
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    kbJwt: Jwt?,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject =
    with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(".") })) {
        when(this@asJwsJsonObject) {
            is SdJwt.Issuance<JWT> -> asJwsJsonObject(option)
            is SdJwt.Presentation<JWT> ->asJwsJsonObject(option, kbJwt)
        }
    }

/**
 * Factory method for creating a [SdJwtIssuer] that uses Nimbus
 *
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
 *
 * @return [SdJwtIssuer] that uses Nimbus
 *
 * @see SdJwtFactory.Default
 */
@Deprecated(
    message = "Deprecated. Use NimbusSdJwtOps",
    replaceWith = ReplaceWith(" NimbusSdJwtOps.issuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)"),
)
fun SdJwtIssuer.Companion.nimbus(
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
): SdJwtIssuer<NimbusSignedJWT> =
    NimbusSdJwtOps.issuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)
