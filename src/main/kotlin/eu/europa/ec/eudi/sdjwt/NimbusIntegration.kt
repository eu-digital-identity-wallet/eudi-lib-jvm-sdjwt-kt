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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.ParseException
import com.nimbusds.jose.JOSEException as NimbusJOSEException
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
import com.nimbusds.jwt.proc.JWTProcessor as NimbusJWTProcessor

//
// Signature Verification support
//

/**
 * Adds a [JwtSignatureVerifier], to the companion object, which just checks/parses the JWT,
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
 * Declares a [KeyBindingVerifier] that just makes sure that the Key Binding JWT is present and it's indeed a JWT
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
    holderPubKeyExtractor: (Claims) -> JWK? = HolderPubKeyInConfirmationClaim,
    challenge: Claims? = null,
): KeyBindingVerifier.MustBePresentAndValid {
    val keyBindingVerifierProvider: (Claims) -> JwtSignatureVerifier = { sdJwtClaims ->
        holderPubKeyExtractor(sdJwtClaims)?.let { holderPubKey ->
            val holderPubKeyJWK = holderPubKey.toPublicJWK()
            val challengeClaimSet: JWTClaimsSet = JWTClaimsSet.parse(challenge.toString())
            keyBindingJWTProcess(holderPubKeyJWK, challengeClaimSet).asJwtVerifier()
        } ?: throw KeyBindingError.MissingHolderPubKey.asException()
    }
    return KeyBindingVerifier.MustBePresentAndValid(keyBindingVerifierProvider)
}

/**
 * Creates a [JWTProcessor] suitable for verifying the Key Binding JWT
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
fun keyBindingJWTProcess(holderPubKey: JWK, challenge: JWTClaimsSet? = null): JWTProcessor<SecurityContext> =
    DefaultJWTProcessor<SecurityContext>().apply {
        jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(JOSEObjectType("kb+jwt"))
        jwsKeySelector = JWSKeySelector { header, context ->
            val algorithm = header.algorithm
            val nestedSelector =
                SingleKeyJWSKeySelector<SecurityContext>(algorithm, holderPubKey.toECKey().toECPublicKey())
            nestedSelector.selectJWSKeys(header, context)
        }
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            challenge ?: JWTClaimsSet.Builder().build(),
            setOf("aud", "iat", "nonce"),
        )
    }

/**
 * This is a dual of [cnf] function
 * Obtains holder's pub key from claims
 *
 * @return the holder's pub key, if found
 */
val HolderPubKeyInConfirmationClaim: (Claims) -> JWK? = { claims ->

    claims["cnf"]
        ?.let { cnf -> if (cnf is JsonObject) cnf["jwk"] else null }
        ?.let { jwk -> if (jwk is JsonObject) jwk else null }
        ?.let { jwk -> runCatching { JWK.parse(jwk.toString()) }.getOrNull() }
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

//
// JSON Support
//

/**
 * An adapter that transforms the [payload][JWTClaimsSet] of a [Nimbus JWT][NimbusJWT]
 * to a KotlinX Serialization combatible representation
 */
fun NimbusJWTClaimsSet.asClaims(): Claims =
    toPayload().toBytes().run {
        val s: String = this.decodeToString()
        Json.parseToJsonElement(s).jsonObject
    }

private fun JWK.asJsonObject(): JsonObject = Json.parseToJsonElement(toJSONString()).jsonObject

//
// DSL additions
//

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * @param jwk the key to put in confirmation claim
 */
fun SdObjectBuilder.cnf(jwk: JWK) = cnf(jwk.asJsonObject())

/**
 * A variation of [sdJwt] which produces signed SD-JWT
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 *
 * @return signed SD-JWT
 *
 * @see SdJwtIssuer.Companion.nimbus which in addition allows customization of JWS Header
 */
inline fun signedSdJwt(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    builderAction: SdObjectBuilder.() -> Unit,
): SdJwt.Issuance<NimbusSignedJWT> {
    val issuer = SdJwtIssuer.nimbus(sdJwtFactory, signer, signAlgorithm)
    val sdJwtElements = sdJwt(builderAction)
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

    private const val allowSymmetric = true

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
        require(allowSymmetric || signAlgorithm.isAsymmetric()) { "Only asymmetric algorithm can be used" }
        val header = with(NimbusJWSHeader.Builder(signAlgorithm)) {
            jwsHeaderCustomization()
            build()
        }
        val signedJwt = NimbusSignedJWT(header, NimbusJWTClaimsSet.parse(claims.toString())).apply { sign(signer) }
        SdJwt.Issuance(signedJwt, disclosures)
    }

    /**
     * Indicates whether an [NimbusJWSAlgorithm] is asymmetric
     * @receiver the algorithm to check
     * @return true if algorithm is asymmetric.
     */
    private fun NimbusJWSAlgorithm.isAsymmetric(): Boolean = NimbusJWSAlgorithm.Family.SIGNATURE.contains(this)
}

//
// Serialization
//

/**
 * Serializes a [SdJwt] into either Combined Issuance or Combined Presentation format
 * depending on the case
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param HB_JWT the type representing the Holder Binding part of the SD
 * @receiver the SD-JWT to be serialized
 * @return the SD-JWT in either  Combined Issuance or Combined Presentation format depending on the case
 */
fun <JWT : NimbusJWT, HB_JWT : NimbusJWT> SdJwt<JWT, HB_JWT>.serialize(): String = when (this) {
    is SdJwt.Issuance<JWT> -> toCombinedIssuanceFormat(NimbusJWT::serialize)
    is SdJwt.Presentation<JWT, HB_JWT> -> toCombinedPresentationFormat(NimbusJWT::serialize, NimbusJWT::serialize)
}