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

import eu.europa.ec.eudi.sdjwt.SdJwtElement.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

/**
 */
class DisclosuresCreator(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    numOfDecoys: Int = 0,
) {

    private val hashedDisclosureCreator: HashedDisclosureCreator =
        HashedDisclosureCreator(hashAlgorithm, saltProvider, numOfDecoys)

    /**
     * Discloses a single [element]
     * @param element the element to disclose
     * @return the [DisclosedClaims] for the given [element]
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun discloseElement(element: SdJwtElement): DisclosedClaims {
        fun plain(cs: Claims): DisclosedClaims =
            if (cs.isEmpty()) DisclosedClaims.Empty
            else DisclosedClaims(emptySet(), JsonObject(cs))

        fun flat(cs: Claims): DisclosedClaims =
            if (cs.isEmpty()) DisclosedClaims.Empty
            else {
                val (ds, hs) = hashedDisclosureCreator.create(cs)
                val hashClaims = JsonObject(mapOf("_sd" to buildJsonArray { addAll(hs.map { it.value }) }))
                DisclosedClaims(ds, hashClaims)
            }

        fun structured(claimName: String, es: Set<SdJwtElement>): DisclosedClaims =
            disclose(es).mapClaims { claims -> buildJsonObject { put(claimName, claims) } }

        return when (element) {
            is Plain -> plain(element.claims)
            is FlatDisclosed -> flat(element.claims)
            is StructuredDisclosed -> structured(element.claimName, element.elements)
        }
    }

    private fun disclose(sdJwtElements: Set<SdJwtElement>): DisclosedClaims {
        tailrec fun discloseAccumulating(
            es: Set<SdJwtElement>,
            acc: DisclosedClaims,
        ): DisclosedClaims =
            if (es.isEmpty()) acc
            else {
                val e = es.first()
                val disclosedClaims = discloseElement(e)
                discloseAccumulating(es - e, acc + disclosedClaims)
            }

        return discloseAccumulating(sdJwtElements, DisclosedClaims.Empty)
    }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements]
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: Set<SdJwtElement>): Result<DisclosedClaims> = runCatching {
        disclose(sdJwtElements).addHashAlgClaim(hashedDisclosureCreator.hashAlgorithm)
    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hashing algorithm used for calculating hashes
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims =
        if (disclosures.isEmpty()) this
        else mapClaims { claims ->
            val hashAlgClaim = JsonObject(mapOf("_sd_alg" to JsonPrimitive(h.alias)))
            JsonObject(claims + hashAlgClaim)
        }
}

/**
 * A function for making [disclosures][Disclosure] & [hashes][HashedDisclosure], possibly
 * including decoys
 */
private class HashedDisclosureCreator(
    val hashAlgorithm: HashAlgorithm,
    private val saltProvider: SaltProvider,
    private val numOfDecoys: Int,
) {

    fun create(claims: Claims): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        val initial =
            emptySet<Disclosure>() to DecoyGen.Default.gen(hashAlgorithm, numOfDecoys).toSet()
        val (ds, hs) = claims
            .map { claim -> make(claim.toPair()) }
            .fold(initial) { acc, pair -> combine(acc, pair) }
        return ds to hs.toSortedSet(Comparator.comparing { it.value })
    }

    private fun make(claim: Claim): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        val d = Disclosure.encode(saltProvider, claim).getOrThrow()
        val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
        return setOf(d) to setOf(h)
    }

    private fun combine(
        a: Pair<Set<Disclosure>, Set<HashedDisclosure>>,
        b: Pair<Set<Disclosure>, Set<HashedDisclosure>>,
    ): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        fun <T> assertNoCommonElements(a: Iterable<T>, b: Iterable<T>, lazyMessage: () -> Any) {
            require(a.intersect(b.toSet()).isEmpty(), lazyMessage)
        }

        assertNoCommonElements(a.first, b.first) {
            "Cannot combine DisclosuresAndHashes with common disclosures"
        }
        assertNoCommonElements(a.second, b.second) {
            "Cannot combine DisclosuresAndHashes with common hashes"
        }

        return (a.first + b.first) to (a.second + b.second)
    }
}
