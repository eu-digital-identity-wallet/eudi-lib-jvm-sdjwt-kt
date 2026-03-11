/*
 * Copyright (c) 2023-2026 European Commission
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
import kotlinx.serialization.json.*
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
     * @param challenge Challenge for verifying the validity of the Key-Binding JWT.
     * Will be used in when [this] is [MustBePresentAndValid]
     * @param unverifiedKbJwt the Key Binding JWT to be verified.
     * In case of [MustNotBePresent] it must not be provided.
     * Otherwise, in case of [MustBePresentAndValid], it must be present, having a valid signature
     * and being valid per the [challenge]
     *
     * @return the claims of the Key Binding JWT, in case of [MustBePresentAndValid], otherwise null.
     */
    suspend fun KeyBindingVerifier<JWT>.verify(
        jwtClaims: JsonObject,
        expectedDigest: SdJwtDigest,
        challenge: ChallengePredicate?,
        unverifiedKbJwt: String?,
    ): Result<JWT?>

    companion object {

        operator fun <JWT> invoke(claimsOf: (JWT) -> JsonObject): KeyBindingVerifierOps<JWT> =
            KeyBindingVerifierOps { jwtClaims, expectedDigest, challenge, unverifiedKbJwt ->

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

                    val keyBindingJwtClaims = KeyBindingJwtClaims(claimsOf(keyBindingJwt))
                    if (expectedDigest.value != keyBindingJwtClaims.sdHash) {
                        throw InvalidKeyBindingJwt("${RFC9901.CLAIM_SD_HASH} claim contains an invalid value").asException()
                    }
                    if (null != challenge && (keyBindingJwtClaims.iat !in challenge.issuedAt)) {
                        throw InvalidKeyBindingJwt("'${RFC7519.ISSUED_AT}' is not withing the acceptable time window").asException()
                    }

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

private fun JsonObject.iat(): Instant? =
    this[RFC7519.ISSUED_AT]
        ?.let {
            check(it is JsonPrimitive && !it.isString) {
                "'${RFC7519.ISSUED_AT}' claim must be a number"
            }
            Instant.fromEpochSeconds(it.content.toLong())
        }

private fun JsonElement.isString(): Boolean {
    contract {
        returns(true) implies (this@isString is JsonPrimitive)
    }
    return this is JsonPrimitive && isString
}

private fun JsonObject.aud(): String? =
    this[RFC7519.AUDIENCE]
        ?.let {
            check(it.isString()) {
                "'${RFC7519.AUDIENCE}' claim must be a string"
            }
            it.content
        }

private fun JsonObject.nonce(): String? =
    this[RFC9901.CLAIM_NONCE]
        ?.let {
            check(it.isString()) {
                "'${RFC9901.CLAIM_NONCE}' claim must be a string"
            }
            it.content
        }

private fun JsonObject.sdHash(): String? =
    this[RFC9901.CLAIM_SD_HASH]
        ?.let {
            check(it.isString()) {
                "'${RFC9901.CLAIM_SD_HASH}' claim must be a string"
            }
            it.content
        }

/**
 * Τhe claims of a Key-Binding JWT.
 */
@JvmInline
private value class KeyBindingJwtClaims private constructor(val value: JsonObject) : Map<String, JsonElement> by value {

    val sdHash: String get() = checkNotNull(value.sdHash())
    val iat: Instant get() = checkNotNull(value.iat())
    val audience: String get() = checkNotNull(value.aud())
    val nonce: String get() = checkNotNull(value.nonce())

    companion object {
        /**
         * Creates a new instance and verifies the claims required per RFC9901 are present.
         *
         * The following claims are required to be present:
         * 1. sd_hash
         * 2. iat
         * 3. aud
         * 4. nonce
         *
         * @throws SdJwtVerificationException in case any of the required claims is missing
         */
        operator fun invoke(value: JsonObject): KeyBindingJwtClaims {
            if (null == value.sdHash()) throw InvalidKeyBindingJwt("'${RFC9901.CLAIM_SD_HASH}' claim is missing").asException()
            if (null == value.iat()) throw InvalidKeyBindingJwt("'${RFC7519.ISSUED_AT}' claim is missing").asException()
            if (null == value.aud()) throw InvalidKeyBindingJwt("'${RFC7519.AUDIENCE}' claim is missing").asException()
            if (null == value.nonce()) throw InvalidKeyBindingJwt("'${RFC9901.CLAIM_NONCE}' claim is missing").asException()
            return KeyBindingJwtClaims(value)
        }
    }
}

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
     * @param challenge Challenge for verifying the validity of the Key-Binding JWT.
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
        challenge: ChallengePredicate?,
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
     * @param challenge Challenge for verifying the validity of the Key-Binding JWT.
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
        challenge: ChallengePredicate?,
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwtAndKbJwt<JWT>> =
        verify(
            jwtSignatureVerifier,
            keyBindingVerifier,
            challenge,
            JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt),
        )

    companion object {

        operator fun <JWT> invoke(clock: Clock, skew: Duration?, claimsOf: (JWT) -> JsonObject): SdJwtVerifier<JWT> {
            if (null != skew) {
                require(!skew.isNegative()) { "skew cannot be negative" }
            }

            return object : SdJwtVerifier<JWT> {
                override suspend fun verify(
                    jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
                    unverifiedSdJwt: String,
                ): Result<SdJwt<JWT>> =
                    doVerify(claimsOf, jwtSignatureVerifier, MustNotBePresent, clock, skew ?: Duration.ZERO, null, unverifiedSdJwt)
                        .map { (sdJwt, kbJwt) ->
                            check(kbJwt == null) { "KeyBinding JWT is not expected" }
                            sdJwt
                        }

                override suspend fun verify(
                    jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
                    keyBindingVerifier: MustBePresentAndValid<JWT>,
                    challenge: ChallengePredicate?,
                    unverifiedSdJwt: String,
                ): Result<SdJwtAndKbJwt<JWT>> =
                    doVerify(claimsOf, jwtSignatureVerifier, keyBindingVerifier, clock, skew ?: Duration.ZERO, challenge, unverifiedSdJwt)
                        .map { (sdJwt, kbJwt) ->
                            checkNotNull(kbJwt) { "KeyBinding JWT is expected" }
                            SdJwtAndKbJwt(sdJwt, kbJwt)
                        }
            }
        }
    }
}

