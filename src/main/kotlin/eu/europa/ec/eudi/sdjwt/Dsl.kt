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
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableObject(
    content: Map<String, DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : Map<String, DisclosableElement> by content

class DisclosableArray(
    content: List<DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : List<DisclosableElement> by content

enum class Disclosable {
    Always, Selectively;

    operator fun not(): Disclosable = when (this) {
        Always -> Selectively
        Selectively -> Always
    }
}

sealed interface DisclosableValue {
    @JvmInline
    value class Json(val value: JsonElement) : DisclosableValue

    @JvmInline
    value class Obj(val value: DisclosableObject) : DisclosableValue

    @JvmInline
    value class Arr(val value: DisclosableArray) : DisclosableValue
}

/**
 * The elements within a disclosable object
 */
data class DisclosableElement(val disclosable: Disclosable, val element: DisclosableValue) {
    operator fun not(): DisclosableElement = copy(!disclosable)
}

private fun <T : Any> selectivelyDisclosable(t: T): DisclosableElement = when (t) {
    is JsonElement -> DisclosableElement(Disclosable.Selectively, DisclosableValue.Json(t))
    is DisclosableObject -> DisclosableElement(Disclosable.Selectively, DisclosableValue.Obj(t))
    is DisclosableArray -> DisclosableElement(Disclosable.Selectively, DisclosableValue.Arr(t))
    else -> error("Not supported")
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
    internal val elements: MutableList<DisclosableElement>,
) : MutableList<DisclosableElement> by elements {

    fun claim(value: String) = add(!selectivelyDisclosable(JsonPrimitive(value)))
    fun claim(value: Number) = add(!selectivelyDisclosable(JsonPrimitive(value)))
    fun claim(value: Boolean) = add(!selectivelyDisclosable(JsonPrimitive(value)))
    fun claim(value: JsonElement) = add(!selectivelyDisclosable(value))

    fun sdClaim(value: String) = add(selectivelyDisclosable(JsonPrimitive(value)))
    fun sdClaim(value: Number) = add(selectivelyDisclosable(JsonPrimitive(value)))
    fun sdClaim(value: Boolean) = add(selectivelyDisclosable(JsonPrimitive(value)))
    fun sdClaim(value: JsonElement) = add(selectivelyDisclosable(value))

    fun objClaim(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) =
        add(!selectivelyDisclosable(buildDisclosableObject(minimumDigests, action)))

    fun sdObjClaim(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) =
        add(selectivelyDisclosable(buildDisclosableObject(minimumDigests, action)))

    fun arrClaim(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) =
        add(!selectivelyDisclosable(buildDisclosableArray(minimumDigests, action)))

    fun sdArrClaim(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) =
        add(selectivelyDisclosable(buildDisclosableArray(minimumDigests, action)))
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
 * [DisclosableObject] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildDisclosableObject
 */
@DisclosableElementDsl
class DisclosableObjectSpecBuilder(val elements: MutableMap<String, DisclosableElement>) :
    MutableMap<String, DisclosableElement> by elements {

        fun claim(name: String, element: JsonElement) = put(name, !selectivelyDisclosable(element))
        fun claim(name: String, value: String) = put(name, !selectivelyDisclosable(JsonPrimitive(value)))
        fun claim(name: String, value: Number) = put(name, !selectivelyDisclosable(JsonPrimitive(value)))
        fun claim(name: String, value: Boolean) = put(name, !selectivelyDisclosable(JsonPrimitive(value)))
        fun sdClaim(name: String, element: JsonElement) = put(name, selectivelyDisclosable(element))
        fun sdClaim(name: String, value: String) = put(name, selectivelyDisclosable(JsonPrimitive(value)))
        fun sdClaim(name: String, value: Number) = put(name, selectivelyDisclosable(JsonPrimitive(value)))
        fun sdClaim(name: String, value: Boolean) = put(name, selectivelyDisclosable(JsonPrimitive(value)))

        fun objClaim(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
            put(name, !selectivelyDisclosable(buildDisclosableObject(minimumDigests, action)))
        }

        fun sdObjClaim(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
            put(name, selectivelyDisclosable(buildDisclosableObject(minimumDigests, action)))
        }

        fun arrClaim(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
            put(name, !selectivelyDisclosable(buildDisclosableArray(minimumDigests, action)))
        }

        fun sdArrClaim(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
            put(name, selectivelyDisclosable(buildDisclosableArray(minimumDigests, action)))
        }
    }

/**
 * Factory method for creating a [DisclosableObject] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests check [DisclosableObject.minimumDigests]
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
 * @param minimumDigests check [DisclosableObject.minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObject]
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObject = buildDisclosableObject(minimumDigests, builderAction)
