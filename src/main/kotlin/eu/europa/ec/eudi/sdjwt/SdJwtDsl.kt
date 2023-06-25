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

                override fun invoke(
                    a: DisclosedClaims<JO>,
                    b: DisclosedClaims<JO>,
                ): DisclosedClaims<JO> =
                    DisclosedClaims(a.disclosures + b.disclosures, claimAddition(a.claimSet, b.claimSet))
            }
    }
}

/**
 * A representation of an addition on [V]
 *
 * @param V the type for which addition is defined
 */
interface Addition<V> : (V, V) -> V {
    val zero: V
    override operator fun invoke(a: V, b: V): V
}

/**
 * A function for making [disclosures][Disclosure] & [hashes][HashedDisclosure], possibly
 * including decoys
 */
interface HashClaims<JE> {
    val hashAlgorithm: HashAlgorithm
    operator fun invoke(claims: Claims<JE>): Pair<Set<Disclosure>, Set<HashedDisclosure>>
}

/**
 *
 *
 * @param additionOfClaims an addition for the [JO]
 * @param createObjectFromClaims a method for creating a [JO] given a [set of claims][Claims]
 * @param nestClaims
 * @param hashClaims
 * @param createHashClaim a method for creating the hash claim ( an array of [HashedDisclosure] with the attibuted _sd)
 * @param JE the type representing a JSON element (object, array, primitive, etc)
 * @param JO the type representing a JSON object
 */
class SdJwtElementDiscloser<JE, JO>(
    private val additionOfClaims: Addition<JO>,
    private val createObjectFromClaims: (Claims<JE>) -> JO,
    private val nestClaims: (String, JO) -> JO,
    private val hashClaims: HashClaims<JE>,
    private val createHashClaim: (Set<HashedDisclosure>) -> JO,
    private val createHashAlgClaim: (HashAlgorithm) -> JO,
) {

    /**
     * The addition of [DisclosedClaims]
     */
    private val additionOfDisclosedClaims: Addition<DisclosedClaims<JO>> =
        DisclosedClaims.addition(additionOfClaims)

    /**
     * Discloses a single [element]
     * @param element the element to disclose
     * @return the [DisclosedClaims] for the given [element]
     */
    private fun discloseElement(element: SdJwtElement<JE>): DisclosedClaims<JO> {
        fun plain(cs: Claims<JE>): DisclosedClaims<JO> =
            if (cs.isEmpty()) additionOfDisclosedClaims.zero
            else {
                val claimSet = createObjectFromClaims(cs)
                additionOfDisclosedClaims.zero.copy(claimSet = claimSet)
            }

        fun flat(cs: Claims<JE>): DisclosedClaims<JO> =
            if (cs.isEmpty()) additionOfDisclosedClaims.zero
            else {
                val (ds, hs) = hashClaims(cs)
                DisclosedClaims(ds, createHashClaim(hs))
            }

        fun structured(claimName: String, es: Set<SdJwtElement<JE>>): DisclosedClaims<JO> =
            disclose(es).getOrThrow().mapClaims { claims -> nestClaims(claimName, claims) }

        return when (element) {
            is Plain -> plain(element.claims)
            is FlatDisclosed -> flat(element.claims)
            is StructuredDisclosed -> structured(element.claimName, element.elements)
        }
    }

    private fun disclose(sdJwtElements: Set<SdJwtElement<JE>>): Result<DisclosedClaims<JO>> = runCatching {
        tailrec fun discloseAccumulating(
            es: Set<SdJwtElement<JE>>,
            acc: DisclosedClaims<JO>,
        ): DisclosedClaims<JO> =
            if (es.isEmpty()) acc
            else {
                val e = es.first()
                val disclosedClaims = discloseElement(e)
                val newAcc = additionOfDisclosedClaims(acc, disclosedClaims)
                discloseAccumulating(es - e, newAcc)
            }

        discloseAccumulating(sdJwtElements, additionOfDisclosedClaims.zero)
    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hashing algorithm used for calculating hashes
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims<JO>.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims<JO> {
        return if (disclosures.isEmpty()) this
        else mapClaims {
            additionOfClaims(it, createHashAlgClaim(hashClaims.hashAlgorithm))
        }
    }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements]
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: Set<SdJwtElement<JE>>): Result<DisclosedClaims<JO>> =
        disclose(sdJwtElements)
            .map { disclosedClaims -> disclosedClaims.addHashAlgClaim(hashClaims.hashAlgorithm) }
}
