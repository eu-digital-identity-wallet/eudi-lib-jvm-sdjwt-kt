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

/**
 * Representations of multiple claims
 *
 * @param JE type representing a JSON element (object, array, primitive etc)
 */
typealias Claims<JE> = Map<String, JE>

/**
 * A domain specific language for describing the payload of an SD-JWT
 * @param JE type representing a JSON element (object, array, primitive etc)
 */
sealed interface SdJwtElement<JE> {
    data class Plain<JE>(val claims: Claims<JE>) : SdJwtElement<JE>
    data class FlatDisclosed<JE>(val claims: Claims<JE>) : SdJwtElement<JE>
    data class StructuredDisclosed<JE>(val claimName: String, val elements: Set<SdJwtElement<JE>>) : SdJwtElement<JE>
}

/**
 * Represent a selectively disclosed Json object and the calculated disclosures
 *
 * @param disclosures the disclosures calculated
 * @param claimSet the JSON object that contains the hashed disclosures and possible plain claims
 * @param JO the type representing a JSON object
 */
data class DisclosedClaims<JO>(val disclosures: Set<Disclosure>, val claimSet: JO) {

    fun <JO2> mapClaims(f: (JO) -> JO2): DisclosedClaims<JO2> = DisclosedClaims(disclosures, f(claimSet))

    companion object {

        /**
         * Creates an [Addition] for the [DisclosedClaims] given an [addition for the claims][claimAddition]
         * @param claimAddition an addition for the [DisclosedClaims.claimSet]
         * @param JO the type representing a JSON object
         * @return an [Addition] for the [DisclosedClaims] given an [addition for the claims][claimAddition]
         */
        fun <JO> addition(claimAddition: Addition<JO>): Addition<DisclosedClaims<JO>> =
            object : Addition<DisclosedClaims<JO>> {
                override val zero: DisclosedClaims<JO> =
                    DisclosedClaims(emptySet(), claimAddition.zero)

                override fun add(
                    a: DisclosedClaims<JO>,
                    b: DisclosedClaims<JO>,
                ): DisclosedClaims<JO> =
                    DisclosedClaims(a.disclosures + b.disclosures, claimAddition.add(a.claimSet, b.claimSet))
            }
    }
}

/**
 * A representation of an addition on [V]
 *
 * @param V the type for which addition is defined
 */
interface Addition<V> {
    val zero: V
    fun add(a: V, b: V): V
}

interface JsonOperations<JE, JO> : Addition<JO> {
    fun createObjectFromClaims(cs: Claims<JE>): JO
    fun nestClaims(claimName: String, claims: JO): JO
    fun createArrayOfStrings(ss: Collection<String>): JE
    fun stringElement(s: String): JE
}

/**
 * A function for making [disclosures][Disclosure] & [hashes][HashedDisclosure], possibly
 * including decoys
 */
interface HashedDisclosureCreator<JE> {
    val hashAlgorithm: HashAlgorithm
    fun create(claims: Claims<JE>): Pair<Set<Disclosure>, Set<HashedDisclosure>>

    companion object {
        fun <JE> withDecoys(creator: HashedDisclosureCreator<JE>, numOfDecoys: Int): HashedDisclosureCreator<JE> =
            if (numOfDecoys >= 0) {
                object : HashedDisclosureCreator<JE> {
                    override val hashAlgorithm: HashAlgorithm = creator.hashAlgorithm

                    override fun create(claims: Claims<JE>): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
                        val (ds, hs) = creator.create(claims)
                        val decoys = DecoyGen.Default.gen(creator.hashAlgorithm)
                        val hashesWithDecoys = (hs + decoys).toSortedSet(Comparator.comparing { it.value })
                        return ds to hashesWithDecoys
                    }
                }
            } else creator
    }
}

fun interface DisclosureCreator<JE> {
    fun encode(saltProvider: SaltProvider, claim: Pair<String, JE>): Result<Disclosure>
}

