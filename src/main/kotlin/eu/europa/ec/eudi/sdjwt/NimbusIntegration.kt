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

import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObjectBuilder
import eu.europa.ec.eudi.sdjwt.vc.NimbusSdJwtVcVerifierFactory
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifierFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.security.cert.X509Certificate
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
 * Creates a [NimbusJWTProcessor] suitable for verifying the Key Binding JWT
 * Enforces the following rules:
 * - The header contains typ claim equal to `kb+jwt`
 * - The header contains the signing algorithm claim
 * - The JWT must be signed by the given [holderPubKey]
 * - If the challenge is provided it should present in JWT payload
 * - Claims `aud`, `iat` and `nonce`must be present to the JWT payload
 *
 * @param holderPubKey the public key of the holder
 * @param challenge an optional challenge provided by the verifier, to be signed by the holder as the Key-binding JWT.
 * If provided, Key Binding JWT payload should contain the challenge as is.
 * @return
 */
internal fun <PubKey> keyBindingJWTProcess(
    holderPubKey: PubKey,
    challenge: NimbusJWTClaimsSet? = null,
): NimbusJWTProcessor<NimbusSecurityContext> where PubKey : NimbusJWK, PubKey : NimbusAsymmetricJWK =
    JwkSourceJWTProcessor(
        NimbusDefaultJOSEObjectTypeVerifier(NimbusJOSEObjectType("kb+jwt")),
        NimbusDefaultJWTClaimsVerifier(
            challenge ?: NimbusJWTClaimsSet.Builder().build(),
            setOf(RFC7519.AUDIENCE, RFC7519.ISSUED_AT, "nonce"),
        ),
        NimbusImmutableJWKSet(NimbusJWKSet(listOf(holderPubKey))),
        true,
    )

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
 * @param jwk the key to put in the confirmation claim
 */
fun SdJwtObjectBuilder.cnf(jwk: NimbusJWK) = claim("cnf", buildJsonObject { put("jwk", jwk.asJsonObject()) })

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
        val signedJwt = withContext(Dispatchers.IO) {
            sign(signer, signAlgorithm, jwsHeaderCustomization)(claims).getOrThrow()
        }
        SdJwt(signedJwt, disclosures)
    }
}

