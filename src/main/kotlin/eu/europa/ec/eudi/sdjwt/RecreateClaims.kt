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

import kotlinx.serialization.json.*

/**
 * Recreates the claims, used to produce the SD-JWT
 * That are:
 * - The plain claims found into the [SdJwt.jwt]
 * - Digests found in [SdJwt.jwt] and/or [Disclosure] (in case of recursive disclosure) will
 *   be replaced by [Disclosure.claim]
 *
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to use
 * @return the claims that were used to produce the SD-JWT
 */
fun <JWT> SdJwt<JWT>.recreateClaims(claimsOf: (JWT) -> Claims): Claims {
    return recreateClaims(visitor = null, claimsOf = claimsOf)
}

/**
 * Recreates the claims, used to produce the SD-JWT
 * That are:
 * - The plain claims found into the [SdJwt.jwt]
 * - Digests found in [SdJwt.jwt] and/or [Disclosure] (in case of recursive disclosure) will
 *   be replaced by [Disclosure.claim]
 *
 * @param visitor [ClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to use
 * @return the claims that were used to produce the SD-JWT
 */
fun <JWT> SdJwt<JWT>.recreateClaims(visitor: ClaimVisitor? = null, claimsOf: (JWT) -> Claims): Claims {
    val disclosedClaims = JsonObject(claimsOf(jwt))
    return RecreateClaims(visitor).recreateClaims(disclosedClaims, disclosures)
}

fun UnsignedSdJwt.recreateClaims(visitor: ClaimVisitor? = null) = recreateClaims(visitor) { it }

/**
 * Visitor for selectively disclosed claims.
 */
fun interface ClaimVisitor {

    /**
     * Invoked whenever a selectively disclosed claim is encountered while recreating the claims of an [SdJwt].
     * @param pointer a JsonPointer to the current element
     * @param disclosure the disclosure of the current selectively disclosed element
     */
    operator fun invoke(pointer: JsonPointer, disclosure: Disclosure?)
}

private typealias DisclosurePerDigest = MutableMap<DisclosureDigest, Disclosure>

/**
 * @param visitor [ClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
 */
private class RecreateClaims(private val visitor: ClaimVisitor?) {

    fun recreateClaims(jwtClaims: JsonObject, disclosures: List<Disclosure>): Claims {
        val hashAlgorithm = jwtClaims.hashAlgorithm() ?: HashAlgorithm.SHA_256
        return discloseJwt(
            hashAlgorithm,
            JsonObject(jwtClaims - "_sd_alg"),
            disclosures,
        )
    }

    /**
     * Replaces the digests found within [jwtClaims] and/or [disclosures] with
     * the [disclosures]
     *
     * @param hashAlgorithm the hash algorithm used to produce the [digests][DisclosureDigest]
     * @param disclosures the disclosures to use when replacing digests
     * @param jwtClaims the claims of the JWT (except _sd_alg)
     *
     * @return the initial [jwtClaims] having all digests replaced by [Disclosure]
     * @throws IllegalStateException when not all [disclosures] have been used, which means
     * that [jwtClaims] doesn't contain a [DisclosureDigest] for every [Disclosure]
     */
    private fun discloseJwt(
        hashAlgorithm: HashAlgorithm,
        jwtClaims: JsonObject,
        disclosures: List<Disclosure>,
    ): JsonObject {
        // Recalculate digests, using the hash algorithm
        val disclosuresPerDigest = disclosures.associateBy {
            DisclosureDigest.digest(hashAlgorithm, it.value).getOrThrow()
        }.toMutableMap()

        val discloseObject = DiscloseObject(visitor, disclosuresPerDigest)
        val disclosedClaims = discloseObject(JsonPointer.Root, jwtClaims)

        // Make sure, all disclosures have been embedded
        require(disclosuresPerDigest.isEmpty()) {
            "Could not find digests for disclosures ${disclosuresPerDigest.values.map { it.value }}"
        }
        return disclosedClaims
    }
}

