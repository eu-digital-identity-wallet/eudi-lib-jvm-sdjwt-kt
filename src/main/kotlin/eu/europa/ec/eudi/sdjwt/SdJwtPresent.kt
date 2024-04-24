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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.serialization.json.*
import com.nfeld.jsonpathkt.JsonPath as ExternalJsonPath

typealias JsonPath = String
typealias DisclosuresPerClaim = Map<JsonPath, List<Disclosure>>

/**
 * Gets the full [JsonPath] of each selectively disclosed claim alongside the [Disclosures][Disclosure] that are required
 * to disclose it.
 */
fun <JWT> SdJwt.Issuance<JWT>.disclosuresPerClaim(claimsOf: (JWT) -> Claims): DisclosuresPerClaim {
    val disclosures = mutableMapOf<JsonPath, List<Disclosure>>()
    val visitor = SdClaimVisitor { parent, current, disclosure ->
        require(current !in disclosures.keys) { "Disclosures for claim $current have already been calculated." }
        disclosures[current] = (disclosures[parent] ?: emptyList()) + disclosure
    }
    recreateClaims(visitor, claimsOf)
    return disclosures
}

fun UnsignedSdJwt.disclosuresPerClaim(): DisclosuresPerClaim = disclosuresPerClaim { it }

sealed interface Query {
    data object OnlyNonSelectivelyDisclosableClaims : Query
    data class ClaimInPath(val path: JsonPath, val filter: (Claim) -> Boolean = { true }) : Query {
        init {
            require(JsonPathOps.isValid(path)) { "Not a JSON path: $path" }
        }
    }

    data class Many(val claimsInPath: List<ClaimInPath>) : Query
    data object AllClaims : Query
}

sealed interface Match {
    data object ByPlainClaims : Match // No disclosures needed. Claim is non-selectively disclosable
    data class BySdClaims(val disclosures: List<Disclosure>) : Match {
        init {
            require(disclosures.isNotEmpty())
        }
    }

    fun disclosures(): List<Disclosure> = when (this) {
        ByPlainClaims -> emptyList()
        is BySdClaims -> disclosures
    }
}

interface Presenter<JWT> {
    fun present(sdJwt: SdJwt.Issuance<JWT>, query: Query): SdJwt.Presentation<JWT>? =
        when (val match = match(sdJwt, query)) {
            null -> null
            Match.ByPlainClaims -> SdJwt.Presentation(sdJwt.jwt, emptyList())
            is Match.BySdClaims -> {
                match.disclosures.forEach { disclosure ->
                    require(disclosure in sdJwt.disclosures) { "Unknown disclosure: $disclosure" }
                }
                SdJwt.Presentation(sdJwt.jwt, match.disclosures)
            }
        }

    fun match(sdJwt: SdJwt.Issuance<JWT>, query: Query): Match?
}

class DefaultPresenter<JWT>(
    private val claimsOf: (JWT) -> Claims,
    private val continueIfSomeNotMatch: Boolean = false,
) : Presenter<JWT> {

    override fun match(sdJwt: SdJwt.Issuance<JWT>, query: Query): Match? {
        return when (query) {
            Query.AllClaims -> Match.BySdClaims(sdJwt.disclosures)
            is Query.ClaimInPath -> {
                val ps: List<String> = query.path.run {
                    this.split(".")
                }.drop(1)
                sdJwt.disclosuresOf(claimsOf, ps)
            }
            is Query.Many -> TODO()
            Query.OnlyNonSelectivelyDisclosableClaims -> Match.ByPlainClaims
        }
    }
}

/**
 * JSON Path related operations
 */
internal object JsonPathOps {

    /**
     * Checks that the provided [path][String] is JSON Path
     */
    internal fun isValid(path: String): Boolean = path.toJsonPath().isSuccess

    /**
     * Extracts from given [JSON][jsonString] the content
     * at [path][jsonPath]. Returns the value found at the path, if found
     */
    internal fun getJsonAtPath(jsonPath: JsonPath, jsonString: String): String? =
        ExternalJsonPath(jsonPath)
            .readFromJson<JsonNode>(jsonString)
            ?.toJsonString()

    private fun String.toJsonPath(): Result<ExternalJsonPath> = runCatching {
        ExternalJsonPath(this)
    }

    private fun JsonNode.toJsonString(): String = objectMapper.writeValueAsString(this)

    /**
     * Jackson JSON support
     */
    private val objectMapper: ObjectMapper by lazy { ObjectMapper() }
}

