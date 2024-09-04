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

import com.nimbusds.jose.*
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.jwk.AsymmetricJWK
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.serialization.json.*
import java.security.PublicKey
import java.text.ParseException
import com.nimbusds.jose.JOSEException as NimbusJOSEException
import com.nimbusds.jose.JOSEObjectType as NimbusJOSEObjectType
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory as NimbusDefaultJWSSignerFactory
import com.nimbusds.jose.jwk.AsymmetricJWK as NimbusAsymmetricJWK
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier as NimbusDefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector as NimbusJWSKeySelector
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector as NimbusSingleKeyJWSKeySelector
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier as NimbusDefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor as NimbusDefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor as NimbusJWTProcessor

//
// Signature Verification support
//

/**
 * A [JwtSignatureVerifier] is added to the companion object, which just checks/parses the JWT,
 * without performing signature validation.
 *
 * <em>Should not be used in production use cases</em>
 */
val JwtSignatureVerifier.Companion.NoSignatureValidation: JwtSignatureVerifier by lazy {
    JwtSignatureVerifier { unverifiedJwt ->
        try {
            val parsedJwt = NimbusSignedJWT.parse(unverifiedJwt)
            parsedJwt.jwtClaimsSet.asClaims()
        } catch (e: ParseException) {
            null
        }
    }
}

/**
 * Declares a [KeyBindingVerifier] that just makes sure that the Key Binding JWT is present, and it's indeed a JWT
 * without performing signature validation
 *
 * <em>Should not be used in production</em>
 */
val KeyBindingVerifier.Companion.MustBePresent: KeyBindingVerifier.MustBePresentAndValid by lazy {
    KeyBindingVerifier.MustBePresentAndValid { JwtSignatureVerifier.NoSignatureValidation }
}

/**
 * Factory method for creating a [KeyBindingVerifier] which applies the rules described in [keyBindingJWTProcess].
 * @param holderPubKeyExtractor a function that extracts the holder's public key from the payload of the SD-JWT.
 * If not provided, it is assumed that the SD-JWT issuer used the confirmation claim (see [cnf]) for this purpose.
 * @param challenge an optional challenge provided by the verifier, to be signed by the holder as the Key binding JWT.
 * If provided, Key Binding JWT payload should contain the challenge as is.
 *
 * @see keyBindingJWTProcess
 */
fun KeyBindingVerifier.Companion.mustBePresentAndValid(
    holderPubKeyExtractor: (Claims) -> NimbusAsymmetricJWK? = HolderPubKeyInConfirmationClaim,
    challenge: Claims? = null,
): KeyBindingVerifier.MustBePresentAndValid {
    val keyBindingVerifierProvider: (Claims) -> JwtSignatureVerifier = { sdJwtClaims ->
        holderPubKeyExtractor(sdJwtClaims)?.let { holderPubKey ->
            val key = holderPubKey.toPublicKey()
            val challengeClaimSet: NimbusJWTClaimsSet = NimbusJWTClaimsSet.parse(challenge.toString())
            keyBindingJWTProcess(key, challengeClaimSet).asJwtVerifier()
        } ?: throw KeyBindingError.MissingHolderPubKey.asException()
    }
    return KeyBindingVerifier.MustBePresentAndValid(keyBindingVerifierProvider)
}

/**
 * Creates a [NimbusJWTProcessor] suitable for verifying the Key Binding JWT
 * Enforces the following rules:
 * - The header contains typ claim equal to `kb+jwt`
 * - The header contains the signing algorithm claim
 * - The JWT must be signed by the given [holderPubKey]
 * - If the challenge is provided it should present in JWT payload
 * - Claims `aud`, `iat` and `nonce`must be present to the JWT payload
 *
 * @param holderPubKey the public key of the holder
 * @param challenge an optional challenge provided by the verifier, to be signed by the holder as the Key binding JWT.
 * If provided, Key Binding JWT payload should contain the challenge as is.
 * @return
 */
fun keyBindingJWTProcess(
    holderPubKey: PublicKey,
    challenge: NimbusJWTClaimsSet? = null,
): NimbusJWTProcessor<SecurityContext> =
    NimbusDefaultJWTProcessor<SecurityContext>().apply {
        jwsTypeVerifier = NimbusDefaultJOSEObjectTypeVerifier(NimbusJOSEObjectType("kb+jwt"))
        jwsKeySelector = NimbusJWSKeySelector { header, context ->
            val algorithm = header.algorithm
            val nestedSelector =
                NimbusSingleKeyJWSKeySelector<SecurityContext>(algorithm, holderPubKey)
            nestedSelector.selectJWSKeys(header, context)
        }
        jwtClaimsSetVerifier = NimbusDefaultJWTClaimsVerifier(
            challenge ?: NimbusJWTClaimsSet.Builder().build(),
            setOf("aud", "iat", "nonce"),
        )
    }

