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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 */
object SdJwtElementDiscloserFactory {

    /**
     *  @param hashAlgorithm the hash algorithm to be used for hashing disclosures
     *  @param saltProvider the [Salt] generator used to the calculation of [Disclosure]
     *  @param numOfDecoys an upper limit of the decoys to be used
     */
    fun create(
        hashAlgorithm: HashAlgorithm = DefaultHashClaims.hashAlgorithm,
        saltProvider: SaltProvider = SaltProvider.Default,
        numOfDecoys: Int = 0,
    ): SdJwtElementDiscloser<JsonElement, JsonObject> =
        create(hashClaims(hashAlgorithm, saltProvider, numOfDecoys))

    /**
     * Create a [SdJwtElementDiscloser] combatible with KotlinX Serialization
     * @param hashClaims A function for making [disclosures][Disclosure] & [hashes][HashedDisclosure], possibly
     * including decoys
     * @return a [SdJwtElementDiscloser] combatible with KotlinX Serialization
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun create(hashClaims: HashClaims<JsonElement>): SdJwtElementDiscloser<JsonElement, JsonObject> =
        SdJwtElementDiscloser(
            additionOfClaims = AdditionOfClaims,
            createObjectFromClaims = { JsonObject(it) },
            nestClaims = { claimName, jsonObject -> buildJsonObject { put(claimName, jsonObject) } },
            hashClaims = hashClaims,
            createHashClaim = { hs -> buildJsonObject { putJsonArray("_sd") { addAll(hs.map { it.value }) } } },
            createHashAlgClaim = { h -> buildJsonObject { put("_sd_alg", JsonPrimitive(h.alias)) } },
        )

    private fun hashClaims(
        hashAlgorithm: HashAlgorithm,
        saltProvider: SaltProvider,
        numOfDecoys: Int,
    ): HashClaims<JsonElement> = object : HashClaims<JsonElement> {
        override val hashAlgorithm: HashAlgorithm
            get() = hashAlgorithm

        override fun invoke(claims: Claims<JsonElement>): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
            return DisclosuresAndHashes.make(hashAlgorithm, saltProvider, claims, numOfDecoys).run {
                disclosures to hashes
            }
        }
    }

    private val AdditionOfClaims = object : Addition<JsonObject> {
        override val zero: JsonObject = JsonObject(emptyMap())
        override fun invoke(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a + b)
    }

    private val DefaultHashClaims: HashClaims<JsonElement> =
        hashClaims(HashAlgorithm.SHA_256, SaltProvider.Default, 4)
}

/**
 * Allows the convenient use of [SdJwtElementsBuilder]
 * @param builderAction the usage of the builder
 * @return the set of [SD-JWT elements][SdJwtElement]
 */
@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtElementsBuilder.() -> Unit): Set<SdJwtElement<JsonElement>> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val v = SdJwtElementsBuilder()
    v.builderAction()
    return v.build()
}

/**
 * Builder for conveniently assembling
 * a set of [SD-JWT elements][SdJwtElement]
 */
class SdJwtElementsBuilder
    @PublishedApi
    internal constructor() {

        /**
         * Accumulates plain claims
         */
        private val plainClaims = mutableMapOf<String, JsonElement>()

        /**
         * Accumulates claims to be disclosed in flat manner
         */
        private val flatClaims = mutableMapOf<String, JsonElement>()

        /**
         * Accumulates claims to be disclosed in structured manner
         */
        private val structuredClaims = mutableSetOf<SdJwtElement.StructuredDisclosed<JsonElement>>()

        /**
         * Adds plain claims
         * @param cs claims to add
         */
        fun plain(cs: Claims<JsonElement>) {
            plainClaims.putAll(cs)
        }

        /**
         * Adds plain claims
         * @param builderAction a usage of a json builder
         */
        fun plain(builderAction: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(builderAction))
        }

        fun flat(cs: Claims<JsonElement>) {
            flatClaims.putAll(cs)
        }

        fun flat(builderAction: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(builderAction))
        }

        /**
         * Adds a structured claim where the given [flatSubClaims] will be flat disclosed.
         * That is the structured claim won't contain neither plain nor structured subclaims
         * @param claimName the name of the structured claim
         * @param flatSubClaims the usage of the builder
         */
        fun structuredWithFlatClaims(claimName: String, flatSubClaims: Claims<JsonElement>) {
            val element = SdJwtElement.StructuredDisclosed(claimName, setOf(SdJwtElement.FlatDisclosed(flatSubClaims)))
            structuredClaims.add(element)
        }

        /**
         * Adds a structured claim using, recursively, the builder
         * @param claimName the name of the structured claim
         * @param builderAction the usage of the builder
         */
        fun structured(claimName: String, builderAction: SdJwtElementsBuilder.() -> Unit) {
            val element = SdJwtElement.StructuredDisclosed(claimName, sdJwt(builderAction))
            structuredClaims.add(element)
        }

        fun build(): Set<SdJwtElement<JsonElement>> =
            buildSet {
                add(SdJwtElement.Plain(plainClaims))
                add(SdJwtElement.FlatDisclosed(flatClaims))
                addAll(structuredClaims)
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

        private fun decoys(
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
            claimsToBeDisclosed: Map<String, JsonElement>,
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

private fun <T> assertNoCommonElements(a: Iterable<T>, b: Iterable<T>, lazyMessage: () -> Any) {
    require(a.intersect(b.toSet()).isEmpty(), lazyMessage)
}
