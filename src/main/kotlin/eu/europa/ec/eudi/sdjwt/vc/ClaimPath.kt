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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
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
value class ClaimPath internal constructor(val value: List<ClaimPathElement>) {

    init {
        require(value.isNotEmpty())
    }

    override fun toString(): String = value.toString()

    operator fun plus(other: ClaimPathElement): ClaimPath =
        ClaimPath(this.value + other)

    operator fun plus(other: ClaimPath): ClaimPath =
        ClaimPath(this.value + other.value)

    operator fun contains(that: ClaimPath): Boolean =
        value.foldIndexed(this.value.size <= that.value.size) { index, acc, thisElement ->
            fun comp() =
                that.value.getOrNull(index)?.let { thatElement -> thatElement in thisElement } == true
            acc and comp()
        }

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

    /**
     * Gets the ClaimPath of the parent element. Returns `null` to indicate the root element.
     */
    fun parent(): ClaimPath? =
        value.dropLast(1)
            .takeIf { it.isNotEmpty() }
            ?.let { ClaimPath(it) }

    fun head(): ClaimPathElement = value.first()
    fun tail(): ClaimPath? {
        val tailElements = value.drop(1)
        return if (tailElements.isEmpty()) return null
        else ClaimPath(tailElements)
    }
    fun last(): ClaimPathElement = value.last()

    /**
     * Gets the [head]
     */
    operator fun component1(): ClaimPathElement = head()

    /**
     * Gets the [tail]
     */
    operator fun component2(): ClaimPath? = tail()

    companion object {

        operator fun invoke(head: ClaimPathElement, vararg tail: ClaimPathElement): ClaimPath =
            ClaimPath(listOf(head, *tail))
        fun claim(name: String): ClaimPath = ClaimPath(listOf(ClaimPathElement.Claim(name)))
        fun ensureObjectAttributes(claims: List<ClaimPath>) {
            val objAttributePaths = claims.filter { it.head() is ClaimPathElement.Claim }
            val notObjAttributePaths = claims - objAttributePaths.toSet()
            require(notObjAttributePaths.isEmpty()) {
                "Some paths do not point to object attributes: $notObjAttributePaths"
            }
        }
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
     * @param name the attribute name
     */
    @JvmInline
    value class Claim(val name: String) : ClaimPathElement {
        override fun toString() = name
    }

    /**
     * Indication of whether the current instance contains the other.
     * @param that the element to compare with
     * @return in case that the two elements are of the same type, and if they are equal (including attribute),
     * then true is being returned. Also, an [AllArrayElements] contains [ArrayElement].
     * In all other cases, a false is being returned.
     */
    operator fun contains(that: ClaimPathElement): Boolean =
        when (this) {
            AllArrayElements -> when (that) {
                AllArrayElements -> true
                is ArrayElement -> true
                is Claim -> false
            }

            is ArrayElement -> this == that
            is Claim -> this == that
        }
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
        return try {
            val elements = array.map {
                require(it is JsonPrimitive)
                claimPathElement(it)
            }
            require(elements.isNotEmpty()) { "ClaimPath must not be empty" }
            ClaimPath(elements.first(), *elements.drop(1).toTypedArray())
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Failed to deserialize ClaimPath", e)
        }
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
