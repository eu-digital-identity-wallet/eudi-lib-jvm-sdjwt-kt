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
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.Companion.MustBePresent
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier.Companion.asException
import eu.europa.ec.eudi.sdjwt.SdJwtVerifier.verifyIssuance
import eu.europa.ec.eudi.sdjwt.SdJwtVerifier.verifyPresentation
import eu.europa.ec.eudi.sdjwt.VerificationError.*
import kotlinx.serialization.json.*

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
     * SD-JWT contains in invalid JWT
     */
    data object InvalidJwt : VerificationError

    /**
     * Failure to verify key binding
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
    data object MissingOrUnknownHashingAlgorithm : VerificationError

    /**
     * SD-JWT contains non-unique disclosures
     */
    data object NonUniqueDisclosures : VerificationError

    /**
     * SD-JWT contains a JWT which has non unique digests
     */
    data object NonUniqueDisclosureDigests : VerificationError

    /**
     * SD-JWT doesn't contain digests for the [disclosures]
     * @param disclosures The disclosures for which there are no digests
     */
    data class MissingDigests(val disclosures: List<Disclosure>) : VerificationError

    @JvmInline
    value class Other(val value: String) : VerificationError {
        override fun toString(): String = value
    }
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
    suspend fun verify(jwt: String): Result<JsonObject> =
        runCatching {
            checkSignature(jwt) ?: throw InvalidJwt.asException()
        }

    /**
     * Implement this method to check the signature of the JWT and extract its payload
     * @param jwt the JWT to validate
     * @return the payload of the JWT if signature is valid, otherwise null
     */
    suspend fun checkSignature(jwt: String): JsonObject?

    /**
     * Constructs a new [JwtSignatureVerifier] that in addition applies to the
     * extracted payload the [additionalCondition]
     *
     * @return a new [JwtSignatureVerifier] that in addition applies to the
     *  extracted payload the [additionalCondition]
     */
    fun and(additionalCondition: suspend (JsonObject) -> Boolean): JwtSignatureVerifier = JwtSignatureVerifier { jwt ->
        this.checkSignature(jwt)?.let { claims -> if (additionalCondition(claims)) claims else null }
    }

    companion object {

        /**
         * A [JwtSignatureVerifier] is added to the companion object, which just checks/parses the JWT,
         * without performing signature validation.
         *
         * <em>Should not be used in production use cases</em>
         */
        val NoSignatureValidation: JwtSignatureVerifier = PlatformJwtSignatureVerifierNoSignatureValidation
    }
}

/**
 * Errors related to Key Binding
 */
sealed interface KeyBindingError {

    /**
     * Indicates that the pub key of the holder cannot be located
     * in SD-JWT, JWT claims
     */
    data object MissingHolderPubKey : KeyBindingError

    /**
     * SD-JWT contains in invalid Key Binding JWT
     */
    data object InvalidKeyBindingJwt : KeyBindingError

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
 * [MustNotBePresent] : A [presentation SD-JWT][SdJwt.Presentation] must not have a Key Binding
 * [MustBePresent]: A [presentation SD-JWT][SdJwt.Presentation] must have a valid Key Binding
 */
sealed interface KeyBindingVerifier {

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
    suspend fun verify(
        jwtClaims: JsonObject,
        expectedDigest: SdJwtDigest,
        unverifiedKbJwt: String?,
    ): Result<JsonObject?> =
        runCatching {
            fun mustBeNotPresent(): JsonObject? =
                if (unverifiedKbJwt != null) throw UnexpectedKeyBindingJwt.asException()
                else null

            suspend fun mustBePresentAndValid(keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier?): JsonObject {
                if (unverifiedKbJwt == null) throw MissingKeyBindingJwt.asException()

                val keyBindingJwtVerifier =
                    keyBindingVerifierProvider(jwtClaims) ?: throw MissingHolderPubKey.asException()

                return keyBindingJwtVerifier.checkSignature(unverifiedKbJwt)
                    ?.takeIf { kbClaims ->
                        val sdHash = kbClaims[SdJwtSpec.CLAIM_SD_HASH]
                            ?.takeIf { element -> element is JsonPrimitive && element.isString }
                            ?.jsonPrimitive
                            ?.contentOrNull
                        expectedDigest.value == sdHash
                    }
                    ?: throw InvalidKeyBindingJwt.asException()
            }
            when (this) {
                is MustNotBePresent -> mustBeNotPresent()
                is MustBePresentAndValid -> mustBePresentAndValid(keyBindingVerifierProvider)
            }
        }

    /**
     * Indicates that a presentation SD-JWT must not have key binding
     */
    data object MustNotBePresent : KeyBindingVerifier

