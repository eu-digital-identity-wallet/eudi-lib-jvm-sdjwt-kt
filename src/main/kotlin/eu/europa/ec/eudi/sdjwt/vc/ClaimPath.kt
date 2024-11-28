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

    operator fun plus(other: String): ClaimPath = plus(ClaimPathElement.Named(other))
    operator fun plus(other: Int): ClaimPath = plus(ClaimPathElement.Indexed(other))
    operator fun contains(other: ClaimPath): Boolean =
        value.foldIndexed(true) { index, acc, thisElement ->
            fun comp() = other.value.getOrNull(index)
                ?.let { it in thisElement }
                ?: false

            acc and comp()
        }

    /**
     * Appends a wild-card indicator [ClaimPathElement.All]
     */
    fun all(): ClaimPath = this + ClaimPathElement.All

    /**
     * Appends an indexed path [ClaimPathElement.Indexed]
     */
    fun at(i: Int): ClaimPath = this + ClaimPathElement.Indexed(i)

    /**
     * Appends a named path [ClaimPathElement.Named]
     */
    fun attribute(name: String): ClaimPath = this + ClaimPathElement.Named(name)

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
        fun attribute(name: String): ClaimPath = ClaimPath(listOf(ClaimPathElement.Named(name)))
    }
}

/**
 * Elements of a [ClaimPath]
 * - [Named] indicates that the respective [key][Named.value] is to be selected
 * - [All] indicates that all elements of the currently selected array(s) are to be selected, and
 * - [Indexed] indicates that the respective [index][Indexed.value] in an array is to be selected
 */
sealed interface ClaimPathElement {

    /**
     * Indicates that all elements of the currently selected array(s) are to be selected
     * It is serialized as a [JsonNull]
     */
    data object All : ClaimPathElement {
        override fun toString() = "null"
    }

    /**
     * Indicates that the respective [index][value] in an array is to be selected.
     * It is serialized as an [integer][JsonPrimitive]
     * @param value Non-negative index
     */
    @JvmInline
    value class Indexed(val value: Int) : ClaimPathElement {
        init {
            require(value >= 0) { "Index should be non-negative" }
        }

        override fun toString() = value.toString()
    }

    /**
     * Indicates that the respective [key][value] is to be selected.
     * It is serialized as a [string][JsonPrimitive]
     * @param value a non-blank attribute name
     */
    @JvmInline
    value class Named(val value: String) : ClaimPathElement {
        init {
            require(value.isNotBlank()) { "Attribute must not be blank" }
        }

        override fun toString() = value
    }
}

operator fun ClaimPathElement.contains(thatElement: ClaimPathElement): Boolean =
    when (this) {
        ClaimPathElement.All -> when (thatElement) {
            ClaimPathElement.All -> true
            is ClaimPathElement.Indexed -> true
            is ClaimPathElement.Named -> false
        }

        is ClaimPathElement.Indexed -> thatElement == this
        is ClaimPathElement.Named -> thatElement == this
    }

inline fun <T> ClaimPathElement.fold(
    ifAll: () -> T,
    ifIndexed: (Int) -> T,
    ifNamed: (String) -> T,
): T {
    contract {
        callsInPlace(ifAll, InvocationKind.AT_MOST_ONCE)
        callsInPlace(ifIndexed, InvocationKind.AT_MOST_ONCE)
        callsInPlace(ifNamed, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        ClaimPathElement.All -> ifAll()
        is ClaimPathElement.Indexed -> ifIndexed(value)
        is ClaimPathElement.Named -> ifNamed(value)
    }
}

/**
 * Maps a [JsonPointer] to a [ClaimPath], that either contains exactly the same information, or
 * index tokens are replaced by [`null`][ClaimPathElement.All].
 *
 * @receiver The JSON pointer to transform. It should be other than [""]
 * @param replaceIndexesWithWildcard if `true` integer indexes found in the [JsonPointer] tokens
 * will be replaced by [`null`][ClaimPathElement.All]. Otherwise, indexes will be preserved.
 * If omitted default value is `false`
 * @return the [ClaimPath] as described above.
 * @throws IllegalArgumentException in case the [JsonPointer] is [""][JsonPointer.Root]
 */
@Throws(IllegalArgumentException::class)
fun JsonPointer.toClaimPath(replaceIndexesWithWildcard: Boolean = false): ClaimPath {
    fun asElement(token: String): ClaimPathElement =
        token.toIntOrNull()
            ?.let { index ->
                if (replaceIndexesWithWildcard) ClaimPathElement.All
                else ClaimPathElement.Indexed(index)
            }
            ?: ClaimPathElement.Named(token)

    require(JsonPointer.Root != this) { "Cannot map JsonPointer `\"\"` to a path" }
    return tokens.map(::asElement).let(::ClaimPath)
}

// TODO make stack safe
fun ClaimPath.toJsonPointers(maxWildcardExpansions: Int = 10): List<JsonPointer> {
    fun buildPointers(
        path: ClaimPath?,
        currentPointer: JsonPointer,
    ): List<JsonPointer> {
        return if (path == null) listOf(currentPointer)
        else {
            val (head, tail) = path
            head.fold(
                ifNamed = { name -> buildPointers(tail, currentPointer.child(name)) },
                ifIndexed = { index -> buildPointers(tail, currentPointer.child(index)) },
                ifAll = {
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
            it is JsonNull -> ClaimPathElement.All
            it.isString -> ClaimPathElement.Named(it.content)
            it.intOrNull != null -> ClaimPathElement.Indexed(it.int)
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
        is ClaimPathElement.Named -> JsonPrimitive(value)
        is ClaimPathElement.Indexed -> JsonPrimitive(value)
        ClaimPathElement.All -> JsonNull
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
