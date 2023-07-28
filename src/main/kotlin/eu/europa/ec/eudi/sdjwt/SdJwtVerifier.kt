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
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.MustNotBePresent
import eu.europa.ec.eudi.sdjwt.SdJwtVerifier.verifyIssuance
import eu.europa.ec.eudi.sdjwt.SdJwtVerifier.verifyPresentation
import eu.europa.ec.eudi.sdjwt.VerificationError.*
import kotlinx.serialization.json.*
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Errors that may occur during SD-JWT verification
 *
 * They are raised as [SdJwtVerificationException]
 */
sealed interface VerificationError {

    /**
     * SD-JWT is not in Combined Issuance of Presentation format
     */
    object ParsingError : VerificationError {
        override fun toString(): String = "ParsingError"
    }

    /**
     * SD-JWT contains in invalid JWT
     */
    object InvalidJwt : VerificationError {
        override fun toString(): String = "InvalidJwt"
    }

    /**
     * Failure to verify holder binding
     * @param details the specific problem
     */
    data class KeyBindingFailed(val details: KeyBindingError) : VerificationError

    /**
     * SD-JWT contains invalid disclosures (cannot obtain a claim)
     */
    data class InvalidDisclosures(val invalidDisclosures: List<String>) : VerificationError

    /**
     * SD-JWT contains a JWT which is missing or contains an invalid
     * Hashing Algorithm claim
     */
    object MissingOrUnknownHashingAlgorithm : VerificationError {
        override fun toString(): String = "MissingOrUnknownHashingAlgorithm"
    }

    /**
     * SD-JWT contains non-unique disclosures
     */
    object NonUniqueDisclosures : VerificationError {
        override fun toString(): String = "NonUniqueDisclosures"
    }

    /**
     * SD-JWT contains a JWT which has non unique digests
     */
    object NonUniqueDisclosureDigests : VerificationError {
        override fun toString(): String = "NonUniqueDisclosureDigests"
    }

    /**
     * SD-JWT doesn't contain digests for the [disclosures]
     * @param disclosures The disclosures for which there are no digests
     */
    data class MissingDigests(val disclosures: List<Disclosure>) : VerificationError
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
fun interface JwtSignatureVerifier {

    /**
     * Verifies the signature of the [jwt] and extracts its payload
     * @param jwt the JWT to validate
     * @return the payload of the JWT if signature is valid, otherwise raises [InvalidJwt]
     */
    fun verify(jwt: String): Result<Claims> = runCatching { checkSignature(jwt) ?: throw InvalidJwt.asException() }

    /**
     * Implement this method to check the signature of the JWT and extract its payload
     * @param jwt the JWT to validate
     * @return the payload of the JWT if signature is valid, otherwise null
     */
    fun checkSignature(jwt: String): Claims?

    /**
     * Constructs a new [JwtSignatureVerifier] that in addition applies to the
     * extracted payload the [additionalCondition]
     *
     * @return a new [JwtSignatureVerifier] that in addition applies to the
     *  extracted payload the [additionalCondition]
     */
    fun and(additionalCondition: (Claims) -> Boolean): JwtSignatureVerifier = JwtSignatureVerifier { jwt ->
        this.checkSignature(jwt)?.let { claims -> if (additionalCondition(claims)) claims else null }
    }

    companion object
}

/**
 * Errors related to Holder Binding
 */
sealed interface KeyBindingError {

    /**
     * Indicates that the pub key of the holder cannot be located
     * in SD-JWT, JWT claims
     */
    object MissingHolderPubKey : KeyBindingError {
        override fun toString(): String = "MissingHolderPubKey"
    }

    /**
     * SD-JWT contains in invalid Key Binding JWT
     */
    object InvalidKeyBindingJwt : KeyBindingError {
        override fun toString(): String = "InvalidKeyBindingJwt"
    }

    /**
     * SD-JWT contains a Key Binding JWT, but this was not expected
     */
    object UnexpectedKeyBindingJwt : KeyBindingError {
        override fun toString(): String = "UnexpectedKeyBindingJwt"
    }

