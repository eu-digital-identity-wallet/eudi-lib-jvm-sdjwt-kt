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

import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.ParseException
import com.nimbusds.jose.JOSEException as NimbusJOSEException
import com.nimbusds.jose.JOSEObjectType as NimbusJOSEObjectType
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSObject as NimbusJWSObject
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier as NimbusECDSAVerifier
import com.nimbusds.jose.crypto.Ed25519Verifier as NimbusEd25519Verifier
import com.nimbusds.jose.crypto.MACVerifier as NimbusMACVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier as NimbusRSASSAVerifier
import com.nimbusds.jose.jwk.AsymmetricJWK as NimbusAsymmetricJWK
import com.nimbusds.jose.jwk.ECKey as NimbusECKey
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jose.jwk.JWKMatcher as NimbusJWKMatcher
import com.nimbusds.jose.jwk.JWKSelector as NimbusJWKSelector
import com.nimbusds.jose.jwk.JWKSet as NimbusJWKSet
import com.nimbusds.jose.jwk.OctetKeyPair as NimbusOctetKeyPair
import com.nimbusds.jose.jwk.OctetSequenceKey as NimbusOctetSequenceKey
import com.nimbusds.jose.jwk.RSAKey as NimbusRSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet as NimbusImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource as NimbusJWKSource
import com.nimbusds.jose.proc.BadJOSEException as NimbusBadJOSEException
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier as NimbusDefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier as NimbusJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.SecurityContext as NimbusSecurityContext
import com.nimbusds.jwt.EncryptedJWT as NimbusEncryptedJWT
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.JWTParser as NimbusJWTParser
import com.nimbusds.jwt.PlainJWT as NimbusPlainJWT
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
import com.nimbusds.jwt.proc.BadJWTException as NimbusBadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier as NimbusDefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier as NimbusJWTClaimsSetVerifier
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
internal val PlatformJwtSignatureVerifierNoSignatureValidation: JwtSignatureVerifier by lazy {
    JwtSignatureVerifier { unverifiedJwt ->
        try {
            val parsedJwt = NimbusSignedJWT.parse(unverifiedJwt)
            parsedJwt.jwtClaimsSet.jsonObject()
        } catch (_: ParseException) {
            null
        }
    }
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
    holderPubKeyExtractor: (JsonObject) -> NimbusAsymmetricJWK? = HolderPubKeyInConfirmationClaim,
    challenge: JsonObject? = null,
): KeyBindingVerifier.MustBePresentAndValid {
    val keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier = { sdJwtClaims ->
        holderPubKeyExtractor(sdJwtClaims)?.let { holderPubKey ->
            val challengeClaimSet: NimbusJWTClaimsSet? =
                challenge?.let { NimbusJWTClaimsSet.parse(it.toString()) }
            check(holderPubKey is NimbusJWK)
            keyBindingJWTProcess(holderPubKey, challengeClaimSet).asJwtVerifier()
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
internal fun <PubKey> keyBindingJWTProcess(
    holderPubKey: PubKey,
    challenge: NimbusJWTClaimsSet? = null,
): NimbusJWTProcessor<NimbusSecurityContext> where PubKey : NimbusJWK, PubKey : NimbusAsymmetricJWK =
    JwkSourceJWTProcessor(
        typeVerifier = NimbusDefaultJOSEObjectTypeVerifier(NimbusJOSEObjectType("kb+jwt")),
        claimSetVerifier = NimbusDefaultJWTClaimsVerifier(
            challenge ?: NimbusJWTClaimsSet.Builder().build(),
            setOf("aud", "iat", "nonce"),
        ),
        jwkSource = NimbusImmutableJWKSet(NimbusJWKSet(listOf(holderPubKey))),
    )

/**
 * This is a dual of [cnf] function
 * Obtains holder's pub key from claims
 *
 * @return the holder's pub key, if found
 */
val HolderPubKeyInConfirmationClaim: (JsonObject) -> NimbusAsymmetricJWK? = { claims ->

    claims["cnf"]
        ?.let { cnf -> if (cnf is JsonObject) cnf["jwk"] else null }
        ?.let { jwk -> jwk as? JsonObject }
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
        else signedJwt.jwtClaimsSet.jsonObject()
    } catch (_: ParseException) {
        null
    } catch (_: NimbusJOSEException) {
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
    process(unverifiedJwt, null).jsonObject()
}

/**
 * A method for obtaining an [SdJwt.Issuance] given an [unverifiedSdJwt], without checking the signature
 * of the issuer.
 *
 * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verifyIssuance] the SD-JWT and
 * wants to just re-obtain an instance of the [SdJwt.Issuance] without repeating this verification
 *
 */
internal val PlatformSdJwtUnverifiedIssuanceFrom: UnverifiedIssuanceFrom = UnverifiedIssuanceFrom { unverifiedSdJwt ->
    runCatching {
        val (unverifiedJwt, unverifiedDisclosures) = StandardSerialization.parseIssuance(unverifiedSdJwt)
        verifyIssuance(unverifiedJwt, unverifiedDisclosures) {
            NimbusSignedJWT.parse(unverifiedJwt).jwtClaimsSet.jsonObject()
        }.getOrThrow()
    }
}

//
// JSON Support
//

/**
 * An adapter that transforms the [payload][NimbusJWTClaimsSet] of a [Nimbus JWT][NimbusJWT]
 * to a KotlinX Serialization compatible representation
 */
fun NimbusJWTClaimsSet.jsonObject(): JsonObject =
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
 * @param digestNumberHint This is an optional hint; that expresses the number of digests on the immediate level
 * of this SD-JWT, that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of [DisclosureDigest] is less than the [hint][digestNumberHint]
 *
 * @return signed SD-JWT
 *
 * @see SdJwtIssuer.Companion.nimbus which in addition allows customization of JWS Header
 */
suspend inline fun signedSdJwt(
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
}

//
// Serialization
//
interface NimbusSdJwtOps : SdJwtSerializationOps<NimbusSignedJWT> {

    override fun SdJwt<NimbusSignedJWT>.serialize(): String = with(defaultOps) { serialize() }

    override fun SdJwt<NimbusSignedJWT>.asJwsJsonObject(option: JwsSerializationOption, kbJwt: Jwt?): JsonObject {
        require(jwt.state == NimbusJWSObject.State.SIGNED || jwt.state == NimbusJWSObject.State.VERIFIED) {
            "It seems that the jwt is not signed"
        }
        return with(defaultOps) { asJwsJsonObject(option, kbJwt) }
    }

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
    suspend fun SdJwt.Presentation<NimbusSignedJWT>.serializeWithKeyBinding(
        hashAlgorithm: HashAlgorithm,
        keyBindingSigner: KeyBindingSigner,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    ): Result<String> {
        val kbJwtBuilder = KbJwtBuilder(keyBindingSigner, claimSetBuilderAction)
        return serializeWithKeyBinding(hashAlgorithm, kbJwtBuilder, JsonObject(emptyMap()))
    }

    suspend fun SdJwt.Presentation<NimbusSignedJWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        hashAlgorithm: HashAlgorithm,
        keyBindingSigner: KeyBindingSigner,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    ): Result<JsonObject> {
        val kbJwtBuilder = KbJwtBuilder(keyBindingSigner, claimSetBuilderAction)
        return asJwsJsonObjectWithKeyBinding(option, hashAlgorithm, kbJwtBuilder, JsonObject(emptyMap()))
    }

    companion object : NimbusSdJwtOps {
        private val defaultOps = SdJwtSerializationOps<NimbusSignedJWT>({ jwt -> jwt.serialize() })

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
        fun issuer(
            sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
            signer: NimbusJWSSigner,
            signAlgorithm: NimbusJWSAlgorithm,
            jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
        ): SdJwtIssuer<NimbusSignedJWT> =
            NimbusSdJwtIssuerFactory.createIssuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)
    }
}

/**
 * Representation of a function used to sign the Keybinding JWT of a Presentation SD-JWT.
 */
interface KeyBindingSigner : NimbusJWSSigner {
    val signAlgorithm: NimbusJWSAlgorithm
    val publicKey: NimbusAsymmetricJWK
    override fun supportedJWSAlgorithms(): MutableSet<NimbusJWSAlgorithm> = mutableSetOf(signAlgorithm)
}

internal fun KbJwtBuilder(
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
): KbJwtBuilder = KbJwtBuilder { sdJwtDigest, _ ->
    withContext(Dispatchers.IO) {
        runCatching {
            kbJwt(keyBindingSigner, claimSetBuilderAction, sdJwtDigest).serialize()
        }
    }
}

internal fun kbJwt(
    keyBindingSigner: KeyBindingSigner,
    claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    sdJwtDigest: SdJwtDigest,
): NimbusJWT {
    val header = with(NimbusJWSHeader.Builder(keyBindingSigner.signAlgorithm)) {
        type(NimbusJOSEObjectType(SdJwtSpec.MEDIA_SUBTYPE_KB_JWT))
        val pk = keyBindingSigner.publicKey
        if (pk is NimbusJWK) {
            keyID(pk.keyID)
        }
        build()
    }
    val claimSet = with(NimbusJWTClaimsSet.Builder()) {
        claimSetBuilderAction()
        claim(SdJwtSpec.CLAIM_SD_HASH, sdJwtDigest.value)
        build()
    }

    return NimbusSignedJWT(header, claimSet).apply { sign(keyBindingSigner) }
}

/**
 * Creates a function that given some claims signs them producing a [NimbusSignedJWT]
 *
 * @param signer a way to sign the claims of the envelope JWT
 * @param signAlgorithm the algorithm to use
 * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
 *
 * @return a function that given some claims signs them producing a [NimbusSignedJWT]
 */
private fun sign(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
): (JsonObject) -> Result<NimbusSignedJWT> = { claims ->
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
 *  Tries to create a presentation that discloses the claims that satisfy
 *  [query]
 * @param query a set of [ClaimPaths][ClaimPath] to include in the presentation. The [ClaimPaths][ClaimPath]
 * are relative to the unprotected JSON (not the JWT payload)
 * @receiver The issuance SD-JWT upon which the presentation will be based
 * @return the presentation if possible to satisfy the [query]
 */
fun SdJwt.Issuance<NimbusSignedJWT>.present(query: Set<ClaimPath>): SdJwt.Presentation<NimbusSignedJWT>? =
    present(query) { it.jwtClaimsSet.jsonObject() }

//
// JWT Processor, works on JWKSource
//

internal open class JwkSourceJWTProcessor<C : NimbusSecurityContext>(
    private val typeVerifier: NimbusJOSEObjectTypeVerifier<C>? = null,
    private val claimSetVerifier: NimbusJWTClaimsSetVerifier<C>? = null,
    private val jwkSource: NimbusJWKSource<C>,
) : NimbusJWTProcessor<C> {

    private fun notSupported(): Nothing = throw NimbusBadJOSEException("Only Nimbus SignedJWTs are supported")
    override fun process(plainJWT: NimbusPlainJWT, context: C?): NimbusJWTClaimsSet? = notSupported()
    override fun process(encryptedJWT: NimbusEncryptedJWT, context: C?): NimbusJWTClaimsSet? = notSupported()

    override fun process(jwtString: String, context: C?): NimbusJWTClaimsSet? =
        process(NimbusJWTParser.parse(jwtString), context)

    override fun process(jwt: NimbusJWT, context: C?): NimbusJWTClaimsSet? =
        when (jwt) {
            is NimbusSignedJWT -> process(jwt, context)
            else -> notSupported()
        }

    override fun process(signedJWT: NimbusSignedJWT, context: C?): NimbusJWTClaimsSet {
        typeVerifier?.verify(signedJWT.header.type, context)

        val claimsSet = signedJWT.jwtClaimSet()
        val jwkSelector = NimbusJWKSelector(NimbusJWKMatcher.forJWSHeader(signedJWT.header))

        val jwks = jwkSource.get(jwkSelector, context)
        if (jwks.isNullOrEmpty()) {
            throw NimbusBadJOSEException("Signed JWT rejected: Another algorithm expected, or no matching key(s) found")
        }

        for (jwk in jwks) {
            val verifier = jwsVerifierFor(signedJWT.header.algorithm, jwk)
            if (signedJWT.verify(verifier)) {
                claimSetVerifier?.verify(claimsSet, context)
                return claimsSet
            }
        }

        // No more keys to try out
        throw NimbusBadJOSEException("Signed JWT rejected: Invalid signature or no matching verifier(s) found")
    }

    companion object {

        private fun NimbusSignedJWT.jwtClaimSet(): NimbusJWTClaimsSet =
            try {
                getJWTClaimsSet()
            } catch (e: ParseException) {
                // Payload not a JSON object
                throw NimbusBadJWTException(e.message, e)
            }

        private fun jwsVerifierFor(algorithm: NimbusJWSAlgorithm, jwk: NimbusJWK): NimbusJWSVerifier =
            when (algorithm) {
                in NimbusJWSAlgorithm.Family.HMAC_SHA -> NimbusMACVerifier(jwk.expectIs<NimbusOctetSequenceKey>())
                in NimbusJWSAlgorithm.Family.RSA -> NimbusRSASSAVerifier(jwk.expectIs<NimbusRSAKey>())
                in NimbusJWSAlgorithm.Family.EC -> NimbusECDSAVerifier(jwk.expectIs<NimbusECKey>())
                in NimbusJWSAlgorithm.Family.ED -> NimbusEd25519Verifier(jwk.expectIs<NimbusOctetKeyPair>())
                else -> throw NimbusBadJOSEException("Unsupported JWS algorithm $algorithm")
            }

        private inline fun <reified T> NimbusJWK.expectIs(): T =
            if (this is T) {
                this
            } else {
                throw NimbusBadJOSEException("Expected a JWK of type ${T::class.java.simpleName}")
            }
    }
}
