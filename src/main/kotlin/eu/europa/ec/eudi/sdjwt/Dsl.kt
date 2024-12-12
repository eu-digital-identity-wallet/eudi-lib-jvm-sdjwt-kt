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
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableObject(
    private val content: Map<String, DisclosableElement<*>>,
    val minimumDigests: MinimumDigests?,
) : Map<String, DisclosableElement<*>> by content

class DisclosableArray(
    val content: List<DisclosableElement<*>>,
    val minimumDigests: MinimumDigests?,
) : List<DisclosableElement<*>> by content

/**
 * An element that is either always or selectively disclosable
 */
sealed interface Disclosable<out T : Any> {
    /**
     * An element that is always disclosable
     */
    @JvmInline
    value class Always<out T : Any>(val value: T) : Disclosable<T>

    /**
     * An element that is selectively disclosable (as a whole)
     */
    @JvmInline
    value class Selectively<out T : Any>(val value: T) : Disclosable<T>
}

/**
 * The elements within a [disclosable object][Obj]
 */
sealed interface DisclosableElement<out T : Any> {

    val element: Disclosable<T>

    @JvmInline
    value class Json(override val element: Disclosable<JsonElement>) : DisclosableElement<JsonElement>

    @JvmInline
    value class Obj(override val element: Disclosable<DisclosableObject>) : DisclosableElement<DisclosableObject>

    @JvmInline
    value class Arr(override val element: Disclosable<DisclosableArray>) : DisclosableElement<DisclosableArray>
}

internal operator fun <T : Any> Disclosable<T>.not(): Disclosable<T> = when (this) {
    is Disclosable.Always<T> -> Disclosable.Selectively(value)
    is Disclosable.Selectively<T> -> Disclosable.Always(value)
}

@Suppress("unchecked_cast")
internal inline operator fun <reified T : Any> DisclosableElement<T>.not(): DisclosableElement<T> = when (this) {
    is DisclosableElement.Json -> DisclosableElement.Json(!element)
    is DisclosableElement.Obj -> DisclosableElement.Obj(!element)
    is DisclosableElement.Arr -> DisclosableElement.Arr(!element)
} as DisclosableElement<T>

@Suppress("unchecked_cast")
internal inline fun <reified T : Any> T.sdElement(): DisclosableElement<T> = when (this) {
    is JsonElement -> DisclosableElement.Json(Disclosable.Selectively(this))
    is DisclosableObject -> DisclosableElement.Obj(Disclosable.Selectively(this))
    is DisclosableArray -> DisclosableElement.Arr(Disclosable.Selectively(this))
    else -> error("Not supported")
} as DisclosableElement<T>

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class DisclosableElementDsl

/**
 * A convenient method for building a [DisclosableArray] given a [builderAction]
 * ```
 * val arr = buildArraySpec{
 *    // adds non-selectively disclosable primitive
 *    plain("DE")
 *    // adds selectively disclosable primitive
 *    sd("GR")
 *    // add selectively disclosable object
 *    sd {
 *     put("over_18", true)
 *     put("over_25", false)
 *    }
 * }
 * ```
 *
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
 * [DisclosableArray] is actually a [List] of [elements][DisclosableElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildDisclosableArray
 */
@DisclosableElementDsl
class DisclosableArraySpecBuilder(
    internal val elements: MutableList<DisclosableElement<*>>,
) : MutableList<DisclosableElement<*>> by elements {

    fun notSd(value: String) = add(!JsonPrimitive(value).sdElement())
    fun notSd(value: Number) = add(!JsonPrimitive(value).sdElement())
    fun notSd(value: Boolean) = add(!JsonPrimitive(value).sdElement())
    fun notSd(value: JsonElement) = add(!value.sdElement())

    fun sd(value: String) = add(JsonPrimitive(value).sdElement())
    fun sd(value: Number) = add(JsonPrimitive(value).sdElement())
    fun sd(value: Boolean) = add(JsonPrimitive(value).sdElement())
    fun sd(value: JsonElement) = add(value.sdElement())

    fun notSdObject(
        minimumDigests: Int? = null,
        action: DisclosableObjectSpecBuilder.() -> Unit,
    ) = add(!buildObjectSpec(minimumDigests, action).sdElement())

    fun sdObject(
        minimumDigests: Int? = null,
        action: DisclosableObjectSpecBuilder.() -> Unit,
    ) = add(buildObjectSpec(minimumDigests, action).sdElement())

    fun notSdArray(
        minimumDigests: Int? = null,
        action: DisclosableArraySpecBuilder.() -> Unit,
    ) = add(!buildDisclosableArray(minimumDigests, action).sdElement())

    fun sdArray(
        minimumDigests: Int? = null,
        action: DisclosableArraySpecBuilder.() -> Unit,
    ) = add(buildDisclosableArray(minimumDigests, action).sdElement())
}

/**
 * [DisclosableObject] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildObjectSpec
 */
@DisclosableElementDsl
class DisclosableObjectSpecBuilder(val elements: MutableMap<String, DisclosableElement<*>>) :
    MutableMap<String, DisclosableElement<*>> by elements {

        fun notSd(name: String, element: JsonElement) = put(name, !element.sdElement())
        fun notSd(name: String, value: String) = put(name, !JsonPrimitive(value).sdElement())
        fun notSd(name: String, value: Number) = put(name, !JsonPrimitive(value).sdElement())
        fun notSd(name: String, value: Boolean) = put(name, !JsonPrimitive(value).sdElement())
        fun sd(name: String, element: JsonElement) = put(name, element.sdElement())
        fun sd(name: String, value: String) = put(name, JsonPrimitive(value).sdElement())
        fun sd(name: String, value: Number) = put(name, JsonPrimitive(value).sdElement())
        fun sd(name: String, value: Boolean) = put(name, JsonPrimitive(value).sdElement())

        fun notSdObject(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
            put(name, !buildObjectSpec(minimumDigests, action).sdElement())
        }

        fun sdObject(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
            put(name, buildObjectSpec(minimumDigests, action).sdElement())
        }

        fun notSdArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
            put(name, !buildDisclosableArray(minimumDigests, action).sdElement())
        }

        fun sdArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
            put(name, buildDisclosableArray(minimumDigests, action).sdElement())
        }
    }

/**
 * Factory method for creating a [DisclosableObject] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests check [DisclosableObject.minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObject]
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObject = buildObjectSpec(minimumDigests, builderAction)

/**
 * Factory method for creating a [DisclosableObject] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests check [DisclosableObject.minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObject]
 */
inline fun buildObjectSpec(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObject {
    val content = DisclosableObjectSpecBuilder(mutableMapOf()).apply(builderAction)
    return DisclosableObject(content.elements, minimumDigests.atLeastDigests())
}