    /**
     * SD-JWT lacks a Key Binding JWT, which was expected
     */
    object MissingKeyBindingJwt : KeyBindingError {
        override fun toString(): String = "MissingKeyBindingJwt"
    }
}

/**
 * This represents the two kinds of Holder Binding verification
 *
 * [MustNotBePresent] : A [presentation SD-JWT][SdJwt.Presentation] must not have a [SdJwt.Presentation.keyBindingJwt]
 * [MustBePresent]: A [presentation SD-JWT][SdJwt.Presentation] must have a  valid [SdJwt.Presentation.keyBindingJwt]
 */
sealed interface KeyBindingVerifier {

    fun verify(jwtClaims: Claims, holderBindingJwt: String?): Result<Claims?> = runCatching {
        fun mustBeNotPresent(): Claims? =
            if (holderBindingJwt != null) throw UnexpectedKeyBindingJwt.asException()
            else null

        fun mustBePresentAndValid(holderBindingVerifierProvider: (Claims) -> JwtSignatureVerifier?): Claims {
            if (holderBindingJwt == null) throw MissingKeyBindingJwt.asException()
            val holderBindingJwtVerifier =
                holderBindingVerifierProvider(jwtClaims) ?: throw MissingHolderPubKey.asException()
            return holderBindingJwtVerifier.checkSignature(holderBindingJwt)
                ?: throw InvalidKeyBindingJwt.asException()
        }
        when (this) {
            is MustNotBePresent -> mustBeNotPresent()
            is MustBePresentAndValid -> mustBePresentAndValid(keyBindingVerifierProvider)
        }
    }

    /**
     * Indicates that a presentation SD-JWT must not have holder binding
     */
    object MustNotBePresent : KeyBindingVerifier

    /**
     * Indicates that a presentation SD-JWT must have holder binding
     *
     * @param keyBindingVerifierProvider this is a function to extract of the JWT part of the SD-JWT,
     * the public key of the Holder and create [JwtSignatureVerifier] to be used for validating the
     * signature of the Key Binding JWT.
     * It assumes that Issuer has included somehow the holder pub key to SD-JWT.
     *
     */
    class MustBePresentAndValid(val keyBindingVerifierProvider: (Claims) -> JwtSignatureVerifier?) :
        KeyBindingVerifier

    companion object {

        internal fun KeyBindingError.asException(): SdJwtVerificationException =
            KeyBindingFailed(this).asException()
    }
}

/**
 * Representation of a JWT both as [string][Jwt] and its [payload][Claims]
 */
typealias JwtAndClaims = Pair<Jwt, Claims>

/**
 * A single point for verifying SD-JWTs in both [Combined Issuance Format][verifyIssuance]
 * and [Combined Presentation Format][verifyPresentation]
 */
object SdJwtVerifier {

