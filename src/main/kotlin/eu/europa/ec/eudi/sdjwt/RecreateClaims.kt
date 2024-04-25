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
    return recreateClaims(visitor = SdClaimVisitor.NoOp, claimsOf = claimsOf)
}

/**
 * Recreates the claims, used to produce the SD-JWT
 * That are:
 * - The plain claims found into the [SdJwt.jwt]
 * - Digests found in [SdJwt.jwt] and/or [Disclosure] (in case of recursive disclosure) will
 *   be replaced by [Disclosure.claim]
 *
 * @param visitor [SdClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
 * @param claimsOf a function to obtain the [Claims] of the [SdJwt.jwt]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to use
 * @return the claims that were used to produce the SD-JWT
 */
fun <JWT> SdJwt<JWT>.recreateClaims(visitor: SdClaimVisitor = SdClaimVisitor.NoOp, claimsOf: (JWT) -> Claims): Claims {
    val disclosedClaims = JsonObject(claimsOf(jwt))
    return RecreateClaims(visitor).recreateClaims(disclosedClaims, disclosures)
}

fun UnsignedSdJwt.recreateClaims(visitor: SdClaimVisitor = SdClaimVisitor.NoOp) = recreateClaims(visitor) { it }

/**
 * Subset of json path, used to point to a single claim.
 *
 * Wildcards, deep-scan, bracket notation, array range accessors, comma notation, or negative indexes are not supported.
 */
@JvmInline
value class SingleClaimJsonPath private constructor(private val claimPath: List<String>) {

    /**
     * Gets the path of the containing element, if any.
     */
    fun partOf(): SingleClaimJsonPath? = when (this) {
        Root -> null
        else -> SingleClaimJsonPath(claimPath.dropLast(1))
    }

    /**
     * Converts this to a [JsonPath].
     */
    fun asJsonPath(): JsonPath {
        return "$" + claimPath.fold("") { acc, s ->
            if (s.startsWith("[")) acc + s
            else "$acc.$s"
        }
    }

    /**
     * Gets a new [SingleClaimJsonPath] for the claim with the provided [name], nested in the current [SingleClaimJsonPath].
     *
     * @throws IllegalArgumentException in case [name] contains invalid characters
     */
    fun nestClaim(name: String): SingleClaimJsonPath {
        require(name.isNotBlank() && ClaimNameDisallowedCharacters.none { it in name }) { "Invalid name" }
        return SingleClaimJsonPath(claimPath + name)
    }

    /**
     * Gets a new [SingleClaimJsonPath] for an array item at the provided [index].
     *
     * @throws IllegalArgumentException in case [index] is negative.
     */
    fun arrayItem(index: Int): SingleClaimJsonPath {
        require(index >= 0) { "Invalid index" }
        return SingleClaimJsonPath(claimPath + "[$index]")
    }

    companion object {

        /**
         * The root element. i.e. '$'.
         */
        val Root: SingleClaimJsonPath = SingleClaimJsonPath(emptyList())

        /**
         * Characters not allowed in names of claims.
         */
        val ClaimNameDisallowedCharacters = setOf("$", "[", "]", ",", ":", "'", " ", "*")

        /**
         * Converts a [JsonPath] to a [SingleClaimJsonPath].
         * In case [jsonPath] is not a valid [SingleClaimJsonPath], null is returned.
         */
        fun fromJsonPath(jsonPath: JsonPath): SingleClaimJsonPath? =
            if (jsonPath.isBlank() || jsonPath[0] != '$') null
            else runCatching {
                jsonPath.split(".")
                    .drop(1)
                    .fold(Root) { accumulator, part ->
                        if (part.contains("[") && part.contains("]")) {
                            val nameAndIndex = part.split("[", "]").dropLast(1)
                            require(nameAndIndex.size == 2)

                            val name = nameAndIndex[0]
                            val index = nameAndIndex[1].toInt()

                            accumulator.nestClaim(name).arrayItem(index)
                        } else accumulator.nestClaim(part)
                    }
            }.getOrNull()
    }
}

/**
 * Visitor for selectively disclosed claims.
 */
