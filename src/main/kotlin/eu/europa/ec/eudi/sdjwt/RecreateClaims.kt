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
    val disclosedClaims = JsonObject(claimsOf(jwt))
    return RecreateClaims.recreateClaims(disclosedClaims, disclosures)
}

fun UnsignedSdJwt.recreateClaims() = recreateClaims { it }

private typealias DisclosurePerDigest = MutableMap<DisclosureDigest, Disclosure>

private object RecreateClaims {

    fun recreateClaims(claims: Claims, disclosures: List<Disclosure>): Claims {
        val hashAlgorithm = claims.hashAlgorithm()
        return if (hashAlgorithm != null) replaceDigestsWithDisclosures(hashAlgorithm, disclosures, claims - "_sd_alg")
        else {
            val containsDigests = collectDigests(claims).isNotEmpty()
            if (disclosures.isEmpty() && !containsDigests) claims
            else error("Missing hash algorithm")
        }
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

        val recreatedClaims = embedDisclosuresIntoObject(disclosuresPerDigest, claims)

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
     * @return a json element where all digests have been replaced by disclosed claims
     */
    private fun embedDisclosuresIntoElement(
        disclosures: DisclosurePerDigest,
        jsonElement: JsonElement,
    ): JsonElement {
        fun embedDisclosuresIntoArrayElement(e: JsonElement): JsonElement {
            val sdArrayElement =
                if (e is JsonObject) replaceArrayDigest(disclosures, e) ?: e
                else e

            return embedDisclosuresIntoElement(disclosures, sdArrayElement)
        }
        return when (jsonElement) {
            is JsonObject -> embedDisclosuresIntoObject(disclosures, jsonElement)
            is JsonArray ->
                jsonElement
                    .map { element -> embedDisclosuresIntoArrayElement(element) }
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
     *
     * @return the given [claims] with the digests, if any, replaced by disclosures, including
     * all nested objects and/or array of objects
     */
    private fun embedDisclosuresIntoObject(
        disclosures: DisclosurePerDigest,
        claims: Claims,
    ): JsonObject =
        replaceDirectDigests(disclosures, claims)
            .mapValues { (_, element) -> embedDisclosuresIntoElement(disclosures, element) }
            .let { obj -> JsonObject(obj) }

    /**
     * Replaces the direct (immediate) digests found in the _sd claim
     * with the [Disclosure.ObjectProperty.claim] from [disclosures]
     *
     * @param disclosures the disclosures to use when replacing digests
     * @param claims the claims to use
     * @return the given [claims] with the digests, if any, replaced by disclosures.
     */
    private fun replaceDirectDigests(
        disclosures: DisclosurePerDigest,
        claims: Claims,
    ): Claims {
        val resultingClaims = claims.toMutableMap()

        fun embed(digest: DisclosureDigest, disclosure: Disclosure.ObjectProperty) {
            val (name, value) = disclosure.claim()
            require(!claims.containsKey(name)) { "Failed to embed disclosure with key $name. Already present" }
            disclosures.remove(digest)
            resultingClaims[name] = value
        }

        // Replace each digest with the claim from the disclosure, if found
        // otherwise digest is probably decoy
        for (digest in claims.directDigests()) {
            disclosures[digest]?.let { disclosure ->
                if (disclosure is Disclosure.ObjectProperty) embed(digest, disclosure)
                else error("Found array element disclosure ${disclosure.value} within _sd claim")
            }
        }
        // Remove _sd claim, if present
        resultingClaims.remove("_sd")

        return resultingClaims
    }
}

private fun replaceArrayDigest(disclosures: DisclosurePerDigest, claims: JsonObject): JsonElement? =
    arrayElementDigest(claims)?.let { digest ->

        disclosures[digest]?.let { disclosure ->
            when (disclosure) {
                is Disclosure.ArrayElement -> {
                    disclosures.remove(digest)
                    disclosure.claim().value()
                }

                else -> error("Found an $disclosure within an selectively disclosed array element")
            }
        }
    }

internal fun arrayElementDigest(claims: Claims): DisclosureDigest? =
    if (claims.size == 1)
        claims["..."]?.takeIf { element -> element is JsonPrimitive }?.let {
            DisclosureDigest.wrap(it.jsonPrimitive.content).getOrNull()
        }
    else null

/**
 * Cet the [digests][DisclosureDigest] by looking for digest claim.
 * This should be an array of digests, under "_sd" name.
 *
 * No recursive involved. Just the immediate digests.
 *
 *  @receiver the claims to check
 *  @return the digests found. Method may raise an exception in case the digests cannot be base64 decoded
 */
private fun Claims.directDigests(): Set<DisclosureDigest> =
    this["_sd"]?.jsonArray
        ?.map { DisclosureDigest.wrap(it.jsonPrimitive.content).getOrThrow() }
        ?.toSet()
        ?: emptySet()

/**
 * Gets from the [Claims] the hash algorithm claim ("_sd_alg")
 * @receiver the claims to check
 * @return The [HashAlgorithm] if found
 */
private fun Claims.hashAlgorithm(): HashAlgorithm? =
    this["_sd_alg"]?.let { HashAlgorithm.fromString(it.jsonPrimitive.content) }
