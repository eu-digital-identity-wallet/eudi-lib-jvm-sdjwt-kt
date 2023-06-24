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
 */
data class DisclosedClaims<JO>(val disclosures: Set<Disclosure>, val claimSet: JO) {

    fun <JO2> mapClaims(f: (JO) -> JO2): DisclosedClaims<JO2> = DisclosedClaims(disclosures, f(claimSet))

    companion object {

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

interface Addition<V> : (V, V) -> V {
    val zero: V
    override operator fun invoke(a: V, b: V): V
}

class SdJwtElementDiscloser<JE, JO>(
    additionOfClaims: Addition<JO>,
    private val createObjectFromClaims: (Claims<JE>) -> JO,
    private val nestClaims: (String, JO) -> JO,
    private val hashClaims: (Claims<JE>) -> Pair<Set<Disclosure>, Set<HashedDisclosure>>,
    private val createObjectsFromHashes: (Set<HashedDisclosure>) -> JO,
) {

    private val additionOfDisclosedClaims: Addition<DisclosedClaims<JO>> =
        DisclosedClaims.addition(additionOfClaims)

    private fun discloseElement(element: SdJwtElement<JE>): DisclosedClaims<JO> {
        fun plain(cs: Claims<JE>): DisclosedClaims<JO> {
            val claimSet = createObjectFromClaims(cs)
            return additionOfDisclosedClaims.zero.copy(claimSet = claimSet)
        }

        fun flat(cs: Claims<JE>): DisclosedClaims<JO> =
            if (cs.isEmpty()) additionOfDisclosedClaims.zero
            else {
                val (ds, hs) = hashClaims(cs)
                DisclosedClaims(ds, createObjectsFromHashes(hs))
            }

        fun structured(claimName: String, es: Set<SdJwtElement<JE>>): DisclosedClaims<JO> =
            disclose(es).getOrThrow().mapClaims { claims -> nestClaims(claimName, claims) }

        return when (element) {
            is Plain -> plain(element.claims)
            is FlatDisclosed -> flat(element.claims)
            is StructuredDisclosed -> structured(element.claimName, element.elements)
        }
    }

    fun disclose(sdJwtElements: Set<SdJwtElement<JE>>): Result<DisclosedClaims<JO>> = runCatching {
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
}