/**
 * This is a dual of [cnf] function
 * Obtains holder's pub key from claims
 *
 * @return the holder's pub key, if found
 */
val HolderPubKeyInConfirmationClaim: (Claims) -> NimbusAsymmetricJWK? = { claims ->

    claims["cnf"]
        ?.let { cnf -> if (cnf is JsonObject) cnf["jwk"] else null }
        ?.let { jwk -> if (jwk is JsonObject) jwk else null }
        ?.let { jwk ->
            runCatching {
                val key = NimbusJWK.parse(jwk.toString())
                require(key is NimbusAsymmetricJWK)
                key
            }.getOrNull()
        }
}

/**
 * An adapter that converts a [NimbusJWSVerifier] into a [JwtSignatureVerifier]
 * @return a [JwtSignatureVerifier]using the validation logic provided by [NimbusJWSVerifier]
 * @receiver the [NimbusJWSVerifier] to convert into a [JwtSignatureVerifier]
 *
 * @see NimbusJWTProcessor.asJwtVerifier
 */
fun NimbusJWSVerifier.asJwtVerifier(): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = NimbusSignedJWT.parse(unverifiedJwt)
        if (!signedJwt.verify(this)) null
        else signedJwt.jwtClaimsSet.asClaims()
    } catch (e: ParseException) {
        null
    } catch (e: NimbusJOSEException) {
        null
    }
}

/**
 * An adapter that converts a [NimbusJWTProcessor] into a [JwtSignatureVerifier]
 *
 * @return a [JwtSignatureVerifier] using the validation logic provided by [NimbusJWTProcessor]
 * @receiver the Nimbus processor to convert into [JwtSignatureVerifier]
 * @see NimbusJWSVerifier.asJwtVerifier
 */
fun NimbusJWTProcessor<*>.asJwtVerifier(): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    process(unverifiedJwt, null).asClaims()
}

/**
 * A method for obtaining an [SdJwt.Issuance] given an [unverifiedSdJwt], without checking the signature
 * of the issuer.
 *
 * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verifyIssuance] the SD-JWT and
 * wants to just re-obtain an instance of the [SdJwt.Issuance] without repeating this verification
 *
 */
fun SdJwt.Companion.unverifiedIssuanceFrom(unverifiedSdJwt: String): Result<SdJwt.Issuance<JwtAndClaims>> =
    runCatching {
        val (unverifiedJwt, unverifiedDisclosures) = parseIssuance(unverifiedSdJwt)
        verifyIssuance(unverifiedJwt, unverifiedDisclosures) {
            NimbusSignedJWT.parse(unverifiedJwt).jwtClaimsSet.asClaims()
        }.getOrThrow()
    }

//
// JSON Support
//

/**
 * An adapter that transforms the [payload][NimbusJWTClaimsSet] of a [Nimbus JWT][NimbusJWT]
 * to a KotlinX Serialization compatible representation
 */
fun NimbusJWTClaimsSet.asClaims(): Claims =
    toPayload().toBytes().run {
        val s: String = this.decodeToString()
        Json.parseToJsonElement(s).jsonObject
    }

private fun NimbusJWK.asJsonObject(): JsonObject = Json.parseToJsonElement(toJSONString()).jsonObject

//
// DSL additions
//

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * @param jwk the key to put in confirmation claim
 */
fun SdObjectBuilder.cnf(jwk: NimbusJWK) = cnf(jwk.asJsonObject())

/**
 * A variation of [sdJwt] which produces signed SD-JWT
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 * @param digestNumberHint This is an optional hint, that expresses the number of digests on the immediate level
 * of this SD-JWT, that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of [DisclosureDigest] is less than the [hint][digestNumberHint]
 *
 * @return signed SD-JWT
 *
 * @see SdJwtIssuer.Companion.nimbus which in addition allows customization of JWS Header
 */
inline fun signedSdJwt(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    digestNumberHint: Int? = null,
    builderAction: SdObjectBuilder.() -> Unit,
): SdJwt.Issuance<NimbusSignedJWT> {
    val issuer = SdJwtIssuer.nimbus(sdJwtFactory, signer, signAlgorithm)
    val sdJwtElements = sdJwt(digestNumberHint, builderAction)
    return issuer.issue(sdJwtElements).getOrThrow()
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
fun SdJwtIssuer.Companion.nimbus(
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
): SdJwtIssuer<NimbusSignedJWT> =
    NimbusSdJwtIssuerFactory.createIssuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)

