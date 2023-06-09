package niscy.eudiw.sdjwt

import kotlinx.serialization.json.*

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 *
 * @param disclosures the disclosures calculated
 * @param jwtClaimSet the JSON object that contains the hashed disclosures
 */
data class DisclosedJsonObject(val disclosures: Set<Disclosure>, val jwtClaimSet: JsonObject)


object DisclosureOps {

    private val format: Json by lazy { Json }

    /**
     * @param hashAlgorithm the algorithm to be used for []hashing disclosures][HashedDisclosure]
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param otherJwtClaims
     * @param claimToBeDisclosed
     */
    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: String?,
        claimToBeDisclosed: Pair<String, String>,
        numOfDecoys: Int
    ): Result<DisclosedJsonObject> = runCatching {
        fun parseToObject(s: String): JsonObject = format.parseToJsonElement(s).jsonObject
        val otherJwtClaimsJson = otherJwtClaims?.let { parseToObject(it) } ?: JsonObject(emptyMap())
        val claimsToBeDisclosedJson = claimToBeDisclosed.first to parseToObject(claimToBeDisclosed.second)
        flatDisclose(hashAlgorithm, saltProvider, otherJwtClaimsJson, claimsToBeDisclosedJson, numOfDecoys).getOrThrow()
    }


    /**
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param otherJwtClaims
     * @param claimToBeDisclosed
     */
    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        otherJwtClaims: JsonObject = JsonObject(emptyMap()),
        claimToBeDisclosed: Claim,
        numOfDecoys: Int
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

        val decoys = DecoyGen.Default.gen(hashAlgorithm, numOfDecoys)
        val hashes = disclosuresAnHashes.values + decoys
        val disclosures = disclosuresAnHashes.keys
        val jwtClaimSet = otherJwtClaims.toMutableMap()
        jwtClaimSet.addHashedDisclosuresClaim(hashes)
        jwtClaimSet.addHashingAlgorithmClaim(hashAlgorithm)
        DisclosedJsonObject(disclosures, JsonObject(jwtClaimSet))

    }

    /**
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     */
    fun structureDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        target: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject
    ): Result<DisclosedJsonObject> = runCatching {

        val (disclosures, tmpJson) = structureDisclose(
            hashAlgorithm,
            saltProvider,
            target,
            mapOf("tmp" to claimsToBeDisclosed)
        ).getOrThrow()

        val resultJson = tmpJson.toMutableMap()
        resultJson.remove("tmp")?.let { resultJson.putAll(it.jsonObject) }

        DisclosedJsonObject(disclosures, JsonObject(resultJson))
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
        target: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: Map<String, JsonObject>
    ): Result<DisclosedJsonObject> = runCatching {

        val resultJson = target.toMutableMap()
        val resultDs = mutableListOf<Disclosure>()

        fun handle(name: String, json: JsonObject) {
            val (ds, hds, dJson) = structureDisclose(hashAlgorithm, saltProvider, json).getOrThrow()
            resultDs.addAll(ds)
            resultJson[name] = dJson
            if (hds.isNotEmpty()) {
                resultJson.addHashedDisclosuresClaim(hds)
                resultJson.addHashingAlgorithmClaim(hashAlgorithm)
            }
        }

        for ((name, json) in claimsToBeDisclosed) {
            handle(name, json)
        }

        DisclosedJsonObject(resultDs.toSet(), JsonObject(resultJson))
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
    private fun HashAlgorithm.toJson() = JsonPrimitive(alias)

}

