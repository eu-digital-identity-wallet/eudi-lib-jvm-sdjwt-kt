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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import com.nfeld.jsonpathkt.JsonPath as ExternalJsonPath

typealias JsonPath = String

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
        val protected: Claims by lazy { claimsOf(sdJwt.jwt) }
        val protectedJson by lazy { Json.encodeToString(protected) }
        val unprotected: Claims by lazy { sdJwt.recreateClaims(claimsOf) }
        val unprotectedJson by lazy { Json.encodeToString(unprotected) }

        fun getUnprotectedClaimAt(path: JsonPath): JsonElement? =
            JsonPathOps.getJsonAtPath(path, unprotectedJson)?.let { Json.encodeToJsonElement(it) }

        fun getProtectedClaimAt(path: JsonPath): JsonElement? =
            JsonPathOps.getJsonAtPath(path, protectedJson)?.let { Json.encodeToJsonElement(it) }

        val protectedPaths by lazy { findPaths(JsonObject(protected)) }
        val unprotectedPaths by lazy { findPaths(JsonObject(unprotected)) }

        val intersection = (unprotectedPaths.intersect(protectedPaths)).also {
            println("Intersection")
            println(it)
        }
        val diff = (unprotectedPaths - protectedPaths).also {
            println("Diff")
            println(it)
        }

        fun doMatch(q: Query): Match? = when (q) {
            Query.OnlyNonSelectivelyDisclosableClaims -> Match.ByPlainClaims
            Query.AllClaims -> Match.BySdClaims(sdJwt.disclosures)
            is Query.ClaimInPath -> {
                val matchingClaim = getUnprotectedClaimAt(q.path)
                when (matchingClaim) {
                    JsonNull -> {
                        // This assumes that JSON Null cannot be Selectively disclosed
                        // TODO Check if is true
                        Match.ByPlainClaims
                    }

                    null -> null
                    else -> {
                        val isPlain = getProtectedClaimAt(q.path) != null
                        if (isPlain) Match.ByPlainClaims
                        else {
                            TODO()
                        }
                    }
                }
            }

            is Query.Many -> TODO()
        }

        return doMatch(query)
    }
}

internal fun findPaths(jsonObject: JsonObject, currentPath: String = "\$"): List<String> =
    jsonObject.flatMap { (key, value) ->
        val keyPath = "$currentPath.$key"
        when (value) {
            is JsonArray -> listOf(keyPath)
            is JsonObject -> {
                listOf(keyPath) + findPaths(value, keyPath)
            }

            is JsonPrimitive -> listOf(keyPath)
            JsonNull -> emptyList()
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

internal sealed interface AnnotatedClaim {
    val path: JsonPath
    data class Plain(override val path: JsonPath) : AnnotatedClaim
    data class Sd(override val path: JsonPath) : AnnotatedClaim
}

internal typealias Analyzed = BinaryTree<AnnotatedClaim>

internal fun Analyzed.leafAt(ps: List<String>): Leaf<AnnotatedClaim>? {
    TODO()
}

//
// Tree
//

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
