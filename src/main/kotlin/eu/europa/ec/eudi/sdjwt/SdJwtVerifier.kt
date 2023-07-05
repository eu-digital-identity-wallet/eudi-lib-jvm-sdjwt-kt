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

import eu.europa.ec.eudi.sdjwt.VerificationError.*
import eu.europa.ec.eudi.sdjwt.VerificationError.HolderBindingError.*
import kotlinx.serialization.json.*
import java.text.ParseException
import com.nimbusds.jose.JOSEException as NimbusJOSEException
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
import com.nimbusds.jwt.proc.JWTProcessor as NimbusJWTProcessor

/**
 * Invalid SD-JWT
 */
sealed interface VerificationError {
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
     * Errors related to Holder Binding
     */
    sealed interface HolderBindingError : VerificationError {

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
 */
data class SdJwtVerificationException(val reason: VerificationError) : Exception()

/**
 * Creates a [SdJwtVerificationException] for the given error
 *
 * @receiver the error to be wrapped into the exception
 * @return an exception with the given error
 */
fun VerificationError.asException(): SdJwtVerificationException = SdJwtVerificationException(this)

fun interface JwtVerifier {
    operator fun invoke(jwt: String): Claims?

    fun and(other: (Claims) -> Boolean): JwtVerifier {
        return JwtVerifier { jwt -> this(jwt)?.let { if (other(it)) it else null } }
    }

    companion object {
        val NoSignatureValidation: JwtVerifier = JwtVerifier { jwt ->
            try {
                val signedJwt = NimbusSignedJWT.parse(jwt)
                signedJwt.jwtClaimsSet.asClaims()
            } catch (e: ParseException) {
                null
            }
        }
    }
}

fun NimbusJWSVerifier.asJwtVerifier(): JwtVerifier = JwtVerifier { jwt ->
    try {
        val signedJwt = NimbusSignedJWT.parse(jwt)
        if (!signedJwt.verify(this)) null
        else signedJwt.jwtClaimsSet.asClaims()
    } catch (e: ParseException) {
        null
    } catch (e: NimbusJOSEException) {
        null
    }
}

fun NimbusJWTProcessor<*>.asJwtVerifier(): JwtVerifier = JwtVerifier { jwt ->
    process(jwt, null).asClaims()
}

fun NimbusJWTClaimsSet.asClaims(): Claims =
    toPayload().toBytes().run {
        val s: String = this.decodeToString()
        Json.parseToJsonElement(s).jsonObject
    }

/**
 *
 */
sealed interface HolderBindingVerifier {

    fun verify(jwt: Claims, holderBindingJwt: String?): HolderBindingError? = when (this) {
        is ShouldNotBePresent -> if (holderBindingJwt != null) UnexpectedHolderBindingJwt else null
        is MustBePresentAndValid ->
            if (holderBindingJwt != null) {
                when (val holderBindingJwtVerifier = holderBindingVerifierProvider(jwt)) {
                    null -> MissingHolderPubKey
                    else -> {
                        val holderBindingClaims = holderBindingJwtVerifier(holderBindingJwt)
                        if (holderBindingClaims == null) InvalidHolderBindingJwt
                        else null
                    }
                }
            } else MissingHolderBindingJwt
    }

    object ShouldNotBePresent : HolderBindingVerifier

    class MustBePresentAndValid(val holderBindingVerifierProvider: (Claims) -> JwtVerifier?) : HolderBindingVerifier

    companion object {
        val MustBePresent: MustBePresentAndValid = MustBePresentAndValid { JwtVerifier.NoSignatureValidation }
    }
}

object SdJwtVerifier {

    fun verifyIssuance(
        jwtVerifier: JwtVerifier = JwtVerifier.NoSignatureValidation,
        sdJwt: String,
    ): Result<SdJwt.Issuance<Pair<Jwt, Claims>>> =
        SdJwtIssuanceVerifier(jwtVerifier).verify(sdJwt)

    fun verifyPresentation(
        jwtVerifier: JwtVerifier = JwtVerifier.NoSignatureValidation,
        holderBindingVerifier: HolderBindingVerifier = HolderBindingVerifier.ShouldNotBePresent,
        sdJwt: String,
    ): Result<SdJwt.Presentation<Pair<Jwt, Claims>, Jwt>> =
        SdJwtPresentationVerifier(jwtVerifier, holderBindingVerifier).verify(sdJwt)
}

internal class SdJwtIssuanceVerifier(
    private val jwtVerifier: JwtVerifier = JwtVerifier.NoSignatureValidation,
) {

    fun verify(sdJwt: String): Result<SdJwt.Issuance<Pair<Jwt, Claims>>> = runCatching {
        // Parse
        val (jwt, rawDisclosures) = parseIssuance(sdJwt).getOrThrow()
        // Check JWT
        val jwtClaims = jwtVerifier.verifyJwt(jwt).getOrThrow()
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
    private val jwtVerifier: JwtVerifier = JwtVerifier.NoSignatureValidation,
    private val holderBindingVerifier: HolderBindingVerifier = HolderBindingVerifier.ShouldNotBePresent,
) {

    fun verify(sdJwt: String): Result<SdJwt.Presentation<Pair<Jwt, Claims>, Jwt>> = runCatching {
        // Parse
        val (jwt, rawDisclosures, holderBindingJwt) = parsePresentation(sdJwt).getOrThrow()
        // Check JWT
        val jwtClaims = jwtVerifier.verifyJwt(jwt).getOrThrow()
        // Check Holder binding
        checkHolderBindingJwt(jwtClaims, holderBindingJwt).getOrThrow()
        val disclosures = disclosures(jwtClaims, rawDisclosures).getOrThrow()
        SdJwt.Presentation(jwt to jwtClaims, disclosures, holderBindingJwt)
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

    private fun checkHolderBindingJwt(jwtClaims: Claims, holderBindingJwt: Jwt?): Result<Unit> = runCatching {
        val hbError = holderBindingVerifier.verify(jwtClaims, holderBindingJwt)
        if (hbError != null) throw hbError.asException()
    }
}

private fun JwtVerifier.verifyJwt(jwt: String): Result<Claims> = runCatching {
    invoke(jwt) ?: throw InvalidJwt.asException()
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
