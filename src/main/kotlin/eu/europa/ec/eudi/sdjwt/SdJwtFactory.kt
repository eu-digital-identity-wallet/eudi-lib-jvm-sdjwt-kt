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

import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.json.JsonDisclosableElement
import eu.europa.ec.eudi.sdjwt.dsl.json.JsonElementDisclosableArray
import eu.europa.ec.eudi.sdjwt.dsl.json.JsonElementDisclosableObject
import kotlinx.serialization.json.*
import kotlin.random.Random

private typealias EncodedSdElement = Pair<JsonObject, List<Disclosure>>

@JvmInline
value class MinimumDigests(val value: Int) {
    init {
        require(value > 0) { "value must be greater than zero." }
    }

    operator fun plus(that: MinimumDigests) = MinimumDigests(this.value + that.value)
}

fun Int?.atLeastDigests(): MinimumDigests? = this?.let { MinimumDigests(it) }

/**
 * Factory for creating an unsigned JWT
 *
 * @param hashAlgorithm the algorithm to calculate the [DisclosureDigest]
 * @param saltProvider provides [Salt] for the calculation of [Disclosure]
 * @param fallbackMinimumDigests This is an optional hint, that expresses the number of digests on the immediate level
 * of every [DisclosableObject]. It will be taken into account if there is not an explicitly
 * defined [hint][DisclosableObject.minimumDigests] for
 * this [DisclosableObject]. If not provided, decoys will be added only if there is a hint at [DisclosableObject] level.
 */