    /**
     * Verifies an SD-JWT in Combined Issuance Format
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT
     *
     * @param jwtSignatureVerifier the verification the SD-JWT signature.
     * In order to provide an implementation of this,
     * Holder should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: String,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
        // Parse
        val (unverifiedJwt, unverifiedDisclosures) = parseCombinedIssuance(unverifiedSdJwt)

        // Check JWT signature
        val jwtClaims = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
        val hashAlgorithm = hashingAlgorithmClaim(jwtClaims)
        val disclosures = uniqueDisclosures(unverifiedDisclosures)
        val digests = collectDigests(jwtClaims, disclosures)

        // Check Disclosures
        verifyDisclosures(hashAlgorithm, disclosures, digests)

        // Assemble it
        SdJwt.Issuance(unverifiedJwt to jwtClaims, disclosures)
    }

    /**
     * Verifies a SD-JWT in Combined Presentation Format
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param jwtSignatureVerifier the verification of SD-JWT signature.
     * In order to provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param keyBindingVerifier specifies whether a Holder Binding JWT is expected or not.
     * In the case that it is expected, Verifier should be aware of how the Issuer has chosen to include the
     * Holder public key into the SD-JWT and which algorithm the Holder used to sign the challenge of the Verifier,
     * in order to produce the Holder Binding JWT.
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and [holder binding JWT]
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier,
        keyBindingVerifier: KeyBindingVerifier,
        unverifiedSdJwt: String,
    ): Result<SdJwt.Presentation<JwtAndClaims, JwtAndClaims>> = runCatching {
        // Parse
        val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) =
            parseCombinedPresentation(unverifiedSdJwt)

        // Check JWT
        val jwtClaims = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
        val hashAlgorithm = hashingAlgorithmClaim(jwtClaims)
        val disclosures = uniqueDisclosures(unverifiedDisclosures)
        val digests = collectDigests(jwtClaims, disclosures)
        verifyDisclosures(hashAlgorithm, disclosures, digests)

        // Check Holder binding
        val kbClaims = keyBindingVerifier.verify(jwtClaims, unverifiedKBJwt).getOrThrow()

        // Assemble it
        val holderBindingJwtAndClaims = unverifiedKBJwt?.let { hb -> kbClaims?.let { cs -> hb to cs } }
        SdJwt.Presentation(unverifiedJwt to jwtClaims, disclosures, holderBindingJwtAndClaims)
    }

    /**
     * Verifies an SD-JWT, which is expected to be in envelope format, that is to contain
     * at least the  `iat`, `nonce`, `aud` and `_sd_jwt` claims.
     * If the verification succeeds, the returned SD-JWT will not have a key binding JWT
     * since the envelope acts like a proof of possession.
     *
     * @param envelopeJwtVerifier the verification of the envelope JWT signature.
     * In order to provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Holder
     * used to sign the envelope
     * @param clock the Verifier's clock. Will be used to validate iat claim of the envelope
     * @param iatOffset an offset that will be used to define a time window within which the iat claim
     * is considered valid
     * @param expectedAudience the expected content of the aud claim.
     * @param sdJwtSignatureVerifier the verification of the SD-JWT signature.
     * In order to provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param unverifiedEnvelopeJwt the JWT to verify.
     * Expected to be a valid JWT containing the `iat`, `nonce`, `aud` and `_sd_jwt` claims, at least
     * @return the presentation SD-JWT.If the verification succeeds, the SD-JWT will not have a key binding JWT
     * since the envelope acts like a proof of possession.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    fun verifyEnvelopedPresentation(
        sdJwtSignatureVerifier: JwtSignatureVerifier,
        envelopeJwtVerifier: JwtSignatureVerifier,
        clock: Clock,
        iatOffset: Duration,
        expectedAudience: String,
        unverifiedEnvelopeJwt: String,
    ): Result<SdJwt.Presentation<JwtAndClaims, Nothing>> = runCatching {
        fun isValid(claims: Claims): Boolean = with(ClaimValidations) {
            !claims.envelopSdJwt(clock, iatOffset, expectedAudience).isNullOrEmpty()
        }

        val claims = envelopeJwtVerifier
            .and { claims -> isValid(claims) }
            .verify(unverifiedEnvelopeJwt)
            .getOrThrow()
        val unverifiedSdJwt = claims["_sd_jwt"]!!.jsonPrimitive.contentOrNull!!
        verifyPresentation(sdJwtSignatureVerifier, MustNotBePresent, unverifiedSdJwt)
            .getOrThrow()
            .noKeyBinding()
    }
}

/**
 * Parses an SD-JWT, assuming Combined Issuance Format
 * @param unverifiedSdJwt the SD-JWT to be verified
 * @return the JWT and the list of disclosures (as string), or raises [ParsingError]
 * @throws SdJwtVerificationException with a [ParsingError] in case the given string cannot be parsed
 */
private fun parseCombinedIssuance(unverifiedSdJwt: String): Pair<Jwt, List<String>> {
    val parts = unverifiedSdJwt.split('~')
    if (parts.size <= 1) throw ParsingError.asException()
    val jwt = parts[0]
    val ds = parts.drop(1).filter { it.isNotBlank() }
    return jwt to ds
}

/**
 * Parses an SD-JWT, assuming Combined Presentation Format
 * @param unverifiedSdJwt the SD-JWT to be verified
 * @return the JWT and the list of disclosures and the Holder Binding JWT (as string), or raises [ParsingError]
 * @throws SdJwtVerificationException with a [ParsingError] in case the given string cannot be parsed
 */
private fun parseCombinedPresentation(unverifiedSdJwt: String): Triple<Jwt, List<String>, Jwt?> {
    val parts = unverifiedSdJwt.split('~')
    if (parts.size <= 1) throw ParsingError.asException()
    val jwt = parts[0]
    val containsKeyBinding = !unverifiedSdJwt.endsWith('~')
    val ds = parts.drop(1).run { if (containsKeyBinding) dropLast(1) else this }.filter { it.isNotBlank() }
    val hbJwt = if (containsKeyBinding) parts.last() else null
    return Triple(jwt, ds, hbJwt)
}

/**
 * Verify the [disclosures] against the [digestFoundInSdJwt] found in the SD-JWT and in particular
 * in the payload of the JWT and in the disclosures themselves.
 *
 *  For every disclosure, we make sure that there is a digest within the SD-JWT
 *
 * @param hashAlgorithm the algorithm to use when re-calculating the digests
 * @param disclosures the disclosures to verify
 * @param digestFoundInSdJwt the digests found in the SD-JWT and in particular in the payload of the JWT and in the
 * disclosures themselves.
 * @throws SdJwtVerificationException with [MissingDigests] error in case there are disclosures
 * for which there are no digests.
 */
private fun verifyDisclosures(
    hashAlgorithm: HashAlgorithm,
    disclosures: Set<Disclosure>,
    digestFoundInSdJwt: Set<DisclosureDigest>,
) {
    val calculatedDigestsPerDisclosure: Map<Disclosure, DisclosureDigest> =
        disclosures.associateWith { DisclosureDigest.digest(hashAlgorithm, it).getOrThrow() }

    val disclosuresMissingDigest = mutableListOf<Disclosure>()
    for ((disclosure, digest) in calculatedDigestsPerDisclosure) {
        if (digest !in digestFoundInSdJwt) {
            disclosuresMissingDigest.add(disclosure)
        }
    }
    if (disclosuresMissingDigest.isNotEmpty()) throw MissingDigests(disclosuresMissingDigest).asException()
}

/**
 * Checks that the [unverifiedDisclosures] are indeed [Disclosure] and that are unique
 * @return the set of [Disclosure]. Otherwise, it may raise [InvalidDisclosures] or [NonUniqueDisclosures]
 */
private fun uniqueDisclosures(unverifiedDisclosures: List<String>): Set<Disclosure> {
    val maybeDisclosures = unverifiedDisclosures.associateWith { Disclosure.wrap(it) }
    maybeDisclosures.filterValues { it.isFailure }.keys.also { invalidDisclosures ->
        if (invalidDisclosures.isNotEmpty())
            throw InvalidDisclosures(invalidDisclosures.toList()).asException()
    }
    return maybeDisclosures.values.map { it.getOrThrow() }.toSet().also { disclosures ->
        if (unverifiedDisclosures.size != disclosures.size) throw NonUniqueDisclosures.asException()
    }
}

/**
 * Looks in the provided claims for the hashing algorithm
 * @param jwtClaims the claims in the JWT part of the SD-jWT
 * @return the hashing algorithm, if a hashing algorithm is present and contains a string
 * representing a supported [HashAlgorithm]. Otherwise raises [MissingOrUnknownHashingAlgorithm]
 */
private fun hashingAlgorithmClaim(jwtClaims: Claims): HashAlgorithm {
    return jwtClaims["_sd_alg"]?.let { element ->
        if (element is JsonPrimitive) HashAlgorithm.fromString(element.content)
        else null
    } ?: throw MissingOrUnknownHashingAlgorithm.asException()
}

/**
 * Collects [digests][DisclosureDigest] from both the JWT payload and the [disclosures][Disclosure].
 * @param jwtClaims the JWT payload, of the SD-JWT
 * @param disclosures the disclosures, of the SD-JWT
 * @return digests from both the JWT payload and the [disclosures][Disclosure], assuring that they are unique
 */
private fun collectDigests(jwtClaims: Claims, disclosures: Set<Disclosure>): Set<DisclosureDigest> {
    // Get Digests
    val jwtDigests = collectDigests(jwtClaims)
    val disclosureDigests = disclosures.map { d -> collectDigests(JsonObject(mapOf(d.claim()))) }.flatten()
    val digests = jwtDigests + disclosureDigests
    val uniqueDigests = digests.toSet()
    if (uniqueDigests.size != digests.size) throw NonUniqueDisclosureDigests.asException()
    return uniqueDigests
}

/**
 * Extracts all the [digests][DisclosureDigest] from the given [claims],
 * including also subclaims
 *
 * @param claims to use
 * @return the digests found in the given [claims]
 */
internal fun collectDigests(claims: Claims): List<DisclosureDigest> {
    fun digestsOf(attribute: String, json: JsonElement): List<DisclosureDigest> =
        when {
            attribute == "_sd" && json is JsonArray -> json.mapNotNull { element ->
                if (element is JsonPrimitive) DisclosureDigest.wrap(element.content).getOrNull()
                else null
            }

            attribute == "..." && json is JsonPrimitive ->
                DisclosureDigest.wrap(json.content).getOrNull()?.let { listOf(it) } ?: emptyList()

            json is JsonObject -> collectDigests(json)
            json is JsonArray -> json.map { if (it is JsonObject) collectDigests(it) else emptyList() }.flatten()
            else -> emptyList()
        }
    return claims.map { (attribute, json) -> digestsOf(attribute, json) }.flatten()
}

/**
 * Validations for the contents of an envelope JWT
 */
object ClaimValidations {

