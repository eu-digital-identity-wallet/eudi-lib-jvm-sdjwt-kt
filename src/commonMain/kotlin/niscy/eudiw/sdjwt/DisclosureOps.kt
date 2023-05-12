package niscy.eudiw.sdjwt

import kotlinx.serialization.json.*

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 */
data class DisclosedJsonObject(val disclosures: List<Disclosure>, val json: JsonObject)


object DisclosureOps {

    private val format: Json by lazy { Json }
    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        target: String?,
        claimToBeDisclosed: Pair<String, String>
    ): Result<DisclosedJsonObject> = runCatching{
        fun parseToObject(s: String): JsonObject = format.parseToJsonElement(s).jsonObject
        val targetJson = target?.let { parseToObject(it) } ?: JsonObject(emptyMap())
        val claimsToBeDisclosedJson =claimToBeDisclosed.first to parseToObject(claimToBeDisclosed.second)
        flatDisclose(hashAlgorithm, saltProvider, targetJson, claimsToBeDisclosedJson).getOrThrow()
    }


    fun flatDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        target: JsonObject = JsonObject(emptyMap()),
        claimToBeDisclosed: Pair<String, JsonObject>
    ): Result<DisclosedJsonObject> = runCatching {

        val resultJson = target.toMutableMap()
        val flatJson = mutableMapOf<String, JsonElement>()
        val resultDs = mutableListOf<Disclosure>()

        val (cN, cJson) = claimToBeDisclosed
        for ((k, v) in cJson) {
            val d = Disclosure.encode(saltProvider, k to v).getOrThrow()
            val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
            flatJson.addHashedDisclosures(setOf(h))
            resultDs.add(d)
        }
        resultJson[cN] = JsonObject(flatJson)
        if (resultDs.isNotEmpty()) {
            resultJson.addHashingAlgorithm(hashAlgorithm)
        }


        DisclosedJsonObject(resultDs, JsonObject(resultJson))
    }

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
                resultJson.addHashedDisclosures(hds)
                resultJson.addHashingAlgorithm(hashAlgorithm)
            }
        }

        for ((name, json) in claimsToBeDisclosed) {
            handle(name, json)
        }

        DisclosedJsonObject(resultDs, JsonObject(resultJson))
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
                    resultJson.addHashedDisclosures(setOf(hd))
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

    private fun MutableMap<String, JsonElement>.addHashedDisclosures(hds: Collection<HashedDisclosure>) {

        if (hds.isNotEmpty()) {
            val existingSds = this["_sd"]?.jsonArray ?: JsonArray(emptyList())
            this["_sd"] = JsonArray(existingSds + hds.map { it.toJson() })
        }
    }

    private fun MutableMap<String, JsonElement>.addHashingAlgorithm(hashAlgorithm: HashAlgorithm) {
        if (this["_sd_alg"] == null) this["_sd_alg"] = hashAlgorithm.toJson()
    }

    private fun HashedDisclosure.toJson() = JsonPrimitive(value)
    private fun Disclosure.toJson() = JsonPrimitive(value)
    private fun HashAlgorithm.toJson() = JsonPrimitive(alias)

}

