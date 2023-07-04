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

import com.nimbusds.jose.JOSEException
import eu.europa.ec.eudi.sdjwt.Verification.Invalid.*
import eu.europa.ec.eudi.sdjwt.Verification.Invalid.HolderBindingError.*
import kotlinx.serialization.json.*
import java.text.ParseException
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

/**
 * The outcome of [verifying][SdJwtVerifier.verify] an SD-JWT
 */
sealed interface Verification {

    /**
     * Invalid SD-JWT
     */
    sealed interface Invalid : Verification {
        object ParsingError : Invalid {
            override fun toString(): String = "ParsingError"
        }

        /**
         * SD-JWT contains in invalid JWT
         */
        object InvalidJwt : Invalid {
            override fun toString(): String = "InvalidJwt"
        }

        /**
         * Errors related to Holder Binding
         */
        sealed interface HolderBindingError : Invalid {

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
        data class InvalidDisclosures(val invalidDisclosures: List<String>) : Invalid

        /**
         * SD-JWT contains a JWT which is missing or contains an invalid
         * Hashing Algorithm claim
         */
        object MissingOrUnknownHashingAlgorithm : Invalid {
            override fun toString(): String = "MissingOrUnknownHashingAlgorithm"
        }

        /**
         * SD-JWT contains non-unique disclosures
         */
        object NonUnqueDisclosures : Invalid {
            override fun toString(): String = "NonUnqueDisclosures"
        }

        /**
         * SD-JWT contains a JWT which has non unique digests
         */
        object NonUniqueDisclosureDigests : Invalid {
            override fun toString(): String = "NonUniqueDisclosureDigests"
        }

        /**
         * SD-JWT doesn't contain digests for the [disclosures]
         * @param disclosures The disclosures for which there are no digests
         */
        data class MissingDigests(val disclosures: List<Disclosure>) : Verification
    }

    /**
     * Valid SD-JWT
     */
    data class Valid(
        val jwt: Jwt,
        val jwtClaims: Claims,
        val disclosures: List<Disclosure>,
        val holderBindingJwt: Jwt?,
    ) : Verification
}

fun interface JwtVerifier {
    operator fun invoke(jwt: String): Claims?

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

fun NimbusJWSVerifier.asJwtVerifier(): JwtVerifier = JwtVerifier { jwt ->
    try {
        val signedJwt = NimbusSignedJWT.parse(jwt)
        if (!signedJwt.verify(this)) null
        else signedJwt.jwtClaimsSet.asClaims()
    } catch (e: ParseException) {
        null
    } catch (e: JOSEException) {
        null
    }
}

private fun NimbusJWTClaimsSet.asClaims(): Claims =
    toPayload().toBytes().run {
        val s: String = this.decodeToString()
        Json.parseToJsonElement(s).jsonObject
    }

class SdJwtVerifier(
    private val jwtVerifier: JwtVerifier = JwtVerifier.NoSignatureValidation,
    private val holderBindingVerifier: HolderBindingVerifier = HolderBindingVerifier.ShouldNotBePresent,
) {
    fun verify(sdJwt: String): Verification {
        // Parse
        val (jwt, rawDisclosures, holderBindingJwt) =
            parse(sdJwt) ?: return ParsingError

        // Check JWT
        val jwtClaims =
            jwtVerifier(jwt) ?: return InvalidJwt

        // Check Hashing Algorithm claim is present
        val sdAlgorithm =
            hashingAlgorithmClaim(jwtClaims) ?: return MissingOrUnknownHashingAlgorithm

        // Check Holder binding
        val hbError = holderBindingVerifier.verify(jwtClaims, holderBindingJwt)
        if (hbError != null) return hbError

        // Get Digests
        val disclosureDigestsInJwt = disclosureDigests(jwtClaims)

        // Make sure Digests are unique
        if (disclosureDigestsInJwt.toSet().size != disclosureDigestsInJwt.size)
            return NonUniqueDisclosureDigests

        // Check Disclosures
        val maybeDisclosures = rawDisclosures.associateWith { Disclosure.wrap(it) }
        val invalidDisclosures = maybeDisclosures.filterValues { it.isFailure }.keys
        if (invalidDisclosures.isNotEmpty()) {
            return InvalidDisclosures(invalidDisclosures.toList())
        }
        val disclosures = maybeDisclosures.values.map { it.getOrThrow() }

        // Make sure disclosures are unique
        if (rawDisclosures.size != disclosures.size) return NonUnqueDisclosures

        // Make sure there is signed digest for each disclosure
        val disclosuresMissingDigest = disclosuresMissingDigest(sdAlgorithm, disclosures, disclosureDigestsInJwt)
        if (disclosuresMissingDigest.isNotEmpty()) return MissingDigests(disclosuresMissingDigest)

        return Verification.Valid(jwt, jwtClaims, disclosures, holderBindingJwt)
    }
}

private fun parse(sdJwt: String): Triple<Jwt, List<String>, Jwt?>? {
    val list = sdJwt.split('~')
    if (list.size <= 1) return null
    val jwt = list[0]
    val containsHolderBinding = !sdJwt.endsWith('~')
    val ds = list.drop(1).run { if (containsHolderBinding) dropLast(1) else this }.filter { it.isNotBlank() }
    val hbJwt = if (containsHolderBinding) list.last() else null
    return Triple(jwt, ds, hbJwt)
}

/**
 * Looks in the provided [claims] for the hashing algorithm
 * @param claims to use
 * @return the hashing algorithm, if a hashing algorithm is present and contains a string
 * representing a supported [HashAlgorithm]
 */
private fun hashingAlgorithmClaim(claims: Claims): HashAlgorithm? = claims["_sd_alg"]?.let { element ->
    if (element is JsonPrimitive) HashAlgorithm.fromString(element.content)
    else null
}

/**
 *
 */
private fun disclosuresMissingDigest(
    hashAlgorithm: HashAlgorithm,
    disclosures: List<Disclosure>,
    disclosureDigestsInJwt: List<DisclosureDigest>,
): List<Disclosure> {
    val calculatedDigestsPerDisclosure: Map<Disclosure, DisclosureDigest> =
        disclosures.associateWith { DisclosureDigest.digest(hashAlgorithm, it).getOrThrow() }

    val disclosuresMissingDigest = mutableListOf<Disclosure>()
    for ((disclosure, digest) in calculatedDigestsPerDisclosure) {
        if (digest !in disclosureDigestsInJwt) {
            disclosuresMissingDigest.add(disclosure)
        }
    }

    return disclosuresMissingDigest
}

/**
 * Extracts all the [digests][DisclosureDigest] from the given [claims],
 * including also sub-claims
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