/**
 * Challenge for Key-Binding JWT.
 *
 * @property issuedAt window within which `iat` of the Key-Binding JWT is considered valid
 * @property exactMatchClaims other claims that must be exactly matched in the Key-Binding JWT claims-set
 */
data class ChallengePredicate private constructor(
    internal val issuedAt: ClosedRange<Instant>,
    internal val exactMatchClaims: JsonObject,
) {
    init {
        require(!issuedAt.isEmpty()) { "issuedAt must not be an empty range" }
    }

    companion object {
        /**
         * Creates a new [ChallengePredicate] instance.
         *
         * @param issuedAt expected `iat` of the Key-Binding JWT
         * @param audience expected `aud` of the Key-Binding JWT
         * @param nonce expected `nonce` of the Key-Binding JWT
         * @param skew **positive** duration, if provided defines a time window with [iat] within which `iat` of Key-Binding JWT is considered valid
         * @param exactMatchClaimsBuilder builder for other claims that must be exactly matched withing the Key-Binding JWT claims-set
         */
        operator fun invoke(
            issuedAt: Instant,
            audience: String,
            nonce: String,
            skew: Duration = 5.seconds,
            exactMatchClaimsBuilder: JsonObjectBuilder.() -> Unit = {},
        ): ChallengePredicate {
            require(!skew.isNegative()) { "skew cannot be negative" }
            val exactMatchClaims =
                buildJsonObject {
                    exactMatchClaimsBuilder()
                    put(RFC7519.AUDIENCE, audience)
                    put(RFC9901.CLAIM_NONCE, nonce)
                }.filterKeys {
                    it !in setOf(RFC7519.ISSUED_AT, RFC9901.CLAIM_SD_HASH)
                }.let {
                    JsonObject(it)
                }
            val issuedAt = (issuedAt - skew).withSecondsPrecision()..(issuedAt + skew).withSecondsPrecision()
            return ChallengePredicate(issuedAt, exactMatchClaims)
        }
    }
}

private suspend fun <JWT> doVerify(
    claimsOf: (JWT) -> JsonObject,
    jwtSignatureVerifier: JwtSignatureVerifier<JWT>,
    keyBindingVerifier: KeyBindingVerifier<JWT>,
    clock: Clock,
    skew: Duration,
    challenge: ChallengePredicate?,
    unverifiedSdJwt: String,
): Result<Pair<SdJwt<JWT>, JWT?>> = runCatchingCancellable {
    // Parse
    val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) = StandardSerialization.parse(unverifiedSdJwt)

    // Check JWT
    val jwt = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
    val jwtClaims = claimsOf(jwt)
    val hashAlgorithm = jwtClaims.hashAlgorithm()
    val disclosures = toDisclosures(unverifiedDisclosures)
    val (recreated, _) = SdJwtRecreateClaimsOps.recreateClaimsAndDisclosuresPerClaim(jwtClaims, disclosures).getOrThrow()

    // Verify validity of SD-JWT (checks `nbf`, and `exp`)
    recreated.validate(clock, skew)

    // Check Key binding
    val expectedDigest = SdJwtDigest.digest(hashAlgorithm, unverifiedSdJwt).getOrThrow()
    val kbJwt =
        with(KeyBindingVerifierOps(claimsOf)) {
            keyBindingVerifier.verify(jwtClaims, expectedDigest, challenge, unverifiedKBJwt).getOrThrow()
        }

    // Assemble it
    val sdJwt = SdJwt(jwt, disclosures)
    sdJwt to kbJwt
}

private fun JsonElement.isLong(): Boolean {
    contract {
        returns(true) implies (this@isLong is JsonPrimitive)
    }
    return this is JsonPrimitive && null != longOrNull
}

private fun JsonObject.nbf(): Instant? =
    this[RFC7519.NOT_BEFORE]
        ?.let {
            check(it.isLong()) {
                "'${RFC7519.NOT_BEFORE}' claim must be a number"
            }
            Instant.fromEpochSeconds(it.content.toLong())
        }

private fun JsonObject.exp(): Instant? =
    this[RFC7519.EXPIRATION_TIME]
        ?.let {
            check(it.isLong()) {
                "'${RFC7519.EXPIRATION_TIME}' claim must be a number"
            }
            Instant.fromEpochSeconds(it.content.toLong())
        }

/**
 * Returns a new [Instant] that has seconds precision.
 */
private fun Instant.withSecondsPrecision(): Instant = Instant.fromEpochSeconds(epochSeconds, 0)

private fun JsonObject.validate(clock: Clock, skew: Duration) {
    val now = clock.now().withSecondsPrecision()
    val nbf = nbf()
    if (null != nbf) {
        if ((nbf - skew) > now) {
            throw InvalidJwt(("SD-JWT is not active yet")).asException()
        }
    }

    val exp = exp()
    if (null != exp) {
        if (now >= (exp + skew)) {
            throw InvalidJwt(("SD-JWT is expired")).asException()
        }
    }
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
