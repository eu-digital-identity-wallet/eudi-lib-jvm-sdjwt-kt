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
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.JsonPointer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.Throws
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * The path is a non-empty [list][value] of [elements][ClaimPathElement],
 * null values, or non-negative integers.
 * It is used to [select][SelectPath] a particular claim in the credential or a set of claims.
 *
 * It is [serialized][ClaimPathSerializer] as a [JsonArray] which may contain
 * string, `null`, or integer elements
 */
@Serializable(with = ClaimPathSerializer::class)
@JvmInline
value class ClaimPath(val value: List<ClaimPathElement>) {

    init {
        require(value.isNotEmpty())
    }

    override fun toString(): String = value.toString()

    operator fun plus(other: ClaimPathElement): ClaimPath =
        ClaimPath(this.value + other)

    operator fun plus(other: ClaimPath): ClaimPath =
        ClaimPath(this.value + other.value)

    operator fun contains(other: ClaimPath): Boolean =
        value.foldIndexed(true) { index, acc, thisElement ->
            fun comp() =
                other.value.getOrNull(index)?.let { otherElement -> otherElement in thisElement } == true
            acc and comp()
        }

    infix fun matches(other: ClaimPath): Boolean = (value.size == other.value.size) && (this in other)

    /**
     * Appends a wild-card indicator [ClaimPathElement.AllArrayElements]
     */
    fun allArrayElements(): ClaimPath = this + ClaimPathElement.AllArrayElements

    /**
     * Appends an indexed path [ClaimPathElement.ArrayElement]
     */
    fun arrayElement(i: Int): ClaimPath = this + ClaimPathElement.ArrayElement(i)

    /**
     * Appends a named path [ClaimPathElement.Claim]
     */
    fun claim(name: String): ClaimPath = this + ClaimPathElement.Claim(name)

    fun head(): ClaimPathElement = value.first()
    fun tail(): ClaimPath? {
        val tailElements = value.drop(1)
        return if (tailElements.isEmpty()) return null
        else ClaimPath(tailElements)
    }

    /**
     * Gets the [head]
     */
    operator fun component1(): ClaimPathElement = head()

    /**
     * Gets the [tail]
     */
    operator fun component2(): ClaimPath? = tail()

    companion object {
        fun claim(name: String): ClaimPath = ClaimPath(listOf(ClaimPathElement.Claim(name)))
    }
}

/**
 * Elements of a [ClaimPath]
 * - [Claim] indicates that the respective [key][Claim.name] is to be selected
 * - [AllArrayElements] indicates that all elements of the currently selected array(s) are to be selected, and
 * - [ArrayElement] indicates that the respective [index][ArrayElement.index] in an array is to be selected
 */
sealed interface ClaimPathElement {

    /**
     * Indicates that all elements of the currently selected array(s) are to be selected
     * It is serialized as a [JsonNull]
     */
    data object AllArrayElements : ClaimPathElement {
        override fun toString() = "null"
    }

    /**
     * Indicates that the respective [index][index] in an array is to be selected.
     * It is serialized as an [integer][JsonPrimitive]
     * @param index Non-negative index
     */
    @JvmInline
    value class ArrayElement(val index: Int) : ClaimPathElement {
        init {
            require(index >= 0) { "Index should be non-negative" }
        }

        override fun toString() = index.toString()
    }

    /**
     * Indicates that the respective [key][name] is to be selected.
     * It is serialized as a [string][JsonPrimitive]
     * @param name a non-blank attribute name
     */
    @JvmInline
    value class Claim(val name: String) : ClaimPathElement {
        init {
            require(name.isNotBlank()) { "Attribute must not be blank" }
        }

        override fun toString() = name
    }
}

operator fun ClaimPathElement.contains(thatElement: ClaimPathElement): Boolean =
    when (this) {
        ClaimPathElement.AllArrayElements -> when (thatElement) {
            ClaimPathElement.AllArrayElements -> true
            is ClaimPathElement.ArrayElement -> true
            is ClaimPathElement.Claim -> false
        }

        is ClaimPathElement.ArrayElement -> thatElement == this
        is ClaimPathElement.Claim -> thatElement == this
    }

