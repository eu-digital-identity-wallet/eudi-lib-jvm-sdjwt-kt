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

/**
 * A helper class for implementing flat disclosure
 * It represents the outcome of disclosing the contents of a [JsonObject]
 *
 * @param disclosures the disclosures of the attributes of the [JsonObject]
 * @param hashes the hashes of the disclosures. These hashes are calculated from the disclosures and optionally there
 * can be decoys
 * @param hashAlgorithm the algorithm used to calculate the hashes
 */
private data class FlatDisclosed(
    val disclosures: Set<Disclosure>,
    val hashes: Set<HashedDisclosure>,
    val hashAlgorithm: HashAlgorithm,
) {

    /**
     * Creates a [DisclosedJsonObject] from a [FlatDisclosed]
     *
     * @param includeHashAlgClaim whether to include the _sd_alg claim
     * @return the  [JsonObject] that contains the _sd claim and optionally the _sd_alg claim together with the
     * set of [Disclosure]
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun asDisclosedJsonObject(includeHashAlgClaim: Boolean): DisclosedJsonObject {
        val json =
            if (hashes.isNotEmpty()) {
                buildJsonObject {
                    if (includeHashAlgClaim) {
                        put("_sd_alg", hashAlgorithm.toJson())
                    }
                    putJsonArray("_sd") { addAll(hashes.map { it.toJson() }) }
                }
            } else {
                JsonObject(emptyMap())
            }

        return DisclosedJsonObject(disclosures, json)
    }

    companion object {

        /**
         * Combines two [FlatDisclosed] into a new [FlatDisclosed], provided
         * that they share the same [FlatDisclosed.hashAlgorithm]
         *
         *
         * @param a the first [FlatDisclosed]
         * @param b the second [FlatDisclosed]
         * @return a [FlatDisclosed] that contains the combined disclosures and hashes
         */
        fun combine(a: FlatDisclosed, b: FlatDisclosed): FlatDisclosed {
            require(a.hashAlgorithm == b.hashAlgorithm)
            val ds = a.disclosures + b.disclosures
            val set = a.hashes + b.hashes
            return FlatDisclosed(ds, set, a.hashAlgorithm)
        }

        /**
         * Factory method for calculating a [FlatDisclosed] for an input [claimsToBeDisclosed]
         * Method calculates the disclosures and the hashes for every attribute of the [claimsToBeDisclosed]
         * and then [combines][combine] them into a single [FlatDisclosed]
         *
         * @param hashAlgorithm the algorithm to be used for hashing disclosures
         * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
         * @param claimsToBeDisclosed the claims to be selectively disclosed
         * @param numOfDecoys the number of decoys
         *
         * @return the [FlatDisclosed] for the [claimsToBeDisclosed]
         */
        fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int,
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
        numOfDecoys: Int = 0,
    ): Result<DisclosedJsonObject> = runCatching {
        fun structuredDisclosed(claimName: String, claimValue: JsonElement): DisclosedJsonObject {
            val flatDisclosed = FlatDisclosed.make(
                hashAlgorithm,
                saltProvider,
                JsonObject(mapOf(claimName to claimValue)),
                numOfDecoys,
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
        val json: JsonObject,
    )

    private fun structureDisclose(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        jsonToBeDisclosed: JsonObject,
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
