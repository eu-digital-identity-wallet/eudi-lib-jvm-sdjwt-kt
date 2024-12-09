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

import com.nimbusds.jose.jwk.AsymmetricJWK
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
 * An adapter that transforms the [payload][NimbusJWTClaimsSet] of a [Nimbus JWT]
 * to a KotlinX Serialization compatible representation
 */
@Deprecated(
    message = "Deprecated and will be removed in a future release",
    replaceWith = ReplaceWith("jsonObject()"),
)
fun NimbusJWTClaimsSet.asClaims(): JsonObject = jsonObject()

@Deprecated(
    message = "Use suspendable methods of SdJwtSerializationOps",
    level = DeprecationLevel.ERROR,
)
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBinding(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
): String = runBlocking {
    with(SdJwtSerializationOps(jwtSerializer)) {
        val kbJwtBuilder = NimbusSdJwtOps.kbJwtIssuer(
            keyBindingSigner.signAlgorithm,
            keyBindingSigner,
            keyBindingSigner.publicKey,
            claimSetBuilderAction,
        )
        serializeWithKeyBinding(hashAlgorithm, kbJwtBuilder)
    }.getOrThrow()
}

@Deprecated(
    message = "Use suspendable methods of SdJwtSerializationOps",
    level = DeprecationLevel.ERROR,
)
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBindingAsJwsJson(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject =
    runBlocking {
        with(SdJwtSerializationOps<JWT>(jwtSerializer)) {
            val kbJwtBuilder = NimbusSdJwtOps.kbJwtIssuer(
                keyBindingSigner.signAlgorithm,
                keyBindingSigner,
                keyBindingSigner.publicKey,
                claimSetBuilderAction,
            )
            asJwsJsonObjectWithKeyBinding(option, hashAlgorithm, kbJwtBuilder)
        }.getOrThrow()
    }

@Deprecated(
    message = "Use suspendable methods of SdJwtSerializationOps",
    level = DeprecationLevel.ERROR,
)
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
                kbJwtIssuer(keyBindingSigner.signAlgorithm, keyBindingSigner, keyBindingSigner.publicKey, claimSetBuilderAction),
            )
        }.getOrThrow()
    }

@Deprecated(
    message = "Deprecated",
    replaceWith = ReplaceWith("with(NimbusSdJwtOps) { asJwsJsonObject(option) }"),
)
fun SdJwt<NimbusSignedJWT>.serializeAsJwsJson(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject =
    with(NimbusSdJwtOps) { asJwsJsonObject(option) }

@Deprecated(
    message = "Deprecated and will be removed",
    replaceWith = ReplaceWith(" with(NimbusSdJwtOps) { serialize() }"),
)
fun SdJwt<NimbusSignedJWT>.serialize(): String =
    with(NimbusSdJwtOps) { serialize() }

/**
 * Representation of a function used to sign the Keybinding JWT of a Presentation SD-JWT.
 */
@Deprecated(
    message = "It will be removed from a future release",
)
interface KeyBindingSigner : NimbusJWSSigner {
    val signAlgorithm: NimbusJWSAlgorithm
    val publicKey: AsymmetricJWK
    override fun supportedJWSAlgorithms(): MutableSet<NimbusJWSAlgorithm> = mutableSetOf(signAlgorithm)
}

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
                kbJwtIssuer(keyBindingSigner.signAlgorithm, keyBindingSigner, keyBindingSigner.publicKey, claimSetBuilderAction),
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
    level = DeprecationLevel.WARNING,
)
fun <JWT> SdJwt<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String = with(SdJwtSerializationOps<JWT>(serializeJwt)) { serialize() }

@Deprecated(
    message = "Use suspendable methods of SdJwtSerializationOps ",
    level = DeprecationLevel.ERROR,
)
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    kbJwt: Jwt?,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject =
    runBlocking {
        with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(".") })) {
            if (kbJwt == null) asJwsJsonObject(option)
            else {
                require(this@asJwsJsonObject is SdJwt.Presentation<JWT>)
                val buildKbJwt = BuildKbJwt { _ -> Result.success(kbJwt) }
                val hashAlgorithm = HashAlgorithm.SHA_256 // it will be ignored
                this@asJwsJsonObject.asJwsJsonObjectWithKeyBinding(option, hashAlgorithm, buildKbJwt).getOrThrow()
            }
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

/**
 * A variation of [sdJwt] which produces signed SD-JWT
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 * @param digestNumberHint This is an optional hint; that expresses the number of digests on the immediate level
 * of this SD-JWT, that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of [DisclosureDigest] is less than the [hint][digestNumberHint]
 *
 * @return signed SD-JWT
 *
 * @see SdJwtIssuer.Companion.nimbus which in addition allows customization of JWS Header
 */
@Deprecated(message = "Use NimbusSdJwtOps instead")
suspend inline fun signedSdJwt(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    digestNumberHint: Int? = null,
    builderAction: SdObjectBuilder.() -> Unit,
): SdJwt.Issuance<NimbusSignedJWT> {
    val issuer = NimbusSdJwtOps.issuer(sdJwtFactory, signer, signAlgorithm)
    val sdJwtElements = sdJwt(digestNumberHint, builderAction)
    return issuer.issue(sdJwtElements).getOrThrow()
}