inline fun <T> ClaimPathElement.fold(
    ifAllArrayElements: () -> T,
    ifArrayElement: (Int) -> T,
    ifClaim: (String) -> T,
): T {
    contract {
        callsInPlace(ifAllArrayElements, InvocationKind.AT_MOST_ONCE)
        callsInPlace(ifArrayElement, InvocationKind.AT_MOST_ONCE)
        callsInPlace(ifClaim, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        ClaimPathElement.AllArrayElements -> ifAllArrayElements()
        is ClaimPathElement.ArrayElement -> ifArrayElement(index)
        is ClaimPathElement.Claim -> ifClaim(name)
    }
}

/**
 * Maps a [JsonPointer] to a [ClaimPath], that either contains exactly the same information, or
 * index tokens are replaced by [`null`][ClaimPathElement.AllArrayElements].
 *
 * @receiver The JSON pointer to transform. It should be other than [""]
 * @param replaceIndexesWithWildcard if `true` integer indexes found in the [JsonPointer] tokens
 * will be replaced by [`null`][ClaimPathElement.AllArrayElements]. Otherwise, indexes will be preserved.
 * If omitted default value is `false`
 * @return the [ClaimPath] as described above.
 * @throws IllegalArgumentException in case the [JsonPointer] is [""][JsonPointer.Root]
 */
@Throws(IllegalArgumentException::class)
fun JsonPointer.toClaimPath(replaceIndexesWithWildcard: Boolean = false): ClaimPath {
    fun asElement(token: String): ClaimPathElement =
        token.toIntOrNull()
            ?.let { index ->
                if (replaceIndexesWithWildcard) ClaimPathElement.AllArrayElements
                else ClaimPathElement.ArrayElement(index)
            }
            ?: ClaimPathElement.Claim(token)

    require(JsonPointer.Root != this) { "Cannot map JsonPointer `\"\"` to a path" }
    return tokens.map(::asElement).let(::ClaimPath)
}

// TODO make stack safe
//  Make it to return JsonPoint (single)
internal fun ClaimPath.toJsonPointers(maxWildcardExpansions: Int = 10): List<JsonPointer> {
    fun buildPointers(
        path: ClaimPath?,
        currentPointer: JsonPointer,
    ): List<JsonPointer> {
        return if (path == null) listOf(currentPointer)
        else {
            val (head, tail) = path
            head.fold(
                ifClaim = { name -> buildPointers(tail, currentPointer.child(name)) },
                ifArrayElement = { index -> buildPointers(tail, currentPointer.child(index)) },
                ifAllArrayElements = {
                    // Handle wildcard: generate multiple pointers with index expansions
                    (0..maxWildcardExpansions).flatMap { index ->
                        buildPointers(tail, currentPointer.child(index))
                    }
                },
            )
        }
    }
    return buildPointers(this, JsonPointer.Root)
}

/**
 * Serializer for [ClaimPath]
 */
object ClaimPathSerializer : KSerializer<ClaimPath> {

    private fun claimPathElement(it: JsonPrimitive): ClaimPathElement =
        when {
            it is JsonNull -> ClaimPathElement.AllArrayElements
            it.isString -> ClaimPathElement.Claim(it.content)
            it.intOrNull != null -> ClaimPathElement.ArrayElement(it.int)
            else -> throw IllegalArgumentException("Only string, null, int can be used")
        }

    private fun claimPath(array: JsonArray): ClaimPath {
        val elements = array.map {
            require(it is JsonPrimitive)
            claimPathElement(it)
        }
        return ClaimPath(elements)
    }

    private fun ClaimPath.toJson(): JsonArray = JsonArray(value.map { it.toJson() })

    private fun ClaimPathElement.toJson(): JsonPrimitive = when (this) {
        is ClaimPathElement.Claim -> JsonPrimitive(name)
        is ClaimPathElement.ArrayElement -> JsonPrimitive(index)
        ClaimPathElement.AllArrayElements -> JsonNull
    }

    val arraySerializer = serializer<JsonArray>()

    override val descriptor: SerialDescriptor = arraySerializer.descriptor

    override fun serialize(encoder: Encoder, value: ClaimPath) {
        val array = value.toJson()
        arraySerializer.serialize(encoder, array)
    }

    override fun deserialize(decoder: Decoder): ClaimPath {
        val array = arraySerializer.deserialize(decoder)
        return claimPath(array)
    }
}