@Suppress("ktlint:standard:max-line-length")
class SdJwtFactory(
    private val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    private val saltProvider: SaltProvider = SaltProvider.Default,
    private val decoyGen: DecoyGen = DecoyGen.Default,
    private val fallbackMinimumDigests: MinimumDigests? = null,
) {

    /**
     * Calculates a unsigned JWT for a given [SD-JWT element][sdJwtSpec].
     *
     * @param sdJwtSpec the contents of the SD-JWT
     * @return the unsigned JWT for a given [SD-JWT element][sdJwtSpec]
     */
    @Deprecated(
        message = "To be removed",
        replaceWith = ReplaceWith("createSdJwt(sdJwtSpec.migrate())"),
    )
    fun createSdJwt(
        sdJwtSpec: DisclosableObject,
    ): Result<SdJwt<JsonObject>> = runCatching {
        val migrated = sdJwtSpec.migrate()
        createSdJwt(migrated).getOrThrow()
    }

    /**
     * Creates an SD-JWT from the provided disclosable object specification.
     *
     * @param sdJwtSpec the specification of the SD-JWT, including claims and their associated disclosures
     * @return a [Result] containing the generated [SdJwt] with a [JsonObject] representing the JWT part
     *         and the associated disclosure elements, or an exception if the operation fails
     */
    fun createSdJwt(sdJwtSpec: JsonElementDisclosableObject): Result<SdJwt<JsonObject>> = runCatching {
//        val (jwtClaimSet, disclosures) = encodeObject(sdJwtSpec).addHashAlgClaim(hashAlgorithm)
//        SdJwt(jwtClaimSet, disclosures)
        val f = EnhancedSdJwtFactory(hashAlgorithm, saltProvider, decoyGen, fallbackMinimumDigests)
        f.createSdJwt(sdJwtSpec).getOrThrow()
    }

    private val encodeObject: DeepRecursiveFunction<JsonElementDisclosableObject, EncodedSdElement> =
        DeepRecursiveFunction { disclosableObject ->
            val disclosures = mutableListOf<Disclosure>()
            val encodedClaims = mutableMapOf<String, JsonElement>()

            // Add the given claim to encodedClaims
            fun add(encodedClaim: JsonObject) {
                val mergedSdClaim = JsonArray(encodedClaims.sdClaim() + encodedClaim.sdClaim())
                encodedClaims += encodedClaim
                if (mergedSdClaim.isNotEmpty()) {
                    encodedClaims[SdJwtSpec.CLAIM_SD] = mergedSdClaim
                }
            }

            // Adds decoys if needed
            fun addDecoysIfNeeded() {
                val digests = encodedClaims.sdClaim()
                val decoys =
                    genDecoys(digests.size, disclosableObject.minimumDigests).map { JsonPrimitive(it.value) }
                val digestAndDecoys = (digests + decoys).sortedBy { it.jsonPrimitive.contentOrNull }
                if (digestAndDecoys.isNotEmpty()) {
                    encodedClaims[SdJwtSpec.CLAIM_SD] = JsonArray(digestAndDecoys)
                }
            }

            for ((subClaimName, subClaimValue) in disclosableObject.content) {
                val (encodedSubClaim, subClaimDisclosures) = encodeClaim.callRecursive(subClaimName to subClaimValue)
                disclosures += subClaimDisclosures
                add(encodedSubClaim)
            }

            addDecoysIfNeeded()
            val sdObjectClaims = JsonObject(encodedClaims)
            sdObjectClaims to disclosures
        }

    private val encodeArray: DeepRecursiveFunction<JsonElementDisclosableArray, Pair<JsonArray, List<Disclosure>>> =
        DeepRecursiveFunction { array ->
            fun PlainOrDigest.toJsonElement(): JsonElement = when (this) {
                is PlainOrDigest.Plain -> value
                is PlainOrDigest.Dig -> value.asDigestClaim()
            }

            val disclosures = mutableListOf<Disclosure>()
            val plainOrDigestElements = mutableListOf<PlainOrDigest>()

            array.content.forEach {
                val (disclosuresToAdd, elementToAdd) = encodeArrayElement.callRecursive(it)
                disclosures += disclosuresToAdd
                plainOrDigestElements += elementToAdd
            }

            val actualDisclosureDigests = plainOrDigestElements.filterIsInstance<PlainOrDigest.Dig>().size
            val decoys = genDecoys(actualDisclosureDigests, array.minimumDigests).map { PlainOrDigest.Dig(it) }
            decoys.forEach { decoy ->
                val index = Random.nextInt(plainOrDigestElements.size + 1)
                plainOrDigestElements.add(index, decoy)
            }

            val allElements = JsonArray(plainOrDigestElements.map { it.toJsonElement() })

            allElements to disclosures
        }

    private val encodeClaim: DeepRecursiveFunction<Pair<String, JsonDisclosableElement>, EncodedSdElement> =
        DeepRecursiveFunction { (claimName, disclosableElement) ->
            fun encodeAlwaysDisclosableElement(disclosable: JsonElement): EncodedSdElement {
                val plainClaim = JsonObject(mapOf(claimName to disclosable))
                return plainClaim to emptyList()
            }

            fun encodeSelectivelyDisclosableElement(disclosable: JsonElement): EncodedSdElement {
                val claim = claimName to disclosable
                val (disclosure, digest) = objectPropertyDisclosure(claim)
                val digestAndDecoys = setOf(digest)
                val sdClaim = digestAndDecoys.sdClaim()
                return sdClaim to listOf(disclosure)
            }

            val encodeAlwaysDisclosableObject: DeepRecursiveFunction<JsonElementDisclosableObject, EncodedSdElement> =
                DeepRecursiveFunction { disclosable ->
                    val (encodedSubClaims, disclosures) = encodeObject.callRecursive(disclosable)
                    val structuredSdClaim = JsonObject(mapOf(claimName to encodedSubClaims))
                    structuredSdClaim to disclosures
                }

            val encodeSelectivelyDisclosableObject: DeepRecursiveFunction<JsonElementDisclosableObject, EncodedSdElement> =
                DeepRecursiveFunction { disclosable ->
                    val (contentClaims, contentDisclosures) = encodeObject.callRecursive(disclosable)
                    val wrapper = contentClaims
                    val (wrapperClaim, wrapperDisclosures) = encodeSelectivelyDisclosableElement(wrapper)
                    val disclosures = contentDisclosures + wrapperDisclosures
                    wrapperClaim to disclosures
                }

            val encodeAlwaysDisclosableArray: DeepRecursiveFunction<JsonElementDisclosableArray, EncodedSdElement> =
                DeepRecursiveFunction { disclosable ->
                    val (allElements, disclosures) = encodeArray.callRecursive(disclosable)
                    val arrayClaim = JsonObject(mapOf(claimName to allElements))
                    arrayClaim to disclosures
                }

            val encodeSelectivelyDisclosableArray: DeepRecursiveFunction<JsonElementDisclosableArray, EncodedSdElement> =
                DeepRecursiveFunction { disclosable ->
                    val (contentClaims, contentDisclosures) = encodeAlwaysDisclosableArray.callRecursive(disclosable)
                    val wrapper = checkNotNull(contentClaims[claimName])
                    val (wrapperClaim, wrapperDisclosures) = encodeSelectivelyDisclosableElement(wrapper)
                    val disclosures = contentDisclosures + wrapperDisclosures
                    wrapperClaim to disclosures
                }

            when (disclosableElement) {
                is Disclosable.AlwaysSelectively<DisclosableValue<String, JsonElement>> -> {
                    when (val element = disclosableElement.value) {
                        is DisclosableValue.Id<String, JsonElement> -> {
                            encodeSelectivelyDisclosableElement(element.value)
                        }
                        is DisclosableValue.Arr<String, JsonElement> -> {
                            encodeSelectivelyDisclosableArray.callRecursive(element.value as JsonElementDisclosableArray)
                        }
                        is DisclosableValue.Obj<String, JsonElement> -> {
                            encodeSelectivelyDisclosableObject.callRecursive(element.value as JsonElementDisclosableObject)
                        }
                    }
                }
                is Disclosable.NeverSelectively<DisclosableValue<String, JsonElement>> -> {
                    when (val element = disclosableElement.value) {
                        is DisclosableValue.Id<String, JsonElement> -> {
                            encodeAlwaysDisclosableElement(element.value)
                        }
                        is DisclosableValue.Arr<String, JsonElement> -> {
                            encodeAlwaysDisclosableArray.callRecursive(element.value as JsonElementDisclosableArray)
                        }
                        is DisclosableValue.Obj<String, JsonElement> -> {
                            encodeAlwaysDisclosableObject.callRecursive(element.value as JsonElementDisclosableObject)
                        }
                    }
                }
            }
        }

    private val encodeArrayElement: DeepRecursiveFunction<JsonDisclosableElement, Pair<List<Disclosure>, PlainOrDigest>> =
        DeepRecursiveFunction { disclosableElement ->

            fun encodeAlwaysDisclosableElement(disclosable: JsonElement): Pair<List<Disclosure>, PlainOrDigest> {
                return (emptyList<Disclosure>() to PlainOrDigest.Plain(disclosable))
            }

            fun disclosureOf(jsonElement: JsonElement): Pair<Disclosure, DisclosureDigest> {
                val disclosure = Disclosure.arrayElement(saltProvider, jsonElement).getOrThrow()
                val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
                return disclosure to digest
            }

            fun encodeSelectivelyDisclosableElement(disclosable: JsonElement): Pair<List<Disclosure>, PlainOrDigest> {
                val (disclosure, digest) = disclosureOf(disclosable)
                return listOf(disclosure) to PlainOrDigest.Dig(digest)
            }

            val encodeAlwaysDisclosableObject: DeepRecursiveFunction<JsonElementDisclosableObject, Pair<List<Disclosure>, PlainOrDigest>> =
                DeepRecursiveFunction { disclosable ->
                    val (json, ds) = encodeObject.callRecursive(disclosable)
                    ds to PlainOrDigest.Plain(json)
                }

            val encodeSelectivelyDisclosableObject:
                DeepRecursiveFunction<JsonElementDisclosableObject, Pair<List<Disclosure>, PlainOrDigest>> =
                DeepRecursiveFunction { disclosable ->
                    val (json, ds) = encodeObject.callRecursive(disclosable)
                    val (ds2, dig) = disclosureOf(json)
                    (ds + ds2) to PlainOrDigest.Dig(dig)
                }

            val encodeAlwaysDisclosableArray: DeepRecursiveFunction<JsonElementDisclosableArray, Pair<List<Disclosure>, PlainOrDigest>> =
                DeepRecursiveFunction { disclosable ->
                    val (json, ds) = encodeArray.callRecursive(disclosable)
                    ds to PlainOrDigest.Plain(json)
                }

            val encodeSelectivelyDisclosableArray: DeepRecursiveFunction<JsonElementDisclosableArray, Pair<List<Disclosure>, PlainOrDigest>> =
                DeepRecursiveFunction { disclosable ->
                    val (json, ds) = encodeArray.callRecursive(disclosable)
                    val (ds2, dig) = disclosureOf(json)
                    (ds + ds2) to PlainOrDigest.Dig(dig)
                }

            when (disclosableElement) {
                is Disclosable.AlwaysSelectively<DisclosableValue<String, JsonElement>> -> {
                    when (val element = disclosableElement.value) {
                        is DisclosableValue.Id<String, JsonElement> -> {
                            encodeSelectivelyDisclosableElement(element.value)
                        }
                        is DisclosableValue.Arr<String, JsonElement> -> {
                            check(element.value is JsonElementDisclosableArray)
                            encodeSelectivelyDisclosableArray.callRecursive(element.value)
                        }
                        is DisclosableValue.Obj<String, JsonElement> -> {
                            check(element.value is JsonElementDisclosableObject)
                            encodeSelectivelyDisclosableObject.callRecursive(element.value)
                        }
                    }
                }
                is Disclosable.NeverSelectively<DisclosableValue<String, JsonElement>> -> {
                    when (val element = disclosableElement.value) {
                        is DisclosableValue.Id<String, JsonElement> -> {
                            encodeAlwaysDisclosableElement(element.value)
                        }
                        is DisclosableValue.Arr<String, JsonElement> -> {
                            check(element.value is JsonElementDisclosableArray)
                            encodeAlwaysDisclosableArray.callRecursive(element.value)
                        }
                        is DisclosableValue.Obj<String, JsonElement> -> {
                            check(element.value is JsonElementDisclosableObject)
                            encodeAlwaysDisclosableObject.callRecursive(element.value)
                        }
                    }
                }
            }
        }

    /**
     * Adds the hash algorithm claim if disclosures are present
     * @param h the hash algorithm
     * @return a new [EncodedSdElement] with an updated claim to
     * contain the hash algorithm claim, if disclosures are present
     */
    private fun EncodedSdElement.addHashAlgClaim(h: HashAlgorithm): EncodedSdElement {
        val (jwtClaimSet, disclosures) = this
        return if (disclosures.isEmpty()) this
        else {
            val newClaimSet = JsonObject(jwtClaimSet + (SdJwtSpec.CLAIM_SD_ALG to JsonPrimitive(h.alias)))
            newClaimSet to disclosures
        }
    }

    /**
     * Generates decoys, if needed.
     *
     */
    private fun genDecoys(disclosureDigests: Int, minimumDigests: MinimumDigests?): Set<DisclosureDigest> {
        val min = (minimumDigests ?: fallbackMinimumDigests)?.value ?: 0
        val numOfDecoys = min - disclosureDigests
        return decoyGen.gen(hashAlgorithm, numOfDecoys)
    }

    private fun Set<DisclosureDigest>.sdClaim(): JsonObject =
        if (isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf(SdJwtSpec.CLAIM_SD to JsonArray(map { JsonPrimitive(it.value) })))

    private fun Map<String, JsonElement>.sdClaim(): List<JsonElement> =
        this[SdJwtSpec.CLAIM_SD]?.jsonArray ?: emptyList()

    private fun DisclosureDigest.asDigestClaim(): JsonObject {
        return JsonObject(mapOf(SdJwtSpec.CLAIM_ARRAY_ELEMENT_DIGEST to JsonPrimitive(value)))
    }

    private fun objectPropertyDisclosure(claim: Claim): Pair<Disclosure, DisclosureDigest> {
        val disclosure = Disclosure.objectProperty(saltProvider, claim).getOrThrow()
        val digest = DisclosureDigest.digest(hashAlgorithm, disclosure).getOrThrow()
        return disclosure to digest
    }

    companion object {

        /**
         * A default [SdJwtFactory] with the following options set
         * - SHA_256 hash algorithm
         * - [SaltProvider.Default]
         * - [DecoyGen.Default]
         * - No hint for [SdJwtFactory.fallbackMinimumDigests]
         */
        val Default: SdJwtFactory =
            SdJwtFactory(HashAlgorithm.SHA_256, SaltProvider.Default, DecoyGen.Default, null)
    }
}

private sealed interface PlainOrDigest {
    data class Plain(val value: JsonElement) : PlainOrDigest
    data class Dig(val value: DisclosureDigest) : PlainOrDigest
}
