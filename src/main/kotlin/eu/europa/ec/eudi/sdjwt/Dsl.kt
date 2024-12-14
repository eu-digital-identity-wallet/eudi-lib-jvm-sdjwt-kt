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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A disclosable object which contains disclosable [claims][DisclosableElement].
 *
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableObject(
    content: Map<String, DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : Map<String, DisclosableElement> by content

/**
 * An array of disclosable [claims][DisclosableElement]
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableArray(
    content: List<DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : List<DisclosableElement> by content

/**
 * Specifies whether something is claim [always][Disclosable.Always]
 * or [Disclosable.Selectively] disclosable.
 */
enum class Disclosable {
    Always, Selectively;

    operator fun not(): Disclosable = when (this) {
        Always -> Selectively
        Selectively -> Always
    }
}

/**
 * Values that can be disclosed:
 * - [JSON][DisclosableValue.Json]
 * - [A nested disclosable object][DisclosableValue.Obj]
 * - [A nested disclosable array][DisclosableValue.Arr]
 */
sealed interface DisclosableValue {
    /**
     * A disclosable [JSON][value]
     * @param value a nested disclosable JSON
     */
    @JvmInline
    value class Json(val value: JsonElement) : DisclosableValue

    /**
     * A nested disclosable [object][value]
     * @param value a nested disclosable object
     */
    @JvmInline
    value class Obj(val value: DisclosableObject) : DisclosableValue

    /**
     * A nested disclosable [array][value]
     * @param value the nested disclosable array
     */
    @JvmInline
    value class Arr(val value: DisclosableArray) : DisclosableValue
}

/**
 * A disclosable claim (value)
 *
 * @param disclosable whether claims is always or selectively disclosable
 * @param element the value of the claim
 */
data class DisclosableElement(val disclosable: Disclosable, val element: DisclosableValue) {
    operator fun not(): DisclosableElement = copy(!disclosable)
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class DisclosableElementDsl

/**
 * [DisclosableArray] is actually a [List] of [elements][DisclosableElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildDisclosableArray
 */
@DisclosableElementDsl
class DisclosableArraySpecBuilder(
    val elements: MutableList<DisclosableElement>,
) : MutableList<DisclosableElement> by elements {

    private fun addClaim(disclosable: Disclosable, value: JsonElement) {
        add(DisclosableElement(disclosable, DisclosableValue.Json(value)))
    }

    private fun addObjClaim(disclosable: Disclosable, value: DisclosableObject) {
        add(DisclosableElement(disclosable, DisclosableValue.Obj(value)))
    }

    private fun addArrClaim(disclosable: Disclosable, value: DisclosableArray) {
        add(DisclosableElement(disclosable, DisclosableValue.Arr(value)))
    }

    fun claim(value: String): Unit = addClaim(Disclosable.Always, JsonPrimitive(value))
    fun claim(value: Number): Unit = addClaim(Disclosable.Always, JsonPrimitive(value))
    fun claim(value: Boolean): Unit = addClaim(Disclosable.Always, JsonPrimitive(value))
    fun sdClaim(value: String): Unit = addClaim(Disclosable.Selectively, JsonPrimitive(value))
    fun sdClaim(value: Number): Unit = addClaim(Disclosable.Selectively, JsonPrimitive(value))
    fun sdClaim(value: Boolean): Unit = addClaim(Disclosable.Selectively, JsonPrimitive(value))

    internal fun claim(value: JsonElement): Unit = addClaim(Disclosable.Always, value)
    internal fun sdClaim(value: JsonElement): Unit = addClaim(Disclosable.Selectively, value)

    fun objClaim(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit): Unit =
        addObjClaim(Disclosable.Always, buildDisclosableObject(minimumDigests, action))
    fun sdObjClaim(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit): Unit =
        addObjClaim(Disclosable.Selectively, buildDisclosableObject(minimumDigests, action))

    fun arrClaim(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit): Unit =
        addArrClaim(Disclosable.Always, buildDisclosableArray(minimumDigests, action))
    fun sdArrClaim(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit): Unit =
        addArrClaim(Disclosable.Selectively, buildDisclosableArray(minimumDigests, action))
}

/**
 * A convenient method for building a [DisclosableArray] given a [builderAction]
 * ```
 * val arr = buildDisclosableArray{
 *    // adds non-selectively disclosable primitive
 *    notSd("DE")
 *    // adds selectively disclosable primitive
 *    sd("GR")
 *    // add selectively disclosable object
 *    sd("over_18", true)
 *    sd("over_25", false)
 *
 * }
 * ```
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 * @return the [DisclosableArray] described by the [builderAction]
 */
inline fun buildDisclosableArray(
    minimumDigests: Int?,
    builderAction: DisclosableArraySpecBuilder.() -> Unit,
): DisclosableArray {
    val content = DisclosableArraySpecBuilder(mutableListOf()).apply(builderAction)
    return DisclosableArray(content, minimumDigests.atLeastDigests())
}

/**
 * [DisclosableObject] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildDisclosableObject
 */
@DisclosableElementDsl
class DisclosableObjectSpecBuilder(
    val elements: MutableMap<String, DisclosableElement>,
) : MutableMap<String, DisclosableElement> by elements {

    private fun putClaim(name: String, disclosable: Disclosable, value: JsonElement) {
        put(name, DisclosableElement(disclosable, DisclosableValue.Json(value)))
    }

    private fun addObjClaim(name: String, disclosable: Disclosable, value: DisclosableObject) {
        put(name, DisclosableElement(disclosable, DisclosableValue.Obj(value)))
    }

    private fun putArrClaim(name: String, disclosable: Disclosable, value: DisclosableArray) {
        put(name, DisclosableElement(disclosable, DisclosableValue.Arr(value)))
    }

    internal fun claim(name: String, element: JsonElement): Unit = putClaim(name, Disclosable.Always, element)
    internal fun sdClaim(name: String, element: JsonElement): Unit = putClaim(name, Disclosable.Selectively, element)

    fun claim(name: String, value: String): Unit = putClaim(name, Disclosable.Always, JsonPrimitive(value))
    fun claim(name: String, value: Number): Unit = putClaim(name, Disclosable.Always, JsonPrimitive(value))
    fun claim(name: String, value: Boolean): Unit = putClaim(name, Disclosable.Always, JsonPrimitive(value))

    fun sdClaim(name: String, value: String): Unit = putClaim(name, Disclosable.Selectively, JsonPrimitive(value))
    fun sdClaim(name: String, value: Number): Unit = putClaim(name, Disclosable.Selectively, JsonPrimitive(value))
    fun sdClaim(name: String, value: Boolean): Unit = putClaim(name, Disclosable.Selectively, JsonPrimitive(value))

    fun objClaim(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit): Unit =
        addObjClaim(name, Disclosable.Always, buildDisclosableObject(minimumDigests, action))
    fun sdObjClaim(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit): Unit =
        addObjClaim(name, Disclosable.Selectively, buildDisclosableObject(minimumDigests, action))

    fun arrClaim(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit): Unit =
        putArrClaim(name, Disclosable.Always, buildDisclosableArray(minimumDigests, action))
    fun sdArrClaim(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit): Unit =
        putArrClaim(name, Disclosable.Selectively, buildDisclosableArray(minimumDigests, action))
}

/**
 * Factory method for creating a [DisclosableObject] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObject]
 */
inline fun buildDisclosableObject(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObject {
    val content = DisclosableObjectSpecBuilder(mutableMapOf()).apply(builderAction)
    return DisclosableObject(content.elements, minimumDigests.atLeastDigests())
}

/**
 * Factory method for creating a [DisclosableObject] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObject]
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObject = buildDisclosableObject(minimumDigests, builderAction)