class HashedDisclosureCreatorImpl<JE>(
    override val hashAlgorithm: HashAlgorithm,
    private val saltProvider: SaltProvider,
    private val disclosureCreator: DisclosureCreator<JE>,
) : HashedDisclosureCreator<JE> {

    override fun create(claims: Claims<JE>): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
        fun make(claim: Pair<String, JE>): Pair<Set<Disclosure>, Set<HashedDisclosure>> {
            val d = disclosureCreator.encode(saltProvider, claim).getOrThrow()
            val h = HashedDisclosure.create(hashAlgorithm, d).getOrThrow()
            return setOf(d) to setOf(h)
        }

        fun initial() = emptySet<Disclosure>() to emptySet<HashedDisclosure>()
        return claims
            .map { claim -> make(claim.toPair()) }
            .fold(initial()) { acc, pair -> combine(acc, pair) }
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

/**
 *
 *
 * @param numOfDecoys the number of decoys
 * @param JE the type representing a JSON element (object, array, primitive, etc)
 * @param JO the type representing a JSON object
 */
class DisclosuresCreator<JE, JO>(
    private val jsonOperations: JsonOperations<JE, JO>,
    hashedDisclosureCreator: HashedDisclosureCreator<JE>,
    numOfDecoys: Int = 0,
) {

    private val hashedDisclosureCreator: HashedDisclosureCreator<JE> =
        HashedDisclosureCreator.withDecoys(hashedDisclosureCreator, numOfDecoys)

    /**
     * Discloses a single [element]
     * @param element the element to disclose
     * @return the [DisclosedClaims] for the given [element]
     */
    private fun discloseElement(element: SdJwtElement<JE>): DisclosedClaims<JO> {
        fun plain(cs: Claims<JE>): DisclosedClaims<JO> =
            if (cs.isEmpty()) additionOfDisclosedClaims.zero
            else {
                val claimSet = jsonOperations.createObjectFromClaims(cs)
                additionOfDisclosedClaims.zero.copy(claimSet = claimSet)
            }

        fun flat(cs: Claims<JE>): DisclosedClaims<JO> =
            if (cs.isEmpty()) additionOfDisclosedClaims.zero
            else {
                val (ds, hs) = hashedDisclosureCreator.create(cs)
                val hashClaims: JO = with(jsonOperations) {
                    createObjectFromClaims(mapOf("_sd" to createArrayOfStrings(hs.map { it.value })))
                }
                DisclosedClaims(ds, hashClaims)
            }

        fun structured(claimName: String, es: Set<SdJwtElement<JE>>): DisclosedClaims<JO> =
            disclose(es).mapClaims { claims -> jsonOperations.nestClaims(claimName, claims) }

        return when (element) {
            is Plain -> plain(element.claims)
            is FlatDisclosed -> flat(element.claims)
            is StructuredDisclosed -> structured(element.claimName, element.elements)
        }
    }

    private fun disclose(sdJwtElements: Set<SdJwtElement<JE>>): DisclosedClaims<JO> {
        tailrec fun discloseAccumulating(
            es: Set<SdJwtElement<JE>>,
            acc: DisclosedClaims<JO>,
        ): DisclosedClaims<JO> =
            if (es.isEmpty()) acc
            else {
                val e = es.first()
                val disclosedClaims = discloseElement(e)
                val newAcc = additionOfDisclosedClaims.add(acc, disclosedClaims)
                discloseAccumulating(es - e, newAcc)
            }

        return discloseAccumulating(sdJwtElements, additionOfDisclosedClaims.zero)
    }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements]
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: Set<SdJwtElement<JE>>): Result<DisclosedClaims<JO>> = runCatching {
        disclose(sdJwtElements).addHashAlgClaim(hashedDisclosureCreator.hashAlgorithm)
    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hashing algorithm used for calculating hashes
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims<JO>.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims<JO> =
        if (disclosures.isEmpty()) this
        else mapClaims { claims ->
            with(jsonOperations) {
                val hashAlgClaim = createObjectFromClaims(mapOf("_sd_alg" to stringElement(h.alias)))
                add(claims, hashAlgClaim)
            }
        }

    /**
     * The addition of [DisclosedClaims]
     */
    private val additionOfDisclosedClaims: Addition<DisclosedClaims<JO>> = DisclosedClaims.addition(jsonOperations)
}