private object NimbusSdJwtIssuerFactory {

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
    fun createIssuer(
        sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
    ): SdJwtIssuer<NimbusSignedJWT> = SdJwtIssuer(sdJwtFactory) { unsignedSdJwt ->
        val (claims, disclosures) = unsignedSdJwt
        val signedJwt = sign(signer, signAlgorithm, jwsHeaderCustomization)(claims).getOrThrow()
        SdJwt.Issuance(signedJwt, disclosures)
    }

    /**
     * Factory method for creating a [SdJwtIssuer] that uses Nimbus
     *
     * @param sdJwtFactory factory for creating the unsigned SD-JWT
     * @param signingKey the [key][NimbusJWK] that will sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
     *
     * @return [SdJwtIssuer] that uses Nimbus
     *
     * @see SdJwtFactory.Default
     */
    fun createIssuer(
        sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
        signingKey: NimbusJWK,
        signAlgorithm: NimbusJWSAlgorithm,
        jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
    ): SdJwtIssuer<NimbusSignedJWT> {
        val signer = NimbusDefaultJWSSignerFactory().createJWSSigner(signingKey, signAlgorithm)
        return createIssuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)
    }
}

//
// Serialization
//

/**
 * Serializes a [SdJwt] without a key binding part.
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT
 */
fun <JWT : NimbusJWT> SdJwt<JWT>.serialize(): String =
    serialize(NimbusJWT::serialize)

/**
 * Representation of a function used to sign the Keybinding JWT of a Presentation SD-JWT.
 */
interface KeyBindingSigner : JWSSigner {
    val signAlgorithm: JWSAlgorithm
    val publicKey: AsymmetricJWK
    override fun supportedJWSAlgorithms(): MutableSet<JWSAlgorithm> = mutableSetOf(signAlgorithm)
}

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT.
 *
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
fun <JWT : NimbusJWT> SdJwt.Presentation<JWT>.serializeWithKeyBinding(
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: JWTClaimsSet.Builder.() -> Unit,
): String =
    serializeWithKeyBinding(NimbusJWT::serialize, hashAlgorithm, keyBindingSigner, claimSetBuilderAction)

/**
 * Serializes a [SdJwt.Presentation] with a Key Binding JWT  in JWS JSON
 *
 * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
 * in the generated Key Binding JWT
 * @param keyBindingSigner function used to sign the generated Key Binding JWT
 * @param claimSetBuilderAction a function that can be used to further customize the claims
 * of the generated Key Binding JWT.
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to be serialized
 * @return the serialized SD-JWT including the generated Key Binding JWT
 */
fun <JWT : NimbusJWT> SdJwt.Presentation<JWT>.serializeWithKeyBindingAsJwsJson(
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: JWTClaimsSet.Builder.() -> Unit,
): JsonObject =
    serializeWithKeyBindingAsJwsJson(NimbusJWT::serialize, hashAlgorithm, keyBindingSigner, claimSetBuilderAction)

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
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBinding(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: JWTClaimsSet.Builder.() -> Unit,
): String {
    val (presentationSdJwt, kbJwt) = serializedAndKeyBinding(
        jwtSerializer,
        hashAlgorithm,
        keyBindingSigner,
        claimSetBuilderAction,
    )
    // concatenate the two parts together
    return "$presentationSdJwt$kbJwt"
}

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
 * @return a pair of the serialized SD-JWT and the generated Key Binding JWT
 */
