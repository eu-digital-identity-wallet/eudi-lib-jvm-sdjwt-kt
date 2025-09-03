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

import eu.europa.ec.eudi.sdjwt.KeyBindingError.*
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.Companion.mustBePresent
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.MustBePresentAndValid
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.MustNotBePresent
import eu.europa.ec.eudi.sdjwt.VerificationError.*
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerificationError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Errors that may occur during SD-JWT verification
 *
 * They are raised as [SdJwtVerificationException]
 */
sealed interface VerificationError {

    /**
     * SD-JWT is not in Combined Issuance of Presentation format
     */
    data object ParsingError : VerificationError

    /**
     * SD-JWT contains an invalid JWT
     */
    data class InvalidJwt(val message: String? = null, val cause: Throwable? = null) : VerificationError {
        constructor(cause: Throwable) : this(null, cause)
    }

    /**
     * Failure to verify key binding
     * @param details the specific problem
     */
    data class KeyBindingFailed(val details: KeyBindingError) : VerificationError

    /**
     * SD-JWT contains invalid disclosures (cannot obtain a claim)
     */
    data class InvalidDisclosures(val invalidDisclosures: Map<String, List<String>>) : VerificationError {
        init {
            require(invalidDisclosures.isNotEmpty())
            require(invalidDisclosures.values.all { it.isNotEmpty() })
        }
    }

    /**
     * SD-JWT contains a JWT which contains an unsupported Hashing Algorithm claim
     */
    data class UnsupportedHashingAlgorithm(val algorithm: String) : VerificationError

    /**
     * SD-JWT contains non-unique disclosures
     */
    data class NonUniqueDisclosures(val nonUniqueDisclosures: List<String>) : VerificationError {
        init {
            require(nonUniqueDisclosures.isNotEmpty())
        }
    }

    /**
     * SD-JWT contains a JWT which has non unique digests
     */
    data object NonUniqueDisclosureDigests : VerificationError

    /**
     * SD-JWT doesn't contain digests for the [disclosures]
     * @param disclosures The disclosures for which there are no digests
     */
    data class MissingDigests(val disclosures: List<Disclosure>) : VerificationError

    /**
     * Failed to verify an SD-JWT VC.
     */
    data class SdJwtVcError(val error: SdJwtVcVerificationError) : VerificationError
}

/**
 * An exception carrying a [verification error][reason]
 * @param reason the problem
 */
data class SdJwtVerificationException(val reason: VerificationError) : Exception()

/**
 * Creates a [SdJwtVerificationException] for the given error
 *
 * @receiver the error to be wrapped into the exception
 * @return an exception with the given error
 */
fun VerificationError.asException(): SdJwtVerificationException = SdJwtVerificationException(this)

/**
 * An interface that abstracts the verification of JWT signature
 *
 * Implementations should provide [checkSignature]
 */
fun interface JwtSignatureVerifier<out JWT> {

    /**
     * Verifies the signature of the [jwt] and extracts its payload
     * @param jwt the JWT to validate
     * @return the payload of the JWT if signature is valid, otherwise raises [InvalidJwt]
     */
    suspend fun verify(jwt: String): Result<JWT> =
        runCatchingCancellable {
            checkSignature(jwt) ?: throw InvalidJwt().asException()
        }

    /**
     * Implement this method to check the signature of the JWT and extract its payload
     * @param jwt the JWT to validate
     * @return the payload of the JWT if signature is valid, otherwise null
     */
    suspend fun checkSignature(jwt: String): JWT?
}

fun <JWT, JWT1> JwtSignatureVerifier<JWT>.map(
    f: (JWT) -> JWT1,
): JwtSignatureVerifier<JWT1> {
    return JwtSignatureVerifier { jwt -> checkSignature(jwt)?.let { f(it) } }
}

/**
 * Errors related to Key Binding
 */
sealed interface KeyBindingError {

    /**
     * Indicates that the public key of the holder cannot be located in SD-JWT claims
     */
    data object MissingHolderPublicKey : KeyBindingError

    /**
     * Indicates that the public key of the holder, located in SD-JWT claims, is not supported
     */
    data object UnsupportedHolderPublicKey : KeyBindingError