private class DiscloseObject(
    private val visitor: ClaimVisitor?,
    private val disclosuresPerDigest: DisclosurePerDigest,
) {

    /**
     * Embed disclosures into [jsonObject]
     * Replaces the direct (immediate) digests of the object and
     * then recursively do the same for all elements
     * that are either objects or arrays
     *
     * @param jsonObject the claims to use
     * @param currentPointer the [JsonPointer] of the current element
     *
     * @return the given [jsonObject] with the digests, if any, replaced by disclosures, including
     * all nested objects and/or array of objects
     */
    operator fun invoke(
        currentPointer: JsonPointer,
        jsonObject: JsonObject,
    ): JsonObject = discloseObject(currentPointer, jsonObject)

    //
    // Any JSON Element
    //

    /**
     * Embed disclosures into [element], if the element
     * is [JsonObject] or a [JsonArray] with [JSON objects][JsonObject],
     * including nested elements.
     *
     * @param element the element to use
     * @param currentPointer the [JsonPointer] of the current element
     *
     * @return a json element where all digests have been replaced by disclosed claims
     */
    private fun discloseElement(
        currentPointer: JsonPointer,
        element: JsonElement,
    ): JsonElement {
        visited(currentPointer, null)
        return when (element) {
            is JsonObject -> discloseObject(currentPointer, element)
            is JsonArray -> discloseArray(currentPointer, element)
            else -> element
        }
    }

    //
    // JSON Object
    //

    private fun discloseObject(
        currentPointer: JsonPointer,
        jsonObject: JsonObject,
    ): JsonObject =
        replaceObjectDigests(currentPointer, jsonObject)
            .mapValues { (name, element) ->
                val nestedPath = currentPointer.child(name)
                discloseElement(nestedPath, element)
            }
            .let { obj -> JsonObject(obj) }

    /**
     * Replaces the direct (immediate) digests found in the _sd claim
     * with the [Disclosure.ObjectProperty.claim] from [disclosuresPerDigest]
     *
     * @param jsonObject the claims to use
     * @param current the [JsonPointer] of the current element
     *
     * @return the given [jsonObject] with the digests, if any, replaced by disclosures.
     */
    private fun replaceObjectDigests(
        current: JsonPointer,
        jsonObject: JsonObject,
    ): JsonObject {
        val resultingClaims = jsonObject.toMutableMap()

        fun replace(digest: DisclosureDigest) {
            disclosuresPerDigest.remove(digest)?.let { disclosure ->
                check(disclosure is Disclosure.ObjectProperty) {
                    "Found array element disclosure ${disclosure.value} within _sd claim"
                }
                val (name, value) = disclosure.claim()
                require(!jsonObject.containsKey(name)) {
                    "Failed to embed disclosure with key $name. Already present"
                }
                visited(current.child(name), disclosure)
                resultingClaims[name] = value
            }
        }

        // Replace each digest with the claim from the disclosure, if found
        // otherwise digest is probably decoy
        jsonObject.directDigests().forEach { replace(it) }

        // Remove _sd claim, if present
        resultingClaims.remove("_sd")

        return JsonObject(resultingClaims)
    }

    //
    // JSON Array
    //

    private fun discloseArray(
        currentPointer: JsonPointer,
        jsonArray: JsonArray,
    ): JsonArray =
        jsonArray
            .zip(0..<jsonArray.size)
            .mapNotNull { (element, index) -> discloseArrayElement(currentPointer, element, index) }
            .let { elements -> JsonArray(elements) }

    private fun discloseArrayElement(
        currentPointer: JsonPointer,
        arrayElement: JsonElement,
        index: Int,
    ): JsonElement? {
        val elementPath = currentPointer.child(index)
        val disclosedElement =
            when (val disclosed = DisclosedArrayElement.of(arrayElement)) {
                is DisclosedArrayElement.Digest -> {
                    replaceArrayDigest(elementPath, disclosed.digest)
                }

                DisclosedArrayElement.Object -> {
                    visited(elementPath, null)
                    arrayElement
                }

                DisclosedArrayElement.NotAnObject -> arrayElement
            }
        return disclosedElement
            ?.let { discloseElement(elementPath, it) }
    }

    private fun replaceArrayDigest(
        current: JsonPointer,
        digest: DisclosureDigest,
    ): JsonElement? =
        disclosuresPerDigest.remove(digest)?.let { disclosure ->
            check(disclosure is Disclosure.ArrayElement) {
                "Found an $disclosure within an selectively disclosed array element"
            }
            visited(current, disclosure)
            disclosure.claim().value()
        }

    private fun visited(pointer: JsonPointer, disclosure: Disclosure?) {
        visitor?.invoke(pointer, disclosure)
    }
}

private sealed interface DisclosedArrayElement {
    @JvmInline
    value class Digest(val digest: DisclosureDigest) : DisclosedArrayElement
    data object Object : DisclosedArrayElement
    data object NotAnObject : DisclosedArrayElement

    companion object {
        fun of(jsonElement: JsonElement): DisclosedArrayElement =
            when (jsonElement) {
                is JsonObject ->
                    when (val digest = arrayElementDigest(jsonElement)) {
                        null -> Object
                        else -> Digest(digest)
                    }

                else -> NotAnObject
            }

        private fun arrayElementDigest(obj: JsonObject): DisclosureDigest? =
            if (obj.size == 1)
                obj["..."]
                    ?.takeIf { element -> element is JsonPrimitive }
                    ?.let { DisclosureDigest.wrap(it.jsonPrimitive.content).getOrNull() }
            else null
    }
}

/**
 * Cet the [digests][DisclosureDigest] by looking for digest claim.
 * This should be an array of digests, under "_sd" name.
 *
 * No recursive involved. Just the immediate digests.
 *
 *  @receiver the claims to check
 *  @return the digests found. Method may raise an exception in case the digests cannot be base64 decoded
 */
internal fun Claims.directDigests(): Set<DisclosureDigest> =
    this["_sd"]?.jsonArray
        ?.map { DisclosureDigest.wrap(it.jsonPrimitive.content).getOrThrow() }
        ?.toSet()
        ?: emptySet()

/**
 * Gets from the [Claims] the hash algorithm claim ("_sd_alg")
 * @receiver the claims to check
 * @return The [HashAlgorithm] if found
 */
internal fun Claims.hashAlgorithm(): HashAlgorithm? =
    this["_sd_alg"]?.let { HashAlgorithm.fromString(it.jsonPrimitive.content) }