    /**
     * Indicates that a presentation SD-JWT must have key binding
     *
     * @param keyBindingVerifierProvider this is a function to extract of the JWT part of the SD-JWT,
     * the public key of the Holder and create [JwtSignatureVerifier] to be used for validating the
     * signature of the Key Binding JWT.
     * It assumes that Issuer has included somehow the holder pub key to SD-JWT.
     *
     */
    class MustBePresentAndValid(val keyBindingVerifierProvider: (JsonObject) -> JwtSignatureVerifier?) :
        KeyBindingVerifier

    companion object {
        /**
         * Declares a [KeyBindingVerifier] that just makes sure that the Key Binding JWT is present, and it's indeed a JWT
         * without performing signature validation
         *
         * <em>Should not be used in production</em>
         */
        val MustBePresent: MustBePresentAndValid by lazy {
            MustBePresentAndValid { JwtSignatureVerifier.NoSignatureValidation }
        }

        internal fun KeyBindingError.asException(): SdJwtVerificationException =
            KeyBindingFailed(this).asException()
    }
}

/**
 * Representation of a JWT both as [string][Jwt] and its payload claims
 */
typealias JwtAndClaims = Pair<Jwt, JsonObject>

/**
 * A single point for verifying SD-JWTs in both [Combined Issuance Format][verifyIssuance]
 * and [Combined Presentation Format][verifyPresentation]
 */
object SdJwtVerifier {