    /**
     * SD-JWT contains an invalid Key Binding JWT
     */
    data class InvalidKeyBindingJwt(val message: String? = null, val cause: Throwable? = null) : KeyBindingError {
        constructor(message: String) : this(message = message, cause = null)
        constructor(cause: Throwable) : this(message = null, cause = cause)
    }

    /**
     * SD-JWT contains a Key Binding JWT, but this was not expected
     */
    data object UnexpectedKeyBindingJwt : KeyBindingError

    /**
     * SD-JWT lacks a Key Binding JWT, which was expected
     */
    data object MissingKeyBindingJwt : KeyBindingError
}

/**
 * This represents the two kinds of Key Binding verification
 *
 * [MustNotBePresent] : A [presentation SD-JWT][SdJwt] must not have a Key Binding
 * [mustBePresent]: A [presentation SD-JWT][SdJwt] must have a valid Key Binding
 */
sealed interface KeyBindingVerifier<out JWT> {

    /**
     * Indicates that a presentation SD-JWT must not have key binding
     */
    data object MustNotBePresent : KeyBindingVerifier<Nothing>

    /**
     * Indicates that a presentation SD-JWT must have key binding
     *
     * @param keyBindingVerifierProvider this is a function to extract of the JWT part of the SD-JWT,
     * the public key of the Holder and create [JwtSignatureVerifier] to be used for validating the
     * signature of the Key Binding JWT.
     * It assumes that Issuer has included somehow the holder pub key to SD-JWT.
     *
     */
    class MustBePresentAndValid<out JWT>(val keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier<JWT>?) :
        KeyBindingVerifier<JWT>

    companion object {

        fun <JWT> mustBePresent(verifier: JwtSignatureVerifier<JWT>): MustBePresentAndValid<JWT> =
            MustBePresentAndValid { _ -> verifier }
    }
}

fun <JWT, JWT1> KeyBindingVerifier<JWT>.map(f: (JWT) -> JWT1): KeyBindingVerifier<JWT1> =
    when (this) {
        is MustBePresentAndValid<JWT> -> MustBePresentAndValid { sdJwtClaims ->
            keyBindingVerifierProvider(sdJwtClaims)?.map { f(it) }
        }

        MustNotBePresent -> MustNotBePresent
    }

private fun interface KeyBindingVerifierOps<JWT> {
    /**
     * @param jwtClaims The claims of the JWT part of the SD-JWT. They will be used to extract the
     * public key of the Holder, in case of [MustBePresentAndValid]
     * @param expectedDigest The digest of the SD-JWT, as expected to be found inside the Key Binding JWT
     * under `sd_hash` claim.
     * It will be used in case of [MustBePresentAndValid]
     * @param unverifiedKbJwt the Key Binding JWT to be verified.
     * In case of [MustNotBePresent] it must not be provided.
     * Otherwise, in case of [MustBePresentAndValid], it must be present, having a valid signature and containing
     * at least an [expectedDigest] under claim `sd_hash`
     *
     * @return the claims of the Key Binding JWT, in case of [MustBePresentAndValid], otherwise null.
     */
    suspend fun KeyBindingVerifier<JWT>.verify(
        jwtClaims: JsonObject,
        expectedDigest: SdJwtDigest,
        unverifiedKbJwt: String?,
    ): Result<JWT?>

    companion object {

        operator fun <JWT> invoke(claimsOf: (JWT) -> JsonObject): KeyBindingVerifierOps<JWT> =
            KeyBindingVerifierOps { jwtClaims, expectedDigest, unverifiedKbJwt ->

                fun mustBeNotPresent(): JWT? =
                    if (unverifiedKbJwt != null) throw UnexpectedKeyBindingJwt.asException()
                    else null

                suspend fun mustBePresentAndValid(keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier<JWT>?): JWT {
                    if (unverifiedKbJwt == null) throw MissingKeyBindingJwt.asException()

                    val keyBindingJwtVerifier = keyBindingVerifierProvider(jwtClaims) ?: throw MissingHolderPublicKey.asException()
                    val keyBindingJwt = runCatchingCancellable {
                        requireNotNull(keyBindingJwtVerifier.checkSignature(unverifiedKbJwt)) {
                            "KeyBinding JWT cannot be null"
                        }
                    }.getOrElse { error -> throw InvalidKeyBindingJwt("Could not verify KeyBinding JWT", error).asException() }

                    val keyBindingJwtClaims = claimsOf(keyBindingJwt)
                    val sdHash = keyBindingJwtClaims[SdJwtSpec.CLAIM_SD_HASH]
                        ?.takeIf { element -> element is JsonPrimitive && element.isString }
                        ?.jsonPrimitive
                        ?.contentOrNull
                    if (expectedDigest.value != sdHash) throw InvalidKeyBindingJwt(
                        "${SdJwtSpec.CLAIM_SD_HASH} claim contains an invalid value",
                    ).asException()

                    return keyBindingJwt
                }
                runCatchingCancellable {
                    when (this) {
                        is MustNotBePresent -> mustBeNotPresent()
                        is MustBePresentAndValid -> mustBePresentAndValid(keyBindingVerifierProvider)
                    }
                }
            }
    }
}