//
// Serialization
//
object NimbusSdJwtOps :
    SdJwtSerializationOps<NimbusSignedJWT> by NimbusSerializationOps,
    SdJwtPresentationOps<NimbusSignedJWT> by NimbusPresentationOps,
    SdJwtVerifier<NimbusSignedJWT> by NimbusVerifier {

    /**
     * Factory method for creating a [KeyBindingVerifier] which applies the rules described in [keyBindingJWTProcess].
     *
     *  @param holderPubKeyExtractor a function that extracts the holder's public key from the payload of the SD-JWT.
     * If not provided, it is assumed that the SD-JWT issuer used the confirmation claim (see [cnf]) for this purpose.

     * @param challenge an optional challenge provided by the verifier, to be signed by the holder as the Key-binding JWT.
     * If provided, Key Binding JWT payload should contain the challenge as is.
     *
     * @see keyBindingJWTProcess
     */
    fun KeyBindingVerifier.Companion.mustBePresentAndValid(
        holderPubKeyExtractor: (JsonObject) -> NimbusAsymmetricJWK? = HolderPubKeyInConfirmationClaim,
        challenge: JsonObject? = null,
    ): KeyBindingVerifier.MustBePresentAndValid<NimbusSignedJWT> {
        val keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier<NimbusSignedJWT> = { sdJwtClaims ->
            val holderPublicKey = holderPubKeyExtractor(sdJwtClaims) ?: throw KeyBindingError.MissingHolderPublicKey.asException()
            if (holderPublicKey !is NimbusJWK) throw KeyBindingError.UnsupportedHolderPublicKey.asException()

            val challengeClaimSet: NimbusJWTClaimsSet? = challenge?.let { NimbusJWTClaimsSet.parse(Json.encodeToString(it)) }
            keyBindingJWTProcess(holderPublicKey, challengeClaimSet).asJwtVerifier()
        }
        return KeyBindingVerifier.MustBePresentAndValid(keyBindingVerifierProvider)
    }

    /**
     * An adapter that converts a [NimbusJWSVerifier] into a [JwtSignatureVerifier]
     * @return a [JwtSignatureVerifier]using the validation logic provided by [NimbusJWSVerifier]
     * @receiver the [NimbusJWSVerifier] to convert into a [JwtSignatureVerifier]
     *
     */
    fun NimbusJWSVerifier.asJwtVerifier(): JwtSignatureVerifier<NimbusSignedJWT> =
        JwtSignatureVerifier { unverifiedJwt ->
            withContext(Dispatchers.IO) {
                try {
                    val signedJwt = NimbusSignedJWT.parse(unverifiedJwt)
                    if (!signedJwt.verify(this@asJwtVerifier)) null
                    else signedJwt
                } catch (_: ParseException) {
                    null
                } catch (_: NimbusJOSEException) {
                    null
                }
            }
        }

    /**
     * An adapter that converts a [NimbusJWTProcessor] into a [JwtSignatureVerifier]
     *
     * @return a [JwtSignatureVerifier] using the validation logic provided by [NimbusJWTProcessor]
     * @receiver the Nimbus processor to convert into [JwtSignatureVerifier]
     */
    fun NimbusJWTProcessor<*>.asJwtVerifier(): JwtSignatureVerifier<NimbusSignedJWT> =
        JwtSignatureVerifier { unverifiedJwt ->
            withContext(Dispatchers.IO) {
                try {
                    val signedJwt = NimbusSignedJWT.parse(unverifiedJwt)
                    process(signedJwt, null)
                    signedJwt
                } catch (_: ParseException) {
                    null
                } catch (_: NimbusJOSEException) {
                    null
                }
            }
        }

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
                runCatchingCancellable {
                    val key = NimbusJWK.parse(jwk.toString())
                    require(key is NimbusAsymmetricJWK)
                    key
                }.getOrNull()
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
    fun issuer(
        sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
    ): SdJwtIssuer<NimbusSignedJWT> =
        NimbusSdJwtIssuerFactory.createIssuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)

    fun kbJwtIssuer(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        publicKey: NimbusAsymmetricJWK,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit = {},
    ): BuildKbJwt = BuildKbJwt { sdJwtDigest ->
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val header = NimbusJWSHeader.Builder(signAlgorithm).apply {
                    type(NimbusJOSEObjectType(SdJwtSpec.MEDIA_SUBTYPE_KB_JWT))
                    val pk = publicKey
                    if (pk is NimbusJWK) {
                        keyID(pk.keyID)
                    }
                }.build()
                val claimSet = NimbusJWTClaimsSet.Builder().apply {
                    claimSetBuilderAction()
                    claim(SdJwtSpec.CLAIM_SD_HASH, sdJwtDigest.value)
                }.build()

                NimbusSignedJWT(header, claimSet).apply { sign(signer) }.serialize()
            }
        }
    }

    val SdJwtVcVerifier: SdJwtVcVerifierFactory<NimbusSignedJWT, NimbusJWK, List<X509Certificate>> = NimbusSdJwtVcVerifierFactory
}

private val NimbusSerializationOps: SdJwtSerializationOps<NimbusSignedJWT> =
    SdJwtSerializationOps(
        serializeJwt = { jwt ->
            check(jwt.state == NimbusJWSObject.State.SIGNED || jwt.state == NimbusJWSObject.State.VERIFIED) {
                "It seems that the jwt is not signed"
            }
            jwt.serialize()
        },
        hashAlgorithm = { jwt ->
            jwt.jwtClaimsSet.getStringClaim(SdJwtSpec.CLAIM_SD_ALG)?.let {
                checkNotNull(HashAlgorithm.fromString(it)) { "Unknown hash algorithm $it" }
            }
        },
    )

private val NimbusPresentationOps: SdJwtPresentationOps<NimbusSignedJWT> = SdJwtPresentationOps { jwt -> jwt.jwtClaimsSet.jsonObject() }

private val NimbusVerifier: SdJwtVerifier<NimbusSignedJWT> = SdJwtVerifier { jwt -> jwt.jwtClaimsSet.jsonObject() }

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
    runCatchingCancellable {
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

//
// JWT Processor, works on JWKSource
//

internal open class JwkSourceJWTProcessor<C : NimbusSecurityContext>(
    private val typeVerifier: NimbusJOSEObjectTypeVerifier<C>? = null,
    private val claimSetVerifier: NimbusJWTClaimsSetVerifier<C>? = null,
    private val jwkSource: NimbusJWKSource<C>,
    private val useKeyId: Boolean,
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
        val jwkMatcher =
            if (useKeyId) NimbusJWKMatcher.forJWSHeader(signedJWT.header)
            else NimbusJWKMatcher.forJWSHeader(signedJWT.header).withoutKeyId()
        val jwkSelector = NimbusJWKSelector(jwkMatcher)

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

private fun NimbusJWKMatcher.withoutKeyId(): NimbusJWKMatcher =
    NimbusJWKMatcher.Builder(this)
        .keyID(null)
        .withKeyIDOnly(false)
        .build()
