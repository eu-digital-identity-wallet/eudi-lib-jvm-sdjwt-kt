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

import eu.europa.ec.eudi.sdjwt.HolderBindingError.*
import eu.europa.ec.eudi.sdjwt.HolderBindingVerifier.Companion.MustBePresent
import eu.europa.ec.eudi.sdjwt.HolderBindingVerifier.MustNotBePresent
import eu.europa.ec.eudi.sdjwt.VerificationError.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    data class HolderBindingFailed(val details: HolderBindingError) : VerificationError

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
    object NonUnqueDisclosures : VerificationError {
        override fun toString(): String = "NonUnqueDisclosures"
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

    companion object {
        val NoSignatureValidation: JwtSignatureVerifier by lazy { noSignatureValidation() }
    }
}

/**
 * Errors related to Holder Binding
 */
sealed interface HolderBindingError {

    /**
     * Indicates that the pub key of the holder cannot be located
     * in SD-JWT, JWT claims
     */
    object MissingHolderPubKey : HolderBindingError {
        override fun toString(): String = "MissingHolderPubKey"
    }

    /**
     * SD-JWT contains in invalid Holder Binding JWT
     */
    object InvalidHolderBindingJwt : HolderBindingError {
        override fun toString(): String = "InvalidHolderBindingJwt"
    }

    /**
     * SD-JWT contains a Holder Binding JWT, but this was not expected
     */
    object UnexpectedHolderBindingJwt : HolderBindingError {
        override fun toString(): String = "UnexpectedHolderBindingJwt"
    }

    /**
     * SD-JWT lacks a Holder Binding JWT, which was expected
     */
    object MissingHolderBindingJwt : HolderBindingError {
        override fun toString(): String = "MissingHolderBindingJwt"
    }
}

/**
 * This represents the two kinds of Holder Binding verification
 *
 * [MustNotBePresent] : A [presentation SD-JWT][SdJwt.Presentation] must not have a [SdJwt.Presentation.holderBindingJwt]
 * [MustBePresent]: A [presentation SD-JWT][SdJwt.Presentation] must have a  valid [SdJwt.Presentation.holderBindingJwt]
 */
sealed interface HolderBindingVerifier {

    fun verify(jwtClaims: Claims, holderBindingJwt: String?): Result<Claims?> = runCatching {
        when (this) {
            is MustNotBePresent -> if (holderBindingJwt != null) throw UnexpectedHolderBindingJwt.asException() else null
            is MustBePresentAndValid ->
                if (holderBindingJwt != null) {
                    when (val holderBindingJwtVerifier = holderBindingVerifierProvider(jwtClaims)) {
                        null -> throw MissingHolderPubKey.asException()
                        else -> holderBindingJwtVerifier.checkSignature(holderBindingJwt)
                            ?: throw InvalidHolderBindingJwt.asException()
                    }
                } else throw MissingHolderBindingJwt.asException()
        }
    }

    /**
     * Indicates that a presentation SD-JWT must not have holder binding
     */
    object MustNotBePresent : HolderBindingVerifier

    /**
     * Indicates that a presentation SD-JWT must have holder binding
     *
     * @param holderBindingVerifierProvider this is a function to extract of the JWT part of the SD-JWT,
     * the public key of the Holder and create [JwtSignatureVerifier] to be used for validating the
     * signature of the Holder Binding JWT. It assumes, that Issuer has included somehow the holder pub key
     * to SD-JWT.
     *
     */
    class MustBePresentAndValid(val holderBindingVerifierProvider: (Claims) -> JwtSignatureVerifier?) :
        HolderBindingVerifier

    companion object {
        /**
         * Indicates that  presentation SD-JWT must have holder binding. Νο signature validation is performed
         */
        val MustBePresent: MustBePresentAndValid = MustBePresentAndValid { JwtSignatureVerifier.NoSignatureValidation }
        private fun HolderBindingError.asException(): SdJwtVerificationException =
            HolderBindingFailed(this).asException()
    }
}

typealias JwtAndClaims = Pair<Jwt, Claims>

object SdJwtVerifier {

    fun verifyIssuance(
        jwtSignatureVerifier: JwtSignatureVerifier,
        sdJwt: String,
    ): Result<SdJwt.Issuance<JwtAndClaims>> =
        SdJwtIssuanceVerifier(jwtSignatureVerifier).verify(sdJwt)

    fun verifyPresentation(
        jwtSignatureVerifier: JwtSignatureVerifier,
        holderBindingVerifier: HolderBindingVerifier,
        sdJwt: String,
    ): Result<SdJwt.Presentation<JwtAndClaims, JwtAndClaims>> =
        SdJwtPresentationVerifier(jwtSignatureVerifier, holderBindingVerifier).verify(sdJwt)
}

internal class SdJwtIssuanceVerifier(
    private val jwtSignatureVerifier: JwtSignatureVerifier,
) {

    fun verify(sdJwt: String): Result<SdJwt.Issuance<JwtAndClaims>> = runCatching {
        // Parse
        val (jwt, rawDisclosures) = parseIssuance(sdJwt).getOrThrow()
        // Check JWT
        val jwtClaims = jwtSignatureVerifier.verify(jwt).getOrThrow()
        val disclosures = disclosures(jwtClaims, rawDisclosures).getOrThrow()
        SdJwt.Issuance(jwt to jwtClaims, disclosures)
    }

    private fun parseIssuance(sdJwt: String): Result<Pair<Jwt, List<String>>> = runCatching {
        val list = sdJwt.split('~')
        if (list.size <= 1) throw ParsingError.asException()
        val jwt = list[0]
        val ds = list.drop(1).filter { it.isNotBlank() }
        jwt to ds
    }
}