fun interface SdClaimVisitor {

    /**
     * Invoked whenever a selectively disclosed claim is encountered while recreating the claims of an [SdJwt].
     * @param path the full JsonPath of the current selectively disclosed element - uses dot notation
     * @param disclosure the disclosure of the current selectively disclosed element
     */
    operator fun invoke(path: SingleClaimJsonPath, disclosure: Disclosure?)

    companion object {

        /**
         * An [SdClaimVisitor] that performs no operation.
         */
        val NoOp = SdClaimVisitor { _, _ -> }
    }
}

private typealias DisclosurePerDigest = MutableMap<DisclosureDigest, Disclosure>

/**
 * @param visitor [SdClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
 */
private class RecreateClaims(private val visitor: SdClaimVisitor) {

    fun recreateClaims(claims: Claims, disclosures: List<Disclosure>): Claims {
        val hashAlgorithm = claims.hashAlgorithm()
        return if (hashAlgorithm != null) replaceDigestsWithDisclosures(
            hashAlgorithm,
            disclosures,
            claims - "_sd_alg",
        )
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

        for ((name, _) in claims) {
            if (name != "_sd" && name != "_sd_alg") {
                visitor(SingleClaimJsonPath.Root.nestClaim(name), null)
            }
        }

        val recreatedClaims =
            embedDisclosuresIntoObject(disclosuresPerDigest, claims, SingleClaimJsonPath.Root)

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
     * @param current the [JsonPath] of the current element - uses dot notation
     *
     * @return a json element where all digests have been replaced by disclosed claims
     */
    private fun embedDisclosuresIntoElement(
        disclosures: DisclosurePerDigest,
        jsonElement: JsonElement,
        current: SingleClaimJsonPath,
    ): JsonElement {
        fun embedDisclosuresIntoArrayElement(element: JsonElement, index: Int): JsonElement {
            val sdArrayElementPath = current.arrayItem(index)
            val sdArrayElement =
                if (element is JsonObject) replaceArrayDigest(disclosures, element, sdArrayElementPath, visitor)
                    ?: element
                else element

            return embedDisclosuresIntoElement(disclosures, sdArrayElement, sdArrayElementPath)
        }

        return when (jsonElement) {
            is JsonObject -> embedDisclosuresIntoObject(disclosures, jsonElement, current)
            is JsonArray ->
                jsonElement
                    .zip(0 until jsonElement.size)
                    .map { (element, index) -> embedDisclosuresIntoArrayElement(element, index) }
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
     * @param current the [JsonPath] of the current element - uses dot notation
     *
     * @return the given [claims] with the digests, if any, replaced by disclosures, including
     * all nested objects and/or array of objects
     */
    private fun embedDisclosuresIntoObject(
        disclosures: DisclosurePerDigest,
        claims: Claims,
        current: SingleClaimJsonPath,
    ): JsonObject =
        replaceDirectDigests(disclosures, claims, current)
            .mapValues { (name, element) ->
                val nestedPath = current.nestClaim(name)
                embedDisclosuresIntoElement(disclosures, element, nestedPath)
            }
            .let { obj -> JsonObject(obj) }

    /**
     * Replaces the direct (immediate) digests found in the _sd claim
     * with the [Disclosure.ObjectProperty.claim] from [disclosures]
     *
     * @param disclosures the disclosures to use when replacing digests
     * @param claims the claims to use
     * @param current the [JsonPath] of the current element - uses dot notation
     * @param visitor [SdClaimVisitor] to invoke whenever a selectively disclosed claim is encountered
     *
     * @return the given [claims] with the digests, if any, replaced by disclosures.
     */
    private fun replaceDirectDigests(
        disclosures: DisclosurePerDigest,
        claims: Claims,
        current: SingleClaimJsonPath,
    ): Claims {
        val resultingClaims = claims.toMutableMap()

        fun embed(digest: DisclosureDigest, disclosure: Disclosure.ObjectProperty) {
            val (name, value) = disclosure.claim()
            require(!claims.containsKey(name)) { "Failed to embed disclosure with key $name. Already present" }

            visitor(current.nestClaim(name), disclosure)

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

private fun replaceArrayDigest(
    disclosures: DisclosurePerDigest,
    claims: JsonObject,
    current: SingleClaimJsonPath,
    visitor: SdClaimVisitor,
): JsonElement? =
    arrayElementDigest(claims)?.let { digest ->
        disclosures[digest]?.let { disclosure ->
            when (disclosure) {
                is Disclosure.ArrayElement -> {
                    visitor(current, disclosure)
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