internal fun <JWT> SdJwt.Presentation<JWT>.serializedAndKeyBinding(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: JWTClaimsSet.Builder.() -> Unit,
): Pair<Jwt, Jwt> {
    // Serialize the presentation SD-JWT with no Key binding
    val presentationSdJwt = serialize(jwtSerializer)
    // Calculate its digest
    val sdJwtDigest = SdJwtDigest.digest(hashAlgorithm, presentationSdJwt).getOrThrow()
    // Create the Key Binding JWT, sign it and serialize it
    val kbJwt = NimbusSignedJWT(
        with(JWSHeader.Builder(keyBindingSigner.signAlgorithm)) {
            type(JOSEObjectType("kb+jwt"))
            val pk = keyBindingSigner.publicKey
            if (pk is NimbusJWK) {
                keyID(pk.keyID)
            }
            build()
        },
        JWTClaimsSet.Builder()
            .apply(claimSetBuilderAction)
            .claim(SdJwtDigest.CLAIM_NAME, sdJwtDigest.value)
            .build(),
    ).apply { sign(keyBindingSigner) }.serialize()

    return presentationSdJwt to kbJwt
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
fun <JWT> SdJwt.Presentation<JWT>.serializeWithKeyBindingAsJwsJson(
    jwtSerializer: (JWT) -> String,
    hashAlgorithm: HashAlgorithm,
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: JWTClaimsSet.Builder.() -> Unit,
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject {
    val (presentationSdJwt, kbJwt) = serializedAndKeyBinding(
        jwtSerializer,
        hashAlgorithm,
        keyBindingSigner,
        claimSetBuilderAction,
    )
    val nimbusSdJwt = SdJwt.Presentation(NimbusSignedJWT.parse(presentationSdJwt), disclosures)
    return nimbusSdJwt.asJwsJsonObject(option, kbJwt) { jwt ->
        Triple(
            jwt.header.toBase64URL().toString(),
            jwt.payload.toBase64URL().toString(),
            jwt.signature.toString(),
        )
    }
}

/**
 * Creates a representation of an [SdJwt] as a JWS JSON according to RFC7515.
 * In addition to the General & Flattened representations defined in the RFC7515,
 *  the result JSON contains an unprotected header which includes
 *  an array with the disclosures of the [SdJwt]
 *
 *  This method can be used with an [SdJwt.Issuance] or with an [SdJwt.Presentation] in case no Key binding JWT
 *  is required. If key binding JWT is required for an [SdJwt.Presentation] there is [serializeWithKeyBindingAsJwsJson]
 *  to support this
 *
 * @param option to produce a [JwsSerializationOption.General] or [JwsSerializationOption.Flattened]
 * representation as defined in RFC7515
 * @receiver the [SdJwt] to serialize
 *
 * @return a JSON object either general or flattened according to RFC7515 having an additional
 * disclosures array as per SD-JWT extension
 */
fun SdJwt<NimbusSignedJWT>.serializeAsJwsJson(
    option: JwsSerializationOption = JwsSerializationOption.Flattened,
): JsonObject {
    return asJwsJsonObject(option, kbJwt = null) { jwt ->
        require(jwt.state == JWSObject.State.SIGNED || jwt.state == JWSObject.State.VERIFIED) {
            "It seems that the jwt is not signed"
        }
        Triple(
            jwt.header.toBase64URL().toString(),
            jwt.payload.toBase64URL().toString(),
            jwt.signature.toString(),
        )
    }
}

/**
 * Creates a function that given some [Claims] signs them producing a [NimbusSignedJWT]
 *
 * @param signer a way to sign the claims of the envelope JWT
 * @param signAlgorithm the algorithm to use
 * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
 *
 * @return a function that given some [Claims] signs them producing a [NimbusSignedJWT]
 */
private fun sign(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
): (Claims) -> Result<NimbusSignedJWT> = { claims ->
    runCatching {
        val jwsHeader = with(NimbusJWSHeader.Builder(signAlgorithm)) {
            jwsHeaderCustomization()
            build()
        }
        val jwtClaimSet = NimbusJWTClaimsSet.parse(claims.toString())
        NimbusSignedJWT(jwsHeader, jwtClaimSet).apply { sign(signer) }
    }
}

//
// Presentation
//

/**
 * Tries to create a presentation that discloses the claims are in [query]
 * @param query a set of [JsonPointer] relative to the unprotected JSON (not the JWT payload). Pointers for
 * claims that are always disclosable can be omitted
 * @receiver The issuance SD-JWT upon which the presentation will be based
 *
 * @return the presentation if possible to satisfy the [query]
 */
fun SdJwt.Issuance<NimbusSignedJWT>.present(query: Set<JsonPointer>): SdJwt.Presentation<NimbusSignedJWT>? =
    present(query) { it.jwtClaimsSet.asClaims() }

/**
 *  Tries to create a presentation that discloses the claims that satisfy
 *  [query]
 * @param query a predicate for the claims to include in the presentation. The [JsonPointer]
 * is relative to the unprotected JSON (not the JWT payload)
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @return the presentation if possible to satisfy the [query]
 */
fun SdJwt.Issuance<NimbusSignedJWT>.present(query: (JsonPointer) -> Boolean): SdJwt.Presentation<NimbusSignedJWT>? =
    present(query) { it.jwtClaimsSet.asClaims() }
