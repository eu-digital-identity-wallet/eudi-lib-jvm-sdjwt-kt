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

val DefaultDisclosureCreator: DisclosuresCreator = DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, 0)

/**
 * A class for [disclosing][disclose] a set of [SD-JWT elements][SdJwtElement].
 * In this context, [outcome][DisclosedClaims] of the disclosure is the calculation
 * of a set of [disclosures][DisclosedClaims.disclosures] and a [set of claims][DisclosedClaims.claimSet]
 * to be included in the payload of the SD-JWT.
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param numOfDecoysLimit the upper limit of the decoys to generate
 */
class DisclosuresCreator(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val numOfDecoysLimit: Int = 0,
) {

    /**
     * Creates [disclosures][Disclosure] & [hashes][Disclosure], possibly including decoys,
     * depending on the [numOfDecoysLimit] provided
     *
     * @param claims the claims for which to calculate [DisclosedClaims] and [DisclosureDigest]
     *
     * @return disclosures and hashes, possibly including decoys
     * */
    internal fun disclosuresAndDigests(
        claims: Claims,
        allowNestedHashClaim: Boolean,
    ): Pair<Set<Disclosure.ObjectProperty>, Set<DisclosureDigest>> {
        val digestPerDisclosure = mutableMapOf<Disclosure.ObjectProperty, DisclosureDigest>()

        for (claim in claims) {
            val disclosure = Disclosure.objectProperty(saltProvider, claim.toPair(), allowNestedHashClaim).getOrThrow()
            val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
            digestPerDisclosure[disclosure] = digest
        }

        val decoys = DecoyGen.Default.genUpTo(hashAlgorithm, numOfDecoysLimit)
        val digests = digestPerDisclosure.values + decoys

        return digestPerDisclosure.keys to digests.toSortedSet(Comparator.comparing { it.value })
    }

    internal fun disclosureAndDigestArrayElement(e: JsonElement): Pair<Disclosure.ArrayElement, Set<DisclosureDigest>>{
        val disclosure = Disclosure.arrayElement(saltProvider, e).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm,disclosure).getOrThrow()
        val decoys = DecoyGen.Default.gen(hashAlgorithm, numOfDecoysLimit)
        val digests =  (decoys + digest)
        return disclosure to digests.toSortedSet(Comparator.comparing { it.value })
    }

    /**
     * Discloses a single [sdJwtElement]
     * @param sdJwtElement the element to disclose
     * @return the [DisclosedClaims] for the given [sdJwtElement]
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun discloseElement(sdJwtElement: SdJwtElement): DisclosedClaims {
        fun plain(claims: Claims): DisclosedClaims =
            if (claims.isEmpty()) DisclosedClaims.Empty
            else DisclosedClaims(emptySet(), JsonObject(claims))

        fun flat(claims: Claims, allowNestedHashClaim: Boolean = false): DisclosedClaims {
            val (disclosures, digests) = disclosuresAndDigests(claims, allowNestedHashClaim)
            val hashClaims =
                if (digests.isNotEmpty()) JsonObject(mapOf("_sd" to buildJsonArray { addAll(digests.map { it.value }) }))
                else JsonObject(emptyMap())
            return DisclosedClaims(disclosures, hashClaims)
        }

        fun structured(claimName: String, elements: List<SdJwtElement>): DisclosedClaims =
            disclose(elements).mapClaims { claims -> buildJsonObject { put(claimName, claims) } }

        fun recursively(claimName: String, claims: Claims): DisclosedClaims {
            val (ds1, claimSet1) = flat(claims)
            val (ds2, claimSet2) = flat(mapOf(claimName to claimSet1), allowNestedHashClaim = true)
            return DisclosedClaims(ds1 + ds2, claimSet2)
        }

        fun array(claimName: String, elements: List<SdArrayElement>): DisclosedClaims {
            return if (elements.isEmpty()) DisclosedClaims.Empty
            else {
                val ds = mutableSetOf<Disclosure.ArrayElement>()
                val elements1 = elements.map { e ->
                    when (e) {
                        is SdArrayElement.Plain -> e.element
                        is SdArrayElement.SelectivelyDisclosed -> {
                            val disclosure = Disclosure.arrayElement(saltProvider, e.element).getOrThrow()
                            val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
                            ds += disclosure
                            buildJsonObject { put("...", digest.value) }
                        }
                    }
                }
                DisclosedClaims(
                    ds,
                    buildJsonObject {
                        putJsonArray(claimName) {
                            addAll(elements1)
                        }
                    },
                )
            }
        }

        fun flatNested(nested: FlatNestable): DisclosedClaims {
            val (ds1, cs1) = disclose(listOf(nested))
            val (ds2, cs2) = flat(cs1, true)
            return DisclosedClaims(ds1 + ds2, cs2)
        }

        return when (sdJwtElement) {
            is Plain -> plain(sdJwtElement.claims)
            is FlatDisclosed -> flat(sdJwtElement.claims)
            is StructuredDisclosed -> structured(sdJwtElement.claimName, sdJwtElement.elements)
            is RecursivelyDisclosed -> recursively(sdJwtElement.claimName, sdJwtElement.claims)
            is SelectivelyDisclosedArray -> array(sdJwtElement.claimName, sdJwtElement.elements)
            is FlatNested -> flatNested(sdJwtElement.nested)
        }
    }

    /**
     * Discloses a set of  [SD-JWT element][sdJwtElements]
     * @param sdJwtElements the set of elements to disclose
     * @return the [DisclosedClaims]
     */
    private fun disclose(sdJwtElements: List<SdJwtElement>): DisclosedClaims {
        val accumulatedDisclosures = mutableSetOf<Disclosure>()
        val accumulatedJson = mutableMapOf<String, JsonElement>()

        for (element in sdJwtElements) {
            val (disclosures, json) = discloseElement(element)
            accumulatedDisclosures += disclosures
            accumulatedJson += json
        }

        return DisclosedClaims(accumulatedDisclosures, JsonObject(accumulatedJson))
    }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [DisclosedClaims] with an updated [DisclosedClaims.claimSet] to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun DisclosedClaims.addHashAlgClaim(h: HashAlgorithm): DisclosedClaims =
        if (disclosures.isEmpty()) this
        else mapClaims { claims ->
            val hashAlgClaim = "_sd_alg" to JsonPrimitive(h.alias)
            JsonObject(claims + hashAlgClaim)
        }

    /**
     * Calculates a [DisclosedClaims] for a given [SD-JWT element][sdJwtElements].
     *
     * @param sdJwtElements the contents of the SD-JWT
     * @return the [DisclosedClaims] for the given [SD-JWT element][sdJwtElements]
     */
    fun discloseSdJwt(sdJwtElements: List<SdJwtElement>): Result<DisclosedClaims> = runCatching {
        disclose(sdJwtElements).addHashAlgClaim(hashAlgorithm)
    }
}