internal class SdJwtPresentationVerifier(
    private val jwtSignatureVerifier: JwtSignatureVerifier,
    private val holderBindingVerifier: HolderBindingVerifier,
) {

    fun verify(sdJwt: String): Result<SdJwt.Presentation<JwtAndClaims, JwtAndClaims>> = runCatching {
        // Parse
        val (jwt, rawDisclosures, holderBindingJwt) = parsePresentation(sdJwt).getOrThrow()
        // Check JWT
        val jwtClaims = jwtSignatureVerifier.verify(jwt).getOrThrow()
        // Check Holder binding
        val hbClaims = holderBindingVerifier.verify(jwtClaims, holderBindingJwt).getOrThrow()
        val disclosures = disclosures(jwtClaims, rawDisclosures).getOrThrow()
        val holderBindingJwtAndClaims = holderBindingJwt?.let { hb -> hbClaims?.let { cs -> hb to cs } }
        SdJwt.Presentation(jwt to jwtClaims, disclosures, holderBindingJwtAndClaims)
    }

    private fun parsePresentation(sdJwt: String): Result<Triple<Jwt, List<String>, Jwt?>> = runCatching {
        val list = sdJwt.split('~')
        if (list.size <= 1) throw ParsingError.asException()
        val jwt = list[0]
        val containsHolderBinding = !sdJwt.endsWith('~')
        val ds = list.drop(1).run { if (containsHolderBinding) dropLast(1) else this }.filter { it.isNotBlank() }
        val hbJwt = if (containsHolderBinding) list.last() else null
        Triple(jwt, ds, hbJwt)
    }
}

private fun disclosures(jwtClaims: Claims, rawDisclosures: List<String>): Result<Set<Disclosure>> {
    /**
     * Looks in the provided claims for the hashing algorithm
     * @return the hashing algorithm, if a hashing algorithm is present and contains a string
     * representing a supported [HashAlgorithm]
     */
    fun hashingAlgorithmClaim(): HashAlgorithm =
        jwtClaims["_sd_alg"]?.let { element ->
            if (element is JsonPrimitive) HashAlgorithm.fromString(element.content)
            else null
        } ?: throw MissingOrUnknownHashingAlgorithm.asException()

    fun disclosures(): Set<Disclosure> {
        val maybeDisclosures = rawDisclosures.associateWith { Disclosure.wrap(it) }

        maybeDisclosures.filterValues { it.isFailure }.keys.also { invalidDisclosures ->
            if (invalidDisclosures.isNotEmpty())
                throw InvalidDisclosures(invalidDisclosures.toList()).asException()
        }

        return maybeDisclosures.values.map { it.getOrThrow() }.toSet().also { disclosures ->
            if (rawDisclosures.size != disclosures.size) throw NonUnqueDisclosures.asException()
        }
    }

    fun allDisclosuresHaveSignedDigest(
        hashAlgorithm: HashAlgorithm,
        disclosures: Set<Disclosure>,
        disclosureDigestsInJwt: List<DisclosureDigest>,
    ) {
        val calculatedDigestsPerDisclosure: Map<Disclosure, DisclosureDigest> =
            disclosures.associateWith { DisclosureDigest.digest(hashAlgorithm, it).getOrThrow() }

        val disclosuresMissingDigest = mutableListOf<Disclosure>()
        for ((disclosure, digest) in calculatedDigestsPerDisclosure) {
            if (digest !in disclosureDigestsInJwt) {
                disclosuresMissingDigest.add(disclosure)
            }
        }

        if (disclosuresMissingDigest.isNotEmpty()) throw MissingDigests(disclosuresMissingDigest).asException()
    }

    return runCatching {
        // Get Digests
        val disclosureDigestsInJwt = disclosureDigests(jwtClaims).also {
            // Make sure Digests are unique
            if (it.toSet().size != it.size) throw NonUniqueDisclosureDigests.asException()
        }

        // Make sure there is signed digest for each disclosure
        disclosures().also { disclosures ->
            val hashingAlgorithm = hashingAlgorithmClaim()
            allDisclosuresHaveSignedDigest(hashingAlgorithm, disclosures, disclosureDigestsInJwt)
        }
    }
}

/**
 * Extracts all the [digests][DisclosureDigest] from the given [claims],
 * including also sub claims
 *
 * @param claims to use
 * @return the digests found in the given [claims]
 */
internal fun disclosureDigests(claims: Claims): List<DisclosureDigest> {
    fun digestsOf(attribute: String, json: JsonElement): List<DisclosureDigest> =
        when {
            attribute == "_sd" && json is JsonArray -> json.mapNotNull { element ->
                if (element is JsonPrimitive) DisclosureDigest.wrap(element.content).getOrNull()
                else null
            }

            json is JsonObject -> disclosureDigests(json)
            else -> emptyList()
        }
    return claims.map { (attribute, json) -> digestsOf(attribute, json) }.flatten()
}
