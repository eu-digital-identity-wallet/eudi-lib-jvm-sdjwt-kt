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
        val Empty: DisclosedJsonObject = DisclosedJsonObject(emptySet(), JsonObject(emptyMap()))
        fun plain(plainClaims: JsonObject = JsonObject(mapOf())): DisclosedJsonObject =
            DisclosedJsonObject(emptySet(), plainClaims)

        fun combine(a: DisclosedJsonObject, b: DisclosedJsonObject): DisclosedJsonObject {
            val ds = a.disclosures + b.disclosures
            val set = a.jwtClaimSet + b.jwtClaimSet
            return DisclosedJsonObject(ds, JsonObject(set))
        }
    }
}

private data class FlatDisclosed(val disclosures: Set<Disclosure>, val hashes: Set<HashedDisclosure>) {



    companion object {

        fun combine(a: FlatDisclosed, b: FlatDisclosed): FlatDisclosed {
            val ds = a.disclosures + b.disclosures
            val set = a.hashes + b.hashes
            return FlatDisclosed(ds, set)
        }
        private fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimsToBeDisclosed: Claim
        ): FlatDisclosed {
            val d = Disclosure.encode(saltProvider, claimsToBeDisclosed).getOrThrow()
            val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
            return FlatDisclosed(setOf(d), setOf(h))
        }

        fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int
        ): FlatDisclosed {
            val decoys: Set<HashedDisclosure> = DecoyGen.Default.gen(hashAlgorithm, numOfDecoys).toSet()
            val initial = FlatDisclosed(emptySet(), decoys)
            return claimsToBeDisclosed.map { (k,v)->
                make(hashAlgorithm, saltProvider, k to v )
            }.fold(initial){ acc,x -> combine(acc, x) }

        }
    }
}

object DisclosureOps {

    private val format: Json by lazy { Json }



    /**
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param otherJwtClaims
     * @param claimsToBeDisclosed
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject,
        numOfDecoys: Int,
    ): Result<DisclosedJsonObject> = runCatching {

        val plainClaims = DisclosedJsonObject.plain(otherJwtClaims)
        val flatDisclosed = FlatDisclosed.make(hashAlgorithm, saltProvider, claimsToBeDisclosed, numOfDecoys)
        val flatDisclosedClaims = DisclosedJsonObject(flatDisclosed.disclosures, buildJsonObject {
            put("_sd_alg", hashAlgorithm.toJson())
            putJsonArray("_sd"){  addAll(flatDisclosed.hashes.map { it.toJson() }) }
        })
        DisclosedJsonObject.combine(plainClaims,flatDisclosedClaims)
    }

    /**
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param otherJwtClaims
     * @param claimToBeDisclosed
     */
    private fun flatDisclose2(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: JsonObject = JsonObject(emptyMap()),
        claimToBeDisclosed: Claim,
        numOfDecoys: Int,
        includeHashFunctionClaim: Boolean = true
    ): Result<DisclosedJsonObject> = runCatching {


        val subClaimsToBeDisclosed =
            when (val disclosedClaimValue = claimToBeDisclosed.value()) {
                is JsonObject -> disclosedClaimValue.entries.map { it.key to it.value }
                else -> setOf(claimToBeDisclosed)
            }

        val disclosuresAnHashes = subClaimsToBeDisclosed.associate { claim ->
            val d = Disclosure.encode(saltProvider, claim).getOrThrow()
            val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
            d to h
        }
        val disclosures = disclosuresAnHashes.keys
        val decoys = DecoyGen.Default.gen(hashAlgorithm, numOfDecoys)
        val hashes = disclosuresAnHashes.values + decoys

        val jwtClaimSet = with(otherJwtClaims.toMutableMap()) {
            addHashedDisclosuresClaim(hashes)
            if (includeHashFunctionClaim) addHashingAlgorithmClaim(hashAlgorithm)
            JsonObject(this)
        }

        DisclosedJsonObject(disclosures, jwtClaimSet)

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

        val plain = DisclosedJsonObject.plain(
            JsonObject(
                if (claimsToBeDisclosed.isNotEmpty()) otherJwtClaims + hashAlgorithm.toClaim()
                else otherJwtClaims
            )
        )

        claimsToBeDisclosed.map { (claimName, claimValue) ->

            val disclosedClaimValue = flatDisclose2(
                hashAlgorithm = hashAlgorithm,
                saltProvider = saltProvider,
                claimToBeDisclosed = claimName to claimValue,
                numOfDecoys = numOfDecoys,
                includeHashFunctionClaim = false,
            ).getOrThrow()

            disclosedClaimValue.copy(jwtClaimSet = JsonObject(mapOf(claimName to disclosedClaimValue.jwtClaimSet)))

        }.fold(plain, DisclosedJsonObject::combine)
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

    private fun HashedDisclosure.toJson() = JsonPrimitive(value)
    private fun Disclosure.toJson() = JsonPrimitive(value)
    private fun HashAlgorithm.toClaim() = "_sd_alg" to toJson()
    private fun HashAlgorithm.toJson() = JsonPrimitive(alias)

}