    /**
     * Verifies an SD-JWT (in simple format)
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT
     *
     * @param jwtSignatureVerifier the verification the SD-JWT signature.
     * To provide an implementation of this,
     * Holder should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    suspend fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: String,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
        // Parse
        val (unverifiedJwt, unverifiedDisclosures) = StandardSerialization.parseIssuance(unverifiedSdJwt)
        verifyIssuance(jwtSignatureVerifier, unverifiedJwt, unverifiedDisclosures).getOrThrow()
    }

    /**
     * Verifies an SD-JWT in JWS JSON general of flattened format as defined by RFC7515 and extended by SD-JWT
     * specification
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT
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
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    suspend fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
        val (unverifiedJwt, unverifiedDisclosures, unverifiedKbJwt) =
            JwsJsonSupport.parseJWSJson(unverifiedSdJwt)
        if (null != unverifiedKbJwt) throw UnexpectedKeyBindingJwt.asException()
        verifyIssuance(jwtSignatureVerifier, unverifiedJwt, unverifiedDisclosures).getOrThrow()
    }

    /**
     * Implementation of the verification for an issued SD-JWT which is independent of the serialization
     * format used.
     *
     * @param jwtSignatureVerifier the verification the SD-JWT signature.
     * To provide an implementation of this,
     * Holder should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT
     * @param unverifiedJwt the JWT of the SD-JWT
     * @param unverifiedDisclosures the disclosures of the SD-JWT
     * @return the verified SD-JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    private suspend fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier,
        unverifiedJwt: Jwt,
        unverifiedDisclosures: List<String>,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
        // Check JWT signature
        val jwtClaims = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
        verifyIssuance(unverifiedJwt, unverifiedDisclosures) { jwtClaims }.getOrThrow()
    }

    /**
     * Verifies a SD-JWT in Combined Presentation Format
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param jwtSignatureVerifier the verification of SD-JWT signature.
     * To provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param keyBindingVerifier specifies whether a Key Binding JWT is expected or not.
     * In the case that it is expected, Verifier should be aware of how the Issuer has chosen to include the
     * Holder public key into the SD-JWT and which algorithm the Holder used to sign the challenge of the Verifier.
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT and the key binding JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and key binding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier,
        keyBindingVerifier: KeyBindingVerifier,
        unverifiedSdJwt: String,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> = runCatching {
        // Parse
        val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) =
            StandardSerialization.parse(unverifiedSdJwt)
        // Check JWT
        val jwtClaims = jwtSignatureVerifier.verify(unverifiedJwt).getOrThrow()
        val hashAlgorithm = hashingAlgorithmClaim(jwtClaims)
        val disclosures = uniqueDisclosures(unverifiedDisclosures)
        val digests = collectDigests(jwtClaims, disclosures)
        verifyDisclosures(hashAlgorithm, disclosures, digests)

        // Check Key binding
        val expectedDigest = SdJwtDigest.digest(hashAlgorithm, unverifiedSdJwt).getOrThrow()
        val kbJwtClaims = keyBindingVerifier.verify(jwtClaims, expectedDigest, unverifiedKBJwt).getOrThrow()

        // Assemble it
        val kbJwt: JwtAndClaims? = kbJwtClaims?.let { checkNotNull(unverifiedKBJwt) to it }
        val sdJwt = SdJwt.Presentation(unverifiedJwt to jwtClaims, disclosures)
        sdJwt to kbJwt
    }

    /**
     * Verifies a SD-JWT in JWS JSON serialization
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param jwtSignatureVerifier the verification of SD-JWT signature.
     * To provide an implementation of this,
     * Verifier should be aware of the public key and the signing algorithm that the Issuer
     * used to sign the SD-JWT.
     * @param keyBindingVerifier specifies whether a Key Binding JWT is expected or not.
     * In the case that it is expected, Verifier should be aware of how the Issuer has chosen to include the
     * Holder public key into the SD-JWT and which algorithm the Holder used to sign the challenge of the Verifier.
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and key binding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier,
        keyBindingVerifier: KeyBindingVerifier,
        unverifiedSdJwt: JsonObject,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> = runCatching {
        // Parse and re-assemble it in combined form
        val unverifiedSdJwtAsString = JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt)
        verifyPresentation(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwtAsString).getOrThrow()
    }
}

internal fun verifyIssuance(
    unverifiedJwt: Jwt,
    unverifiedDisclosures: List<String>,
    jwtClaimsExtractor: (Jwt) -> JsonObject,
): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
    val jwtClaims = jwtClaimsExtractor(unverifiedJwt)
    val hashAlgorithm = hashingAlgorithmClaim(jwtClaims)
    val disclosures = uniqueDisclosures(unverifiedDisclosures)
    val digests = collectDigests(jwtClaims, disclosures)

    // Check Disclosures
    verifyDisclosures(hashAlgorithm, disclosures, digests)

    // Assemble it
    SdJwt.Issuance(unverifiedJwt to jwtClaims, disclosures)
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
    disclosures: List<Disclosure>,
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
 * @return the list of [Disclosure]. Otherwise, it may raise [InvalidDisclosures] or [NonUniqueDisclosures]
 */
private fun uniqueDisclosures(unverifiedDisclosures: List<String>): List<Disclosure> {
    val maybeDisclosures = unverifiedDisclosures.associateWith { Disclosure.wrap(it) }
    maybeDisclosures.filterValues { it.isFailure }.keys.also { invalidDisclosures ->
        if (invalidDisclosures.isNotEmpty())
            throw InvalidDisclosures(invalidDisclosures.toList()).asException()
    }
    return unverifiedDisclosures.map { maybeDisclosures[it]!!.getOrThrow() }.also { disclosures ->
        if (maybeDisclosures.keys.size != disclosures.size) throw NonUniqueDisclosures.asException()
    }
}

/**
 * Looks in the provided claims for the hashing algorithm
 * @param jwtClaims the claims in the JWT part of the SD-jWT
 * @return the hashing algorithm, if a hashing algorithm is present and contains a string
 * representing a supported [HashAlgorithm]. Otherwise raises [MissingOrUnknownHashingAlgorithm]
 */
private fun hashingAlgorithmClaim(jwtClaims: JsonObject): HashAlgorithm {
    val element = jwtClaims[SdJwtSpec.CLAIM_SD_ALG] ?: JsonPrimitive("sha-256")
    val alg =
        if (element is JsonPrimitive) HashAlgorithm.fromString(element.content)
        else null
    return alg ?: throw MissingOrUnknownHashingAlgorithm.asException()
}

/**
 * Collects [digests][DisclosureDigest] from both the JWT payload and the [disclosures][Disclosure].
 * @param jwtClaims the JWT payload, of the SD-JWT
 * @param disclosures the disclosures, of the SD-JWT
 * @return digests from both the JWT payload and the [disclosures][Disclosure], assuring that they are unique
 */
private fun collectDigests(jwtClaims: JsonObject, disclosures: List<Disclosure>): Set<DisclosureDigest> {
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
internal fun collectDigests(claims: JsonObject): List<DisclosureDigest> {
    fun digestsOf(attribute: String, json: JsonElement): List<DisclosureDigest> =
        when {
            attribute == SdJwtSpec.CLAIM_SD && json is JsonArray -> json.mapNotNull { element ->
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

internal fun JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt: JsonObject): String {
    val (unverifiedJwt, unverifiedDisclosures, unverifiedKBJwt) =
        parseJWSJson(unverifiedSdJwt)
    val jwtAndDisclosures = StandardSerialization.concat(unverifiedJwt, unverifiedDisclosures)
    val kbJwtSerialized = unverifiedKBJwt ?: ""
    return "$jwtAndDisclosures$kbJwtSerialized"
}