private data class AnnotatedClaim(
    val path: JsonPath,
    val disclosure: Disclosure? = null,
) {
    override fun toString(): String {
        return "AnnotatedClaim[ path:$path" + (
            disclosure?.let {
                val (n, v) = it.claim()
                ", disclosure:$n = $v"
            } ?: ""
            ) + "]"
    }
}

private typealias Analyzed = TreeNode<AnnotatedClaim>

private fun <JWT>SdJwt.Issuance<JWT>.disclosuresOf(
    claimsOf: (JWT) -> Claims,
    pathAsList: List<String>,
): Match? {
    val analysis = analyze(claimsOf)
    var disclosuresToPresent: MutableList<Disclosure>? = null

    fun d(nodes: List<TreeNode<AnnotatedClaim>>, p: String): TreeNode<AnnotatedClaim>? =
        nodes.firstOrNull { it.value.path == p }

    var current = analysis.children
    for (p in pathAsList) {
        val r = d(current, p)
        when (r) {
            null -> {}
            else -> {
                if (disclosuresToPresent == null) {
                    disclosuresToPresent = mutableListOf()
                }
                r.value.disclosure?.let { disclosuresToPresent.add(it) }
                current = r.children
            }
        }
    }
    return disclosuresToPresent?.let {
        if (it.isEmpty()) Match.ByPlainClaims
        else Match.BySdClaims(it)
    }
}
private fun <JWT> SdJwt.Issuance<JWT>.analyze(claimsOf: (JWT) -> Claims): Analyzed {
    val jwtClaims = JsonObject(claimsOf(jwt))
    val disclosures = disclosures
    val hashAlgorithm = jwtClaims.hashAlgorithm() ?: HashAlgorithm.SHA_256
    // Recalculate digests, using the hash algorithm
    val disclosuresPerDigest: MutableMap<DisclosureDigest, Disclosure> = disclosures.associateBy {
        DisclosureDigest.digest(hashAlgorithm, it.value).getOrThrow()
    }.toMutableMap()

    val analysis = TreeNode(AnnotatedClaim("", null))

    doAnalyze(disclosuresPerDigest, jwtClaims, analysis)

    return analysis
}

private fun doAnalyze(
    disclosuresPerDigest: MutableMap<DisclosureDigest, Disclosure>,
    c: Claim,
    parent: TreeNode<AnnotatedClaim>,
    disclosure: Disclosure?,
) {
    val (name, json) = c
    when (json) {
        is JsonArray -> {
            parent.add(AnnotatedClaim(name, disclosure))
        }
        is JsonObject -> {
            val node = parent.add(AnnotatedClaim(name, disclosure))
            doAnalyze(disclosuresPerDigest, json, node)
        }
        is JsonPrimitive -> {
            parent.add(AnnotatedClaim(name, disclosure))
        }
        JsonNull -> {
            parent.add(AnnotatedClaim(name, disclosure))
        }
    }
}
private fun doAnalyze(disclosuresPerDigest: MutableMap<DisclosureDigest, Disclosure>, cs: Claims, parent: TreeNode<AnnotatedClaim>) {
    for (digest in cs.directDigests()) {
        disclosuresPerDigest[digest]?.let { disclosure ->
            if (disclosure is Disclosure.ObjectProperty) {
                doAnalyze(disclosuresPerDigest, disclosure.claim(), parent, disclosure)
            } else error("Found array element disclosure ${disclosure.value} within _sd claim")
        }
    }

    for (c in ((cs - "_sd") - "_sd_alg")) {
        doAnalyze(disclosuresPerDigest, c.key to c.value, parent, null)
    }
}
//
// Tree
//

private class TreeNode<T>(val value: T) {
    val children: MutableList<TreeNode<T>> = mutableListOf()
    fun add(v: T): TreeNode<T> {
        val node = TreeNode(v)
        children.add(node)
        return node
    }

    override fun toString(): String {
        return "value = $value" + if (children.isNotEmpty())", children=$children" else ""
    }
}

internal sealed interface BinaryTree<A>
internal data class Leaf<A>(val value: A) : BinaryTree<A>
internal data class Branch<A>(val left: BinaryTree<A>, val right: BinaryTree<A>) : BinaryTree<A>

internal fun <A, B> BinaryTree<A>.map(transform: (A) -> B): BinaryTree<B> =
    mapD(transform)(this)

private fun <A, B> mapD(transform: (A) -> B): DeepRecursiveFunction<BinaryTree<A>, BinaryTree<B>> =
    DeepRecursiveFunction {
        when (it) {
            is Leaf -> Leaf(transform(it.value))
            is Branch -> Branch(
                callRecursive(it.left),
                callRecursive(it.right),
            )
        }
    }