    /**
     * Retrieves the contents of the _sd_jwt claim, when all the following conditions are being met
     * - _sd_jwt claim is present
     * - nonce claim is present
     * - iat is present, and valid
     * - aud claim contains an expected value
     *
     * @receiver the claims to check
     * @return the _sd_jwt claim
     */
    fun Claims.envelopSdJwt(clock: Clock, iatOffset: Duration, expectedAudience: String): String? =
        primitiveClaim("_sd_jwt")?.contentOrNull?.let { sdJwt ->
            fun validAud() = aud().contains(expectedAudience)
            fun validIat() = iat(clock, iatOffset) != null
            fun hasNonce() = !nonce().isNullOrEmpty()
            sdJwt.takeIf { validAud() && validIat() && hasNonce() }
        }

    /**
     * Retrieves the aud claim
     *
     * @receiver the claims to check
     * @return the aud claim
     */
    fun Claims.aud(): List<String> =
        when (val audElement = get("aud")) {
            is JsonPrimitive -> audElement.contentOrNull?.let { listOf(it) } ?: emptyList()
            is JsonArray -> audElement.mapNotNull {
                if (it is JsonPrimitive) it.contentOrNull else null
            }

            else -> emptyList()
        }

    /**
     * Retrieves the iat claim, if present and within the provided time window.
     * The time window will be calculated by obtaining the [current time][Clock.instant]
     * and the [offset].
     * That is, iat less than equal to the clock's current time and not before the current time minus the offset
     *
     * @param clock the clock to use
     * @param offset a time window within which the iat is expecting
     * @receiver the claims to check
     * @return the iat claim
     */
    fun Claims.iat(clock: Clock, offset: Duration): Instant? =
        primitiveClaim("iat")?.longOrNull?.let { iatValue ->
            val iat = Instant.ofEpochSecond(iatValue)
            val now = clock.instant()
            iat.takeIf { (iat >= now.minusSeconds(offset.seconds) && iat <= now) }
        }

    fun Claims.nonce(): String? = primitiveClaim("nonce")?.contentOrNull

    fun Claims.primitiveClaim(name: String): JsonPrimitive? =
        get(name)?.let { element -> if (element is JsonPrimitive) element else null }
}
