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
 * @param claimSet the JSON object that contains the hashed disclosures and possible plain claims
 */
class DisclosedClaimSet(val disclosures: Set<Disclosure>, val claimSet: JsonObject) {
    operator fun component1(): Set<Disclosure> = disclosures
    operator fun component2(): JsonObject = claimSet

    companion object {

        val Empty: DisclosedClaimSet = DisclosedClaimSet(emptySet(), JsonObject(emptyMap()))
        fun plain(plainClaims: JsonObject = JsonObject(mapOf())): DisclosedClaimSet =
            DisclosedClaimSet(emptySet(), plainClaims)

        /**
         * @param hashAlgorithm the algorithm to be used for hashing disclosures
         * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
         * @param otherClaims claims that will be included as plain claims
         * @param claimsToBeDisclosed claims to be selectively disclosed
         * @param numOfDecoys the number of decoys
         * @return
         */
        fun flat(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider = SaltProvider.Default,
            otherClaims: JsonObject = JsonObject(emptyMap()),
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int = 0,
        ): Result<DisclosedClaimSet> = runCatching {
            val disclosuresAndHashes =
                DisclosuresAndHashes.make(hashAlgorithm, saltProvider, claimsToBeDisclosed, numOfDecoys)
            val flatDisclosedClaims = disclosuresAndHashes.toFlatDisclosedClaimSet(true)
            val plainClaims = plain(otherClaims)
            combine(plainClaims, flatDisclosedClaims)
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
        fun structured(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider = SaltProvider.Default,
            otherJwtClaims: JsonObject = JsonObject(emptyMap()),
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int = 0,
            includeHashAlgClaim: Boolean = true,
        ): Result<DisclosedClaimSet> = runCatching {
            fun structuredDisclosed(claimName: String, claimValue: JsonElement): DisclosedClaimSet {
                val disclosuresAndHashes = when (claimValue) {
                    is JsonObject -> claimValue.map {
                        DisclosuresAndHashes.make(
                            hashAlgorithm,
                            saltProvider,
                            JsonObject(mapOf(it.toPair())),
                            numOfDecoys,
                        )
                    }.fold(DisclosuresAndHashes.empty(hashAlgorithm), DisclosuresAndHashes::combine)

                    else -> DisclosuresAndHashes.make(
                        hashAlgorithm,
                        saltProvider,
                        JsonObject(mapOf(claimName to claimValue)),
                        numOfDecoys,
                    )
                }
                return disclosuresAndHashes.toStructureDisclosedClaimSet(claimName)
            }

            val plainClaims = plain(
                JsonObject(
                    otherJwtClaims +
                        if (claimsToBeDisclosed.isNotEmpty() && includeHashAlgClaim) mapOf(hashAlgorithm.toClaim())
                        else emptyMap(),
                ),
            )

            claimsToBeDisclosed
                .map { (claimName, claimValue) -> structuredDisclosed(claimName, claimValue) }
                .fold(plainClaims, DisclosedClaimSet::combine)
        }

        /**
         * Combines two [DisclosedClaimSet]
         *
         * @return a new [DisclosedClaimSet] which contains the combined set of [DisclosedClaimSet.claimSet] and the
         * combined [DisclosedClaimSet.claimSet]
         */
        fun combine(a: DisclosedClaimSet, b: DisclosedClaimSet): DisclosedClaimSet {
            assertNoCommonElements(a.disclosures, b.disclosures) {
                "Cannot combine DisclosedClaimSet with common disclosures"
            }
            assertNoCommonElements(a.claimSet.keys, b.claimSet.keys) {
                "Cannot combine DisclosedClaimSet with common claims"
            }

            return DisclosedClaimSet(
                disclosures = a.disclosures + b.disclosures,
                claimSet = JsonObject(a.claimSet + b.claimSet),
            )
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
private class DisclosuresAndHashes private constructor(
    val disclosures: Set<Disclosure>,
    val hashes: Set<HashedDisclosure>,
    val hashAlgorithm: HashAlgorithm,
) {
    init {
        require(hashes.size >= disclosures.size) {
            "Hashes should be at least as disclosures"
        }
    }

    companion object {

        fun empty(hashAlgorithm: HashAlgorithm): DisclosuresAndHashes =
            DisclosuresAndHashes(emptySet(), emptySet(), hashAlgorithm)

        /**
         * Combines two [DisclosuresAndHashes] into a new [DisclosuresAndHashes], provided
         * that they share the same [DisclosuresAndHashes.hashAlgorithm]
         *
         *
         * @param a the first [DisclosuresAndHashes]
         * @param b the second [DisclosuresAndHashes]
         * @return a [DisclosuresAndHashes] that contains the combined disclosures and hashes
         */
        fun combine(a: DisclosuresAndHashes, b: DisclosuresAndHashes): DisclosuresAndHashes {
            require(a.hashAlgorithm == b.hashAlgorithm) {
                "Cannot combine DisclosuresAndHashes with different hashing algorithms"
            }
            assertNoCommonElements(a.disclosures, b.disclosures) {
                "Cannot combine DisclosuresAndHashes with common disclosures"
            }
            assertNoCommonElements(a.hashes, b.hashes) {
                "Cannot combine DisclosuresAndHashes with common hashes"
            }
            return DisclosuresAndHashes(
                disclosures = a.disclosures + b.disclosures,
                hashes = a.hashes + b.hashes,
                hashAlgorithm = a.hashAlgorithm,
            )
        }

        /**
         * Factory method for calculating a [DisclosuresAndHashes] for an input [claimsToBeDisclosed]
         * Method calculates the disclosures and the hashes for every attribute of the [claimsToBeDisclosed]
         * and then [combines][combine] them into a single [DisclosuresAndHashes]
         *
         * @param hashAlgorithm the algorithm to be used for hashing disclosures
         * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
         * @param claimsToBeDisclosed the claims to be selectively disclosed
         * @param numOfDecoys the number of decoys
         *
         * @return the [DisclosuresAndHashes] for the [claimsToBeDisclosed]
         */
        fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimsToBeDisclosed: JsonObject,
            numOfDecoys: Int,
        ): DisclosuresAndHashes {
            val decoys: Set<HashedDisclosure> = DecoyGen.Default.gen(hashAlgorithm, numOfDecoys).toSet()
            val initial = DisclosuresAndHashes(emptySet(), decoys, hashAlgorithm)
            return claimsToBeDisclosed.map { (k, v) ->
                val d = Disclosure.encode(saltProvider, k to v).getOrThrow()
                val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
                DisclosuresAndHashes(setOf(d), setOf(h), hashAlgorithm)
            }.fold(initial, DisclosuresAndHashes::combine)
        }
    }
}

/**
 * Creates a [DisclosedClaimSet] from a [DisclosuresAndHashes]
 *
 * @param includeHashAlgClaim whether to include the _sd_alg claim
 * @return the  [JsonObject] that contains the _sd claim and optionally the _sd_alg claim together with the
 * set of [Disclosure]
 */
@OptIn(ExperimentalSerializationApi::class)
private fun DisclosuresAndHashes.toFlatDisclosedClaimSet(includeHashAlgClaim: Boolean): DisclosedClaimSet {
    return if (hashes.isNotEmpty())
        DisclosedClaimSet(
            disclosures,
            buildJsonObject {
                if (includeHashAlgClaim) put("_sd_alg", hashAlgorithm.toJson())
                putJsonArray("_sd") { addAll(hashes.map { it.toJson() }) }
            },
        )
    else DisclosedClaimSet.Empty
}

private fun DisclosuresAndHashes.toStructureDisclosedClaimSet(claimName: String): DisclosedClaimSet {
    val json = buildJsonObject {
        put(claimName, toFlatDisclosedClaimSet(false).claimSet)
    }
    return DisclosedClaimSet(disclosures, json)
}

object DisclosureOps {

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
private fun <T> assertNoCommonElements(a: Iterable<T>, b: Iterable<T>, lazyMessage: () -> Any) {
    require(a.intersect(b).isEmpty(), lazyMessage)
}
