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

    fun recreateClaims(claims: Claims, disclosures: List<Disclosure>): Claims {
        val hashAlgorithm = claims.hashAlgorithm() ?: HashAlgorithm.SHA_256
        return replaceDigestsWithDisclosures(
            hashAlgorithm,
            disclosures,
            claims - "_sd_alg",
        )
    }

    /**
     * Replaces the digests found within [claims] and/or [disclosures] with
     * the [disclosures]
     *
     * @param hashAlgorithm the hash algorithm used to produce the [digests][DisclosureDigest]
     * @param disclosures the disclosures to use when replacing digests
     * @param claims the claims to use
     *
     * @return the initial [claims] having all digests replaced by [Disclosure]
     * @throws IllegalStateException when not all [disclosures] have been used, which means
     * that [claims] doesn't contain a [DisclosureDigest] for every [Disclosure]
     */
    private fun replaceDigestsWithDisclosures(
        hashAlgorithm: HashAlgorithm,
        disclosures: List<Disclosure>,
        claims: Claims,
    ): JsonObject {
        // Recalculate digests, using the hash algorithm
        val disclosuresPerDigest = disclosures.associateBy {
            DisclosureDigest.digest(hashAlgorithm, it.value).getOrThrow()
        }.toMutableMap()

        val recreatedClaims = embedDisclosuresIntoObject(disclosuresPerDigest, claims, JsonPointer.Root)

        // Make sure, all disclosures have been embedded
        require(disclosuresPerDigest.isEmpty()) {
            "Could not find digests for disclosures ${disclosuresPerDigest.values.map { it.value }}"
        }
        return recreatedClaims
    }

    /**
     * Embed disclosures into [jsonElement], if the element
     * is [JsonObject] or a [JsonArray] with [JSON objects][JsonObject],
     * including nested elements.
     *
     * @param disclosures the disclosures to use when replacing digests
     * @param jsonElement the element to use
     * @param current the [JsonPointer] of the current element
     *
     * @return a json element where all digests have been replaced by disclosed claims
     */
    private fun embedDisclosuresIntoElement(
        disclosures: DisclosurePerDigest,
        jsonElement: JsonElement,
        current: JsonPointer,
    ): JsonElement {
        fun embedDisclosuresIntoArrayElement(element: JsonElement, index: Int): JsonElement? {
            val elementPath = current.child(index)

            val sdArrayElement =
                when (val disclosed = DisclosedArrayElement.of(element)) {
                    is DisclosedArrayElement.Digest -> {
                        replaceArrayDigest(disclosures, disclosed.digest, elementPath)
                    }

                    DisclosedArrayElement.Object -> {
                        visited(elementPath, null)
                        element
                    }

                    DisclosedArrayElement.NotAnObject -> element
                }
            return sdArrayElement
                ?.let { embedDisclosuresIntoElement(disclosures, it, elementPath) }
        }

        visited(current, null)
        return when (jsonElement) {
            is JsonObject -> embedDisclosuresIntoObject(disclosures, jsonElement, current)
            is JsonArray ->
                jsonElement
                    .zip(0..<jsonElement.size)
                    .mapNotNull { (element, index) -> embedDisclosuresIntoArrayElement(element, index) }
                    .let { elements -> JsonArray(elements) }

            else -> jsonElement
        }
    }

    /**
     * Embed disclosures into [claims]
     * Replaces the direct (immediate) digests of the object and
     * then recursively do the same for all elements
     * that are either objects or arrays
     *
     * @param disclosures the disclosures to use when replacing digests
     * @param claims the claims to use
     * @param current the [JsonPointer] of the current element
     *
     * @return the given [claims] with the digests, if any, replaced by disclosures, including
     * all nested objects and/or array of objects
     */
    private fun embedDisclosuresIntoObject(
        disclosures: DisclosurePerDigest,
        claims: Claims,
        current: JsonPointer,
    ): JsonObject =
        replaceDirectDigests(disclosures, claims, current)
            .mapValues { (name, element) ->
                val nestedPath = current.child(name)
                embedDisclosuresIntoElement(disclosures, element, nestedPath)
            }
            .let { obj -> JsonObject(obj) }

    /**
     * Replaces the direct (immediate) digests found in the _sd claim
     * with the [Disclosure.ObjectProperty.claim] from [disclosures]
     *
     * @param disclosures the disclosures to use when replacing digests
     * @param claims the claims to use
     * @param current the [JsonPointer] of the current element
     *
     * @return the given [claims] with the digests, if any, replaced by disclosures.
     */
    private fun replaceDirectDigests(
        disclosures: DisclosurePerDigest,
        claims: Claims,
        current: JsonPointer,
    ): Claims {
        val resultingClaims = claims.toMutableMap()

        fun embed(digest: DisclosureDigest, disclosure: Disclosure.ObjectProperty) {
            val (name, value) = disclosure.claim()
            require(!claims.containsKey(name)) { "Failed to embed disclosure with key $name. Already present" }

            visited(current.child(name), disclosure)

            disclosures.remove(digest)
            resultingClaims[name] = value
        }

        // Replace each digest with the claim from the disclosure, if found
        // otherwise digest is probably decoy
        for (digest in claims.directDigests()) {
            disclosures[digest]?.let { disclosure ->
                check(disclosure is Disclosure.ObjectProperty) {
                    "Found array element disclosure ${disclosure.value} within _sd claim"
                }
                embed(digest, disclosure)
            }
        }
        // Remove _sd claim, if present
        resultingClaims.remove("_sd")

        return resultingClaims
    }

    private fun replaceArrayDigest(
        disclosures: DisclosurePerDigest,
        digest: DisclosureDigest,
        current: JsonPointer,
    ): JsonElement? =
        disclosures[digest]?.let { disclosure ->
            check(disclosure is Disclosure.ArrayElement) {
                "Found an $disclosure within an selectively disclosed array element"
            }
            visited(current, disclosure)
            disclosures.remove(digest)
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
