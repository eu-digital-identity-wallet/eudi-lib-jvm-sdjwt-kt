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
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.jwk.AsymmetricJWK as NimbusAsymmetricJWK
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

@Deprecated(
    message = "Deprecated",
    replaceWith = ReplaceWith("with(NimbusSdJwtOps) { asJwsJsonObject(option) }"),
    level = DeprecationLevel.WARNING,
)
fun SdJwt<NimbusSignedJWT>.serializeAsJwsJson(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject =
    with(NimbusSdJwtOps) { asJwsJsonObject(option) }

@Deprecated(
    message = "Deprecated and will be removed",
    replaceWith = ReplaceWith(" with(NimbusSdJwtOps) { serialize() }"),
    level = DeprecationLevel.WARNING,
)
fun SdJwt<NimbusSignedJWT>.serialize(): String =
    with(NimbusSdJwtOps) { serialize() }

/**
 * Representation of a function used to sign the Keybinding JWT of a Presentation SD-JWT.
 */
@Deprecated(
    message = "It will be removed from a future release",
    level = DeprecationLevel.WARNING,
)
interface KeyBindingSigner : NimbusJWSSigner {
    val signAlgorithm: NimbusJWSAlgorithm
    val publicKey: AsymmetricJWK
    override fun supportedJWSAlgorithms(): MutableSet<NimbusJWSAlgorithm> = mutableSetOf(signAlgorithm)
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
    replaceWith = ReplaceWith("with(SdJwtSerializationOps<JWT>(serializeJwt, { error(\"Not Used\") })) { serialize() }"),
    level = DeprecationLevel.WARNING,
)
fun <JWT> SdJwt<JWT>.serialize(
    serializeJwt: (JWT) -> String,
): String = with(SdJwtSerializationOps<JWT>(serializeJwt, { error("Not Used") })) { serialize() }

@Deprecated(
    message = "Use suspendable methods of SdJwtSerializationOps ",
    level = DeprecationLevel.WARNING,
)
fun <JWT> SdJwt<JWT>.asJwsJsonObject(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
    kbJwt: Jwt?,
    getParts: (JWT) -> Triple<String, String, String>,
): JsonObject =
    with(SdJwtSerializationOps<JWT>({ getParts(it).toList().joinToString(".") }, { _ -> error("Not used") })) {
        if (kbJwt == null) asJwsJsonObject(option)
        else {
            this@asJwsJsonObject.asJwsJsonObjectWithKeyBinding(option, kbJwt)
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
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = fun NimbusJWSHeader.Builder.() {
    },
): SdJwtIssuer<NimbusSignedJWT> =
    NimbusSdJwtOps.issuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)

/**
 * Recreates the claims, used to produce the SD-JWT and at the same time calculates [DisclosuresPerClaim]
 *
 * @param claimsOf a function to obtain the claims of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 *
 */
@Deprecated(
    message = "This method will be removed in a future version",
    replaceWith = ReplaceWith("with(SdJwtPresentationOps(claimsOf)) { recreateClaimsAndDisclosuresPerClaim() }"),
)
fun <JWT> SdJwt<JWT>.recreateClaimsAndDisclosuresPerClaim(claimsOf: (JWT) -> JsonObject): Pair<JsonObject, DisclosuresPerClaimPath> =
    with(SdJwtPresentationOps(claimsOf)) { recreateClaimsAndDisclosuresPerClaim() }

/**
 * Recreates the claims, used to produce the SD-JWT
 * That are:
 * - The plain claims found into the [SdJwt.jwt]
 * - Digests found in [SdJwt.jwt] and/or [Disclosure] (in case of recursive disclosure) will
 *   be replaced by [Disclosure.claim]
 *
 * @param claimsOf a function to get the claims of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to use
 * @return the claims that were used to produce the SD-JWT
 */
@Deprecated(
    message = "Replace with SdJwtSerializationOps",
    replaceWith = ReplaceWith("with(SdJwtRecreateClaimsOps(claimsOf)) { recreateClaims(visitor = null) }"),
)
fun <JWT> SdJwt<JWT>.recreateClaims(claimsOf: (JWT) -> JsonObject): JsonObject =
    with(SdJwtRecreateClaimsOps(claimsOf)) { recreateClaims(visitor = null) }

/**
 * Recreates the claims, used to produce the SD-JWT
 * That are:
 * - The plain claims found into the [SdJwt.jwt]
 * - Digests found in [SdJwt.jwt] and/or [Disclosure] (in case of recursive disclosure) will
 *   be replaced by [Disclosure.claim]
 *
 * @param visitor [ClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
 * @param claimsOf a function to get the claims of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to use
 * @return the claims that were used to produce the SD-JWT
 */
@Deprecated(
    message = "Replace with SdJwtSerializationOps",
    replaceWith = ReplaceWith(" with(SdJwtRecreateClaimsOps(claimsOf)) { recreateClaims(visitor) }"),
)
fun <JWT> SdJwt<JWT>.recreateClaims(visitor: ClaimVisitor? = null, claimsOf: (JWT) -> JsonObject): JsonObject =
    with(SdJwtRecreateClaimsOps(claimsOf)) { recreateClaims(visitor) }

/**
 * Factory method for creating a [KeyBindingVerifier] which applies the rules described in [keyBindingJWTProcess].
 * @param holderPubKeyExtractor a function that extracts the holder's public key from the payload of the SD-JWT.
 * If not provided, it is assumed that the SD-JWT issuer used the confirmation claim (see [cnf]) for this purpose.
 * @param challenge an optional challenge provided by the verifier, to be signed by the holder as the Key binding JWT.
 * If provided, Key Binding JWT payload should contain the challenge as is.
 *
 * @see keyBindingJWTProcess
 */
@Deprecated(
    message = "Replace with NimbusSdJwtOps instead",
    replaceWith = ReplaceWith("with(NimbusSdJwtOps) { KeyBindingVerifier.mustBePresentAndValid(holderPubKeyExtractor, challenge) }"),
)
fun KeyBindingVerifier.Companion.mustBePresentAndValid(
    holderPubKeyExtractor: (JsonObject) -> NimbusAsymmetricJWK? = NimbusSdJwtOps.HolderPubKeyInConfirmationClaim,
    challenge: JsonObject? = null,
): KeyBindingVerifier.MustBePresentAndValid<NimbusSignedJWT> =
    with(NimbusSdJwtOps) { KeyBindingVerifier.mustBePresentAndValid(holderPubKeyExtractor, challenge) }

@Deprecated(
    message = "This method will be removed in a future release.",
    replaceWith = ReplaceWith("SdJwt<JsonObject>"),
)
typealias UnsignedSdJwt = SdJwt<JsonObject>