internal fun KeyBindingError.asException(): SdJwtVerificationException =
    KeyBindingFailed(this).asException()

@Suppress("unused")
fun interface UnverifiedIssuanceFrom<out JWT> {
    /**
     * A method for obtaining an [SdJwt] given an [unverifiedSdJwt], without checking the signature
     * of the issuer.
     *
     * The method can be useful in case where a holder has previously [verified][SdJwtVerifier.verify] the SD-JWT and
     * wants to just re-obtain an instance of the [SdJwt] without repeating this verification
     *
     */
    fun unverifiedIssuanceFrom(unverifiedSdJwt: String): Result<SdJwt<JWT>>
}

/**
 * Representation of a JWT both as [string][Jwt] and its payload claims
 */
typealias JwtAndClaims = Pair<Jwt, JsonObject>

/**
 * A single point for verifying SD-JWTs in both SD-JWT and SD-JWT+KB formats, using either compact or
 * JWS JSON serialization.
 */
interface SdJwtVerifier<JWT> {

    /**
     * Verifies an SD-JWT serialized using compact serialization.
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT, or to Verifier that wants to verify
     * a presented SD-JWT in case the KB-JWT [must not be present][KeyBindingVerifier.MustNotBePresent].
     *
     * @param jwtSignatureVerifier the verification the SD-JWT signature.
     * To provide an implementation of this,
     * Holder should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.jwt] as both string and decoded payload
     */
    suspend fun verify(
        jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
        unverifiedSdJwt: String,
    ): Result<SdJwt<JWT>>

    /**
     * Verifies an SD-JWT serialized using JWS JSON serialization (either general or flattened format) as defined by RFC7515
     * and extended by SD-JWT specification.
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT, or to Verifier that wants to verify
     * a presented SD-JWT in case the KB-JWT [must not be present][KeyBindingVerifier.MustNotBePresent].
     *
     * @param jwtSignatureVerifier the verification the SD-JWT signature.
     * To provide an implementation of this,
     * Holder should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified.
     * A JSON Object that is expected to be in general
     * or flatten form as defined in RFC7515 and extended by SD-JWT specification.
     * @return the verified SD-JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.jwt] as both string and decoded payload
     */
    suspend fun verify(
        jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwt<JWT>> =
        verify(jwtSignatureVerifier, JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt))

