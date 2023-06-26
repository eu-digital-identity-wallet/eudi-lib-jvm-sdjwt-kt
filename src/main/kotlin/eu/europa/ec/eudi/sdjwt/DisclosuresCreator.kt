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
 * A class for [disclosing][disclose] a set of [SD-JWT elements][SdJwtElement].
 * In this context, [outcome][DisclosedClaims] of the disclosure is the calculation
 * of a set of [disclosures][DisclosedClaims.disclosures] and a [set of claims][DisclosedClaims.claimSet]
 * to be included in the payload of the SD-JWT.
 *
 * @param hashAlgorithm the algorithm to calculate the [HashedDisclosure]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param numOfDecoysLimit the upper limit of the decoys to generate
 */
class DisclosuresCreator(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    numOfDecoysLimit: Int = 0,
) {

    /**
     * Discloses a single [sdJwtElement]
     * @param sdJwtElement the element to disclose
     * @return the [DisclosedClaims] for the given [sdJwtElement]
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun discloseElement(sdJwtElement: SdJwtElement): DisclosedClaims {
        fun plain(cs: Claims): DisclosedClaims =
            if (cs.isEmpty()) DisclosedClaims.Empty
            else DisclosedClaims(emptySet(), JsonObject(cs))

        fun flat(cs: Claims, allowNestedHashClaim: Boolean = false): DisclosedClaims =
            if (cs.isEmpty()) DisclosedClaims.Empty
            else {
                val (ds, hs) = hashedDisclosureCreator.create(cs, allowNestedHashClaim)
                val hashClaims = JsonObject(mapOf("_sd" to buildJsonArray { addAll(hs.map { it.value }) }))
                DisclosedClaims(ds, hashClaims)
            }

        fun structured(claimName: String, es: List<SdJwtElement>): DisclosedClaims =
            disclose(es).mapClaims { claims -> buildJsonObject { put(claimName, claims) } }

        fun recursively(claimName: String, cs: Claims): DisclosedClaims {
            val (ds1, claimSet1) = flat(cs)
            val (ds2, claimSet2) = flat(mapOf(claimName to claimSet1), true)
            return DisclosedClaims(ds1 + ds2, claimSet2)
        }

        return when (sdJwtElement) {
            is Plain -> plain(sdJwtElement.claims)
            is FlatDisclosed -> flat(sdJwtElement.claims)
            is StructuredDisclosed -> structured(sdJwtElement.claimName, sdJwtElement.elements)
            is RecursivelyDisclosed -> recursively(sdJwtElement.claimName, sdJwtElement.claims)
        }
    }

    /**
     * Discloses a set of  [SD-JWT element][sdJwtElements]
     * @param sdJwtElements the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun disclose(sdJwtElements: List<SdJwtElement>): DisclosedClaims {
        tailrec fun discloseAccumulating(
            elements: List<SdJwtElement>,
            accumulated: DisclosedClaims,
        ): DisclosedClaims =
            if (elements.isEmpty()) accumulated
            else {
                val element = elements.first()
                val disclosedClaims = discloseElement(element)
                discloseAccumulating(elements - element, accumulated + disclosedClaims)
            }

        return discloseAccumulating(sdJwtElements, DisclosedClaims.Empty)
    }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: List<SdJwtElement>): Result<DisclosedClaims> = runCatching {
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

    /**
     * []Disclosure] and [HashedDisclosure] creator
     */
    private val hashedDisclosureCreator: HashedDisclosureCreator by lazy {
        HashedDisclosureCreator(hashAlgorithm, saltProvider, numOfDecoysLimit)
    }
}

/**
 * A class for [creating][create] [disclosures][Disclosure] and [hashes][HashedDisclosure], possibly
 * including decoys
 *
 * @param hashAlgorithm the algorithm to calculate the [HashedDisclosure]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param numOfDecoysLimit the upper limit of the decoys to generate
 */
private class HashedDisclosureCreator(
    val hashAlgorithm: HashAlgorithm,
    private val saltProvider: SaltProvider,
    private val numOfDecoysLimit: Int,
) {

    /**
     * Creates [disclosures][Disclosure] & [hashes][HashedDisclosure], possibly including decoys
     *
     * @param claims the claims for which to calculate [Disclosure] and [HashedDisclosure]
     * @return disclosures and hashes, possibly including decoys
     * */
    fun create(claims: Claims, allowNestedHashClaim: Boolean): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        val decoys = emptySet<Disclosure>() to DecoyGen.Default.genUpTo(hashAlgorithm, numOfDecoysLimit)
        val (ds, hs) = claims
            .map { claim -> make(claim.toPair(), allowNestedHashClaim) }
            .fold(decoys, this::combine)
        return ds to hs.toSortedSet(Comparator.comparing { it.value })
    }

    private fun make(claim: Claim, allowNestedHashClaim: Boolean): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        val d = Disclosure.encode(saltProvider, claim, allowNestedHashClaim).getOrThrow()
        val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
        return setOf(d) to setOf(h)
    }

    /**
     * Combines two pair of sets of disclosures & hashes into a new pair
     * @param a the first pair to combine
     * @param b the second pair to combine
     * @return the combines pair
     * @throws IllegalArgumentException in case the provided sets have common elements
     */
    private fun combine(
        a: Pair<Set<Disclosure>, Set<HashedDisclosure>>,
        b: Pair<Set<Disclosure>, Set<HashedDisclosure>>,
    ): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        assertNoCommonElements(a.first, b.first) {
            "Cannot combine DisclosuresAndHashes with common disclosures"
        }
        assertNoCommonElements(a.second, b.second) {
            "Cannot combine DisclosuresAndHashes with common hashes"
        }

        return (a.first + b.first) to (a.second + b.second)
    }

    private fun <T> assertNoCommonElements(a: Iterable<T>, b: Iterable<T>, lazyMessage: () -> Any) {
        require(a.intersect(b.toSet()).isEmpty(), lazyMessage)
    }
}
