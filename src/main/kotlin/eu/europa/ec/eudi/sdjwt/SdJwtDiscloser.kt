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

typealias SdJwtElements = Set<SdJwtElement<JsonElement>>
private typealias HashClaims = (Claims<JsonElement>) -> Pair<Set<Disclosure>, Set<HashedDisclosure>>

object SdJwtDiscloser {

    @OptIn(ExperimentalSerializationApi::class)
    private fun elementDiscloser(hashClaims: HashClaims): SdJwtElementDiscloser<JsonElement, JsonObject> =
        SdJwtElementDiscloser(
            additionOfClaims = object : Addition<JsonObject> {
                override val zero: JsonObject = JsonObject(emptyMap())
                override fun invoke(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a + b)
            },
            createObjectFromClaims = { JsonObject(it) },
            nestClaims = { claimName, jsonObject -> buildJsonObject { put(claimName, jsonObject) } },
            hashClaims = hashClaims,
            createObjectsFromHashes = { hs -> buildJsonObject { putJsonArray("_sd") { addAll(hs.map { it.value }) } } },
        )

    private fun DisclosedClaims<JsonObject>.addHashAlg(h: HashAlgorithm) =
        if (disclosures.isEmpty()) this
        else copy(claimSet = JsonObject(claimSet + ("_sd_alg" to JsonPrimitive(h.alias))))

    fun disclose(
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        numOfDecoys: Int = 0,
        sdJwt: SdJwtElements,
    ): Result<DisclosedClaims<JsonObject>> {
        fun hashClaims(cs: Claims<JsonElement>): Pair<Set<Disclosure>, Set<HashedDisclosure>> =
            DisclosuresAndHashes.make(hashAlgorithm, saltProvider, cs, numOfDecoys).run {
                disclosures to hashes
            }

        return elementDiscloser { hashClaims(it) }.disclose(sdJwt).map { claims -> claims.addHashAlg(hashAlgorithm) }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtElementsBuilder.() -> Unit): Set<SdJwtElement<JsonElement>> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val v = SdJwtElementsBuilder()
    v.builderAction()
    return v.build()
}

/**
 * Builder for conveniently assembling
 * a set of [SdJwtElement]
 */
class SdJwtElementsBuilder
    @PublishedApi
    internal constructor() {

        private val plainClaims = mutableMapOf<String, JsonElement>()
        private val flatClaims = mutableMapOf<String, JsonElement>()
        private val structuredClaims = mutableSetOf<SdJwtElement.StructuredDisclosed<JsonElement>>()

        fun plain(cs: Claims<JsonElement>) {
            plainClaims.putAll(cs)
        }

        fun plain(usage: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(usage))
        }

        fun flat(cs: Claims<JsonElement>) {
            flatClaims.putAll(cs)
        }

        fun flat(usage: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(usage))
        }

        fun structuredWithFlatClaims(claimName: String, claims: Claims<JsonElement>) {
            val element = SdJwtElement.StructuredDisclosed(claimName, setOf(SdJwtElement.FlatDisclosed(claims)))
            structuredClaims.add(element)
        }

        fun structured(claimName: String, action: SdJwtElementsBuilder.() -> Unit) {
            val element = SdJwtElement.StructuredDisclosed(claimName, sdJwt(action))
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