    /**
     * Verifies a SD-JWT+KB serialized using compact serialization.
     *
     * Typically, this is useful to Verifiers that want to verify presentation SD-JWT communicated by Holders.
     *
     * @param jwtSignatureVerifier the verification of SD-JWT signature.
     * To provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param keyBindingVerifier the verification of the KeyBinding signature
     * Verifier should be aware of how the Issuer has chosen to include the
     * Holder public key into the SD-JWT and which algorithm the Holder used to sign the challenge of the Verifier.
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT and the KeyBinding JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.jwt] and KeyBinding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verify(
        jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
        keyBindingVerifier: MustBePresentAndValid<JWT>,
        unverifiedSdJwt: String,
    ): Result<SdJwtAndKbJwt<JWT>>

    /**
     * Verifies a SD-JWT+KB in JWS JSON serialization.
     *
     * Typically, this is useful to Verifiers that want to verify presentation SD-JWT communicated by Holders
     *
     * @param jwtSignatureVerifier the verification of SD-JWT signature.
     * To provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param keyBindingVerifier the verification of the KeyBinding signature
     * Verifier should be aware of how the Issuer has chosen to include the
     * Holder public key into the SD-JWT and which algorithm the Holder used to sign the challenge of the Verifier.
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT and KeyBinding JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.jwt] and KeyBinding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verify(
        jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
        keyBindingVerifier: MustBePresentAndValid<JWT>,
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwtAndKbJwt<JWT>> =
        verify(jwtSignatureVerifier, keyBindingVerifier, JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt))

    companion object {

        operator fun <JWT> invoke(claimsOf: (JWT) -> JsonObject): SdJwtVerifier<JWT> = object : SdJwtVerifier<JWT> {

            override suspend fun verify(
                jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
                unverifiedSdJwt: String,
            ): Result<SdJwt<JWT>> =
                doVerify(claimsOf, jwtSignatureVerifier, MustNotBePresent, unverifiedSdJwt)
                    .map { (sdJwt, kbJwt) ->
                        check(kbJwt == null) { "KeyBinding JWT is not expected" }
                        sdJwt
                    }

            override suspend fun verify(
                jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
                keyBindingVerifier: MustBePresentAndValid<JWT>,
                unverifiedSdJwt: String,
            ): Result<SdJwtAndKbJwt<JWT>> =
                doVerify(claimsOf, jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt)
                    .map { (sdJwt, kbJwt) ->
                        checkNotNull(kbJwt) { "KeyBinding JWT is expected" }
                        SdJwtAndKbJwt(sdJwt, kbJwt)
                    }
        }
    }
}

private suspend fun <JWT> doVerify(
    claimsOf: (JWT) -> JsonObject,
    jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
    keyBindingVerifier: KeyBindingVerifier<JWT>,
    unverifiedSdJwt: String,
): Result<Pair<SdJwt<JWT>, JWT?>> = runCatchingCancellable {
    // Parse
    val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) = StandardSerialization.parse(unverifiedSdJwt)

    // Check JWT
    val jwt = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
    val jwtClaims = claimsOf(jwt)
    val hashAlgorithm = jwtClaims.hashAlgorithm()
    val disclosures = toDisclosures(unverifiedDisclosures)
    val (_, _) = SdJwtRecreateClaimsOps.recreateClaimsAndDisclosuresPerClaim(jwtClaims, disclosures).getOrThrow()

    // Check Key binding
    val expectedDigest = SdJwtDigest.digest(hashAlgorithm, unverifiedSdJwt).getOrThrow()
    val kbJwt =
        with(KeyBindingVerifierOps(claimsOf)) {
            keyBindingVerifier.verify(jwtClaims, expectedDigest, unverifiedKBJwt).getOrThrow()
        }

    // Assemble it
    val sdJwt = SdJwt(jwt, disclosures)
    sdJwt to kbJwt
}

/**
 * Checks that the [unverifiedDisclosures] are indeed [Disclosure].
 *
 * @return the list of [Disclosure]. Otherwise, it may raise [InvalidDisclosures]
 */
internal fun toDisclosures(unverifiedDisclosures: List<String>): List<Disclosure> {
    val maybeDisclosures = unverifiedDisclosures.map { it to Disclosure.wrap(it) }
    val invalidDisclosures = maybeDisclosures.filter { (_, result) -> result.isFailure }
        .map { (invalidDisclosure, failure) ->
            val cause = failure.exceptionOrNull()!!.message ?: "unknown error occurred"
            cause to invalidDisclosure
        }
        .groupBy({ it.first }, { it.second })
    if (invalidDisclosures.isNotEmpty()) throw InvalidDisclosures(invalidDisclosures).asException()
    return maybeDisclosures.map { (_, result) -> result.getOrNull()!! }
}

internal fun JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt: JsonObject): String {
    val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) =
        parseJWSJson(unverifiedSdJwt)
    val jwtAndDisclosures = StandardSerialization.concat(unverifiedJwt, unverifiedDisclosures)
    val kbJwtSerialized = unverifiedKBJwt ?: ""
    return "$jwtAndDisclosures$kbJwtSerialized"
}
