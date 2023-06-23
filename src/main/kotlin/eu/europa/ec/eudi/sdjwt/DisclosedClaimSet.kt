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
import java.util.*

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 *
 * @param disclosures the disclosures calculated
 * @param claimSet the JSON object that contains the hashed disclosures and possible plain claims
 */
class DisclosedClaimSet private constructor(val disclosures: Set<Disclosure>, val claimSet: JsonObject) {
    operator fun component1(): Set<Disclosure> = disclosures
    operator fun component2(): JsonObject = claimSet

    companion object {

        /**
         * An empty claim set with no disclosures
         */
        private val Empty: DisclosedClaimSet = DisclosedClaimSet(emptySet(), JsonObject(emptyMap()))

        /**
         * Creates a [DisclosedClaimSet] with no disclosures
         *
         * @param plainClaims the claims to be included plainly
         *
         * @return a [DisclosedClaimSet] with no disclosures
         */
        private fun plain(plainClaims: JsonObject = JsonObject(mapOf())): DisclosedClaimSet =
            DisclosedClaimSet(emptySet(), plainClaims)

        private fun hashClaim(a: HashAlgorithm): DisclosedClaimSet =
            DisclosedClaimSet(emptySet(), JsonObject(mapOf(a.toClaim())))

        /**
         * @param hashAlgorithm the algorithm to be used for hashing disclosures
         * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
         * @param dsl The SD-JWT structure
         * @param numOfDecoys the number of decoys
         * @return
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun disclose(
            hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
            saltProvider: SaltProvider = SaltProvider.Default,
            numOfDecoys: Int = 0,
            dsl: SdJwtDsl.SdJwt,
        ): Result<DisclosedClaimSet> = runCatching {
            fun doDisclose(x: SdJwtDsl): DisclosedClaimSet = when (x) {
                is SdJwtDsl.Plain -> {
                    DisclosedClaimSet(emptySet(), JsonObject(x.claims))
                }
                is SdJwtDsl.Flat -> {
                    val dh =
                        DisclosuresAndHashes.make(hashAlgorithm, saltProvider, JsonObject(x.claims), numOfDecoys)

                    dh.run {
                        if (hashes.isEmpty()) Empty
                        else
                            DisclosedClaimSet(
                                disclosures,
                                buildJsonObject {
                                    putJsonArray("_sd") { addAll(hashes.map { it.toJson() }) }
                                },
                            )
                    }
                }
                is SdJwtDsl.Structured -> {
                    val flatSubClaims = doDisclose(x.flatSubClaims)
                    val structuredSubClaims = x.structuredSubClaims.map { doDisclose(it) }.fold(Empty, DisclosedClaimSet::combine)
                    val combined = JsonObject(flatSubClaims.claimSet + x.plainSubClaims.claims + structuredSubClaims.claimSet)
                    val repackaged = buildJsonObject { put(x.claimName, combined) }
                    DisclosedClaimSet(flatSubClaims.disclosures + structuredSubClaims.disclosures, repackaged)
                }
                is SdJwtDsl.SdJwt -> {
                    val plainClaims = doDisclose(x.plainClaims)
                    val flatClaims = x.flatClaims?.let { doDisclose(it) } ?: Empty
                    val structuredClaims = x.structuredClaims.map { doDisclose(it) }
                    val disclosableClaims = structuredClaims + flatClaims
                    val hashClaim =
                        if (disclosableClaims.any { it.claimSet.isNotEmpty() }) hashClaim(hashAlgorithm)
                        else Empty
                    val all = disclosableClaims + plainClaims + hashClaim
                    all.fold(Empty, DisclosedClaimSet::combine)
                }
            }
            doDisclose(dsl)
        }

        /**
         * Combines two [DisclosedClaimSet]
         *
         * @return a new [DisclosedClaimSet] which contains the combined set of [DisclosedClaimSet.claimSet] and the
         * combined [DisclosedClaimSet.claimSet]
         */
        private fun combine(a: DisclosedClaimSet, b: DisclosedClaimSet): DisclosedClaimSet {
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
private data class DisclosuresAndHashes(
    val disclosures: Set<Disclosure>,
    val hashes: SortedSet<HashedDisclosure>,
    val hashAlgorithm: HashAlgorithm,
) {

    private constructor(
        disclosures: Set<Disclosure>,
        unsortedHashes: Set<HashedDisclosure>,
        hashAlgorithm: HashAlgorithm,
    ) : this(disclosures, unsortedHashes.toSortedSet(kotlin.Comparator.comparing { it.value }), hashAlgorithm)

    init {
        require(hashes.size >= disclosures.size) {
            "Hashes should be at least as disclosures"
        }
    }

    companion object {

        fun decoys(
            hashAlgorithm: HashAlgorithm,
            decoyGen: DecoyGen = DecoyGen.Default,
            numOfDecoys: Int,
        ): DisclosuresAndHashes {
            val decoys = decoyGen.gen(hashAlgorithm, numOfDecoys).toSortedSet(Comparator.comparing { it.value })
            return DisclosuresAndHashes(emptySet(), decoys, hashAlgorithm)
        }

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
                unsortedHashes = a.hashes + b.hashes,
                hashAlgorithm = a.hashAlgorithm,
            )
        }

        /**
         * Factory method for calculating a [DisclosuresAndHashes] for an input [claimsToBeDisclosed]
         * Method calculates the disclosures and the hashes for every attribute of the [claimsToBeDisclosed]
         * and then [combines][combine] them into a single [DisclosuresAndHashes]
         *
         * Each claim of [claimsToBeDisclosed] is treated as a block that can either be disclosed completely or not at all.
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
            val decoys = decoys(hashAlgorithm = hashAlgorithm, numOfDecoys = numOfDecoys)
            return claimsToBeDisclosed
                .map { claim -> make(hashAlgorithm, saltProvider, claim.toPair()) }
                .fold(decoys, DisclosuresAndHashes::combine)
        }

        fun make(
            hashAlgorithm: HashAlgorithm,
            saltProvider: SaltProvider,
            claimToBeDisclosed: Claim,
        ): DisclosuresAndHashes {
            val d = Disclosure.encode(saltProvider, claimToBeDisclosed).getOrThrow()
            val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
            return DisclosuresAndHashes(setOf(d), setOf(h), hashAlgorithm)
        }
    }
}

private fun HashedDisclosure.toJson() = JsonPrimitive(value)

private fun HashAlgorithm.toClaim() = "_sd_alg" to toJson()
private fun HashAlgorithm.toJson() = JsonPrimitive(alias)
private fun <T> assertNoCommonElements(a: Iterable<T>, b: Iterable<T>, lazyMessage: () -> Any) {
    require(a.intersect(b.toSet()).isEmpty(), lazyMessage)
}
