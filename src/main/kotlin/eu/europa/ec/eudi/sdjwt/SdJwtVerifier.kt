package eu.europa.ec.eudi.sdjwt

import kotlinx.serialization.json.*


sealed interface Validation {
    sealed interface Invalid : Validation {
        object ParsingError : Validation
        object InvalidDisclosures : Validation
        object MissingSdAlgClaim : Validation

        object NonUniqueDisclosureDigests : Validation

    }

    data class Valid(val jwtPayload: Claims, val disclosures: List<Disclosure>) : Validation
}

interface SdJwtVerifier {

    fun verify(sdJwt: String): Validation {

        val parsed = parse(sdJwt)
        if (parsed == null) {
            return Validation.Invalid.ParsingError
        }
        val withDisclosures = checkDisclosures(parsed)
        if (withDisclosures == null) {
            return Validation.Invalid.InvalidDisclosures
        }


        val sdAlgorithm = sdAlgClaim(withDisclosures.jwtPayload)
        if (sdAlgorithm == null) {
            return Validation.Invalid.MissingSdAlgClaim
        }
        TODO()

    }

    fun parse(sdJwt: String): Parsed<String>?
    fun checkDisclosures(parsed: Parsed<String>): Parsed<Disclosure>?

    fun sdAlgClaim(jwt: String): HashAlgorithm?

    fun crossCheckDisclosures(hashAlgorithm: HashAlgorithm, withDisclosures: Parsed<Disclosure>) {

        val digestPerDisclosures: Map<Disclosure, DisclosureDigest> =
            withDisclosures.disclosures.associateWith { DisclosureDigest.digest(hashAlgorithm, it).getOrThrow() }

        val disclosureDigestsInJwt = disclosureDigests(withDisclosures.jwtPayload)



    }
}


data class Parsed<D>(val jwtPayload: JsonObject, val disclosures: List<D>, val holderBindingJwt: String?)

fun disclosureDigests(jsonElement: JsonElement): List<DisclosureDigest> {
    return when (jsonElement) {
        is JsonArray -> TODO()

        is JsonObject -> {
            val sdClaim = jsonElement["_sd"]
            val digests: List<DisclosureDigest> = if (sdClaim != null && sdClaim is JsonArray) {
                sdClaim.jsonArray.map { DisclosureDigest.wrap(it.jsonPrimitive.content).getOrThrow() }.toList()
            } else jsonElement.map { disclosureDigests(it.value) }.flatten()

            return digests
        }

        is JsonLiteral -> emptyList()
        JsonNull -> emptyList()
    }
}
