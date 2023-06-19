package eu.europa.ec.eudi.sdjwt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 *
 * @param disclosures the disclosures calculated
 * @param jwtClaimSet the JSON object that contains the hashed disclosures
 */
data class DisclosedJsonObject(val disclosures: Set<Disclosure>, val jwtClaimSet: JsonObject) {
    companion object {
        fun plain(plainClaims: JsonObject = JsonObject(mapOf())): DisclosedJsonObject =
            DisclosedJsonObject(emptySet(), plainClaims)

        fun combine(a: DisclosedJsonObject, b: DisclosedJsonObject): DisclosedJsonObject {
            val ds = a.disclosures + b.disclosures
            val set = a.jwtClaimSet + b.jwtClaimSet
            return DisclosedJsonObject(ds, JsonObject(set))
        }


    }
}

private data class FlatDisclosed(
    val disclosures: Set<Disclosure>,
    val hashes: Set<HashedDisclosure>,
    val hashAlgorithm: HashAlgorithm
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun asDisclosedJsonObject(includeHashAlgClaim: Boolean): DisclosedJsonObject {
        val json =
            if (hashes.isNotEmpty()) buildJsonObject {
                if (includeHashAlgClaim) {
                    put("_sd_alg", hashAlgorithm.toJson())
                }
                putJsonArray("_sd") { addAll(hashes.map { it.toJson() }) }
            } else JsonObject(emptyMap())

        return DisclosedJsonObject(disclosures, json)
    }



    companion object {

        fun combine(a: FlatDisclosed, b: FlatDisclosed): FlatDisclosed {
            require(a.hashAlgorithm == b.hashAlgorithm)
            val ds = a.disclosures + b.disclosures
            val set = a.hashes + b.hashes
            return FlatDisclosed(ds, set, a.hashAlgorithm)
        }


        fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int
        ): FlatDisclosed {
            val decoys: Set<HashedDisclosure> = DecoyGen.Default.gen(hashAlgorithm, numOfDecoys).toSet()
            val initial = FlatDisclosed(emptySet(), decoys, hashAlgorithm)
            return claimsToBeDisclosed.map { (k, v) ->
                val d = Disclosure.encode(saltProvider, k to v).getOrThrow()
                val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
                FlatDisclosed(setOf(d), setOf(h), hashAlgorithm)
            }.fold(initial, FlatDisclosed::combine)

        }
    }
}


object DisclosureOps {


    /**
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param otherJwtClaims
     * @param claimsToBeDisclosed
     */
    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject,
        numOfDecoys: Int,
    ): Result<DisclosedJsonObject> = runCatching {
        val flatDisclosed = FlatDisclosed.make(hashAlgorithm, saltProvider, claimsToBeDisclosed, numOfDecoys)
        val flatDisclosedClaims = flatDisclosed.asDisclosedJsonObject(true)
        val plainClaims = DisclosedJsonObject.plain(otherJwtClaims)
        DisclosedJsonObject.combine(plainClaims, flatDisclosedClaims)
    }


    /**
     *
     * Method accepts one or more [claimsToBeDisclosed], calculates the related disclosures
     * and returns the list of disclosures and a new [JsonObject] which contains the hashed disclosures (instead
     * of the claims)
     *
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     */
    fun structureDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject,
        numOfDecoys: Int = 0
    ): Result<DisclosedJsonObject> = runCatching {

        fun structuredDisclosed(claimName: String, claimValue: JsonElement): DisclosedJsonObject {
            val flatDisclosed = FlatDisclosed.make(
                hashAlgorithm,
                saltProvider,
                JsonObject(mapOf(claimName to claimValue)),
                numOfDecoys
            )
            val json = buildJsonObject {
                put(claimName, flatDisclosed.asDisclosedJsonObject(false).jwtClaimSet)
                put("_sd_alg", hashAlgorithm.toJson())
            }
            return DisclosedJsonObject(flatDisclosed.disclosures, json)
        }

        val plainClaims = DisclosedJsonObject.plain(otherJwtClaims)

        claimsToBeDisclosed
            .map { (n, v) -> structuredDisclosed(n, v) }
            .fold(plainClaims, DisclosedJsonObject::combine)
    }


    private data class InterimDisclosedJsonObject(
        val disclosures: List<Disclosure>,
        val hashedDisclosures: List<HashedDisclosure>,
        val json: JsonObject
    )

    private fun structureDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        jsonToBeDisclosed: JsonObject
    ): Result<InterimDisclosedJsonObject> = runCatching {

        val resultJson = mutableMapOf<String, JsonElement>()
        val resultDs = mutableListOf<Disclosure>()
        val resultHds = mutableListOf<HashedDisclosure>()

        fun discloseAttribute(name: String, json: JsonElement) {

            when (json) {
                is JsonObject -> {
                    val (ds, _, dJson) = structureDisclose(hashAlgorithm, saltProvider, json).getOrThrow()
                    resultJson[name] = dJson
                    resultDs.addAll(ds)
                }

                else -> {
                    val d = Disclosure.encode(saltProvider, name to json).getOrThrow()
                    val hd = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
                    resultJson.addHashedDisclosuresClaim(setOf(hd))
                    resultDs.add(d)
                    resultHds.add(hd)
                }
            }
        }

        for ((name, json) in jsonToBeDisclosed) {
            discloseAttribute(name, json)
        }

        InterimDisclosedJsonObject(resultDs, resultHds, JsonObject(resultJson))
    }


    //
    // Helper methods for JSON
    //

    private fun MutableMap<String, JsonElement>.addHashedDisclosuresClaim(hds: Collection<HashedDisclosure>) {

        if (hds.isNotEmpty()) {
            val existingSds = this["_sd"]?.jsonArray ?: JsonArray(emptyList())
            this["_sd"] =
                JsonArray((existingSds + hds.map { it.toJson() }).shuffled())
        }
    }

    private fun MutableMap<String, JsonElement>.addHashingAlgorithmClaim(hashAlgorithm: HashAlgorithm) {
        if (this["_sd_alg"] == null) this["_sd_alg"] = hashAlgorithm.toJson()
    }


}

private fun HashedDisclosure.toJson() = JsonPrimitive(value)
private fun HashAlgorithm.toClaim() = "_sd_alg" to toJson()
private fun HashAlgorithm.toJson() = JsonPrimitive(alias)
