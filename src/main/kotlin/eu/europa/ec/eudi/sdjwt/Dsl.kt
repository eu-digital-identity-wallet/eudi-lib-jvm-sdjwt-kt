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

import eu.europa.ec.eudi.sdjwt.Disclosable.Always
import eu.europa.ec.eudi.sdjwt.Disclosable.Selectively
import kotlinx.serialization.json.*

/**
 * Selectively disclosable claims that will be encoded with the flat option.
 * Effectively, this is a specification that will feed [SdJwtFactory] in
 * order to produce the [SdJwt]
 *
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint; that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObjectSpec], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableObjectSpec(
    private val content: Map<String, DisclosableElement<*>>,
    val minimumDigests: MinimumDigests?,
) : Map<String, DisclosableElement<*>> by content

class DisclosableArraySpec(
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
 * The elements within a [disclosable object][DisclosableObjectSpec]
 */
sealed interface DisclosableElement<out T : Any> {

    val disclosable: Disclosable<T>

    @JvmInline
    value class DisclosableJson(override val disclosable: Disclosable<JsonElement>) : DisclosableElement<JsonElement>

    @JvmInline
    value class DisclosableObject(override val disclosable: Disclosable<DisclosableObjectSpec>) :
        DisclosableElement<DisclosableObjectSpec>

    @JvmInline
    value class DisclosableArray(override val disclosable: Disclosable<DisclosableArraySpec>) :
        DisclosableElement<DisclosableArraySpec>
}

internal operator fun <T : Any> Disclosable<T>.not(): Disclosable<T> = when (this) {
    is Always<T> -> Selectively(value)
    is Selectively<T> -> Always(value)
}

@Suppress("unchecked_cast")
internal operator fun <T : Any> DisclosableElement<T>.not(): DisclosableElement<T> {
    return when (this) {
        is DisclosableElement.DisclosableJson -> DisclosableElement.DisclosableJson(!disclosable)
        is DisclosableElement.DisclosableObject -> DisclosableElement.DisclosableObject(!disclosable)
        is DisclosableElement.DisclosableArray -> DisclosableElement.DisclosableArray(!disclosable)
    } as DisclosableElement<T>
}

internal fun <T : Any> T.selectivelyDisclosable(): Disclosable<T> = Disclosable.Selectively(this)

@Suppress("unchecked_cast")
internal inline fun <reified T : Any> T.sdElement(): DisclosableElement<T> = when (this) {
    is JsonElement -> DisclosableElement.DisclosableJson(selectivelyDisclosable())
    is DisclosableObjectSpec -> DisclosableElement.DisclosableObject(selectivelyDisclosable())
    is DisclosableArraySpec -> DisclosableElement.DisclosableArray(selectivelyDisclosable())
    else -> error("Not supported")
} as DisclosableElement<T>

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class DisclosableElementDsl

/**
 * [DisclosableArraySpec] is actually a [List] of [elements][DisclosableElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildArraySpec
 */
typealias DisclosableArraySpecBuilder = (@DisclosableElementDsl MutableList<DisclosableElement<*>>)

/**
 * [DisclosableObjectSpec] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildObjectSpec
 */
typealias DisclosableObjectSpecBuilder = (@DisclosableElementDsl MutableMap<String, DisclosableElement<*>>)

//
// Methods for building sd arrays
//
/**
 * A convenient method for building a [DisclosableArraySpec] given a [builderAction]
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
 * @return the [DisclosableArraySpec] described by the [builderAction]
 */
inline fun buildArraySpec(
    minimumDigests: Int?,
    builderAction: DisclosableArraySpecBuilder.() -> Unit,
): DisclosableArraySpec = DisclosableArraySpec(buildList(builderAction), minimumDigests.atLeastDigests())

fun DisclosableArraySpecBuilder.notSd(value: String) = add(!JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.notSd(value: Number) = add(!JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.notSd(value: Boolean) = add(!JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.notSd(value: JsonElement) = add(!value.sdElement())

fun DisclosableArraySpecBuilder.sd(value: String) = add(JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.sd(value: Number) = add(JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.sd(value: Boolean) = add(JsonPrimitive(value).sdElement())
fun DisclosableArraySpecBuilder.sd(value: JsonElement) = add(value.sdElement())

fun DisclosableArraySpecBuilder.notSdObject(
    minimumDigests: Int? = null,
    action: DisclosableObjectSpecBuilder.() -> Unit,
) = add(!buildObjectSpec(minimumDigests, action).sdElement())

fun DisclosableArraySpecBuilder.sdObject(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) =
    add(buildObjectSpec(minimumDigests, action).sdElement())

fun DisclosableArraySpecBuilder.notSdArray(
    minimumDigests: Int? = null,
    action: DisclosableArraySpecBuilder.() -> Unit,
) =
    add(!buildArraySpec(minimumDigests, action).sdElement())

fun DisclosableArraySpecBuilder.sdArray(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) =
    add(buildArraySpec(minimumDigests, action).sdElement())

//
// Methods for building sd arrays
//

/**
 * Factory method for creating a [DisclosableObjectSpec] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests check [DisclosableObjectSpec.minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObjectSpec]
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObjectSpec = buildObjectSpec(minimumDigests, builderAction)

/**
 * Factory method for creating a [DisclosableObjectSpec] using the [DisclosableObjectSpecBuilder]
 * @param minimumDigests check [DisclosableObjectSpec.minimumDigests]
 * @param builderAction some usage/action of the [DisclosableObjectSpecBuilder]
 * @return the [DisclosableObjectSpec]
 */
inline fun buildObjectSpec(
    minimumDigests: Int? = null,
    builderAction: DisclosableObjectSpecBuilder.() -> Unit,
): DisclosableObjectSpec = DisclosableObjectSpec(buildMap(builderAction), minimumDigests.atLeastDigests())

//
// JsonElement
//
fun DisclosableObjectSpecBuilder.notSd(name: String, element: JsonElement) = put(name, !element.sdElement())
fun DisclosableObjectSpecBuilder.notSd(name: String, value: String) = put(name, !JsonPrimitive(value).sdElement())
fun DisclosableObjectSpecBuilder.notSd(name: String, value: Number) = put(name, !JsonPrimitive(value).sdElement())
fun DisclosableObjectSpecBuilder.notSd(name: String, value: Boolean) = put(name, !JsonPrimitive(value).sdElement())
fun DisclosableObjectSpecBuilder.sd(name: String, element: JsonElement) = put(name, element.sdElement())
fun DisclosableObjectSpecBuilder.sd(name: String, value: String) = put(name, JsonPrimitive(value).sdElement())
fun DisclosableObjectSpecBuilder.sd(name: String, value: Number) = put(name, JsonPrimitive(value).sdElement())
fun DisclosableObjectSpecBuilder.sd(name: String, value: Boolean) = put(name, JsonPrimitive(value).sdElement())

//
// JsonObject
//
fun DisclosableObjectSpecBuilder.notSdObject(
    name: String,
    minimumDigests: Int? = null,
    action: (DisclosableObjectSpecBuilder).() -> Unit,
) {
    put(name, !buildObjectSpec(minimumDigests, action).sdElement())
}

fun DisclosableObjectSpecBuilder.sdObject(
    name: String,
    minimumDigests: Int? = null,
    action: (DisclosableObjectSpecBuilder).() -> Unit,
) {
    put(name, buildObjectSpec(minimumDigests, action).sdElement())
}

//
// JsonArray
//
fun DisclosableObjectSpecBuilder.notSdArray(
    name: String,
    minimumDigests: Int? = null,
    action: DisclosableArraySpecBuilder.() -> Unit,
) {
    put(name, !buildArraySpec(minimumDigests, action).sdElement())
}

fun DisclosableObjectSpecBuilder.sdArray(
    name: String,
    minimumDigests: Int? = null,
    action: DisclosableArraySpecBuilder.() -> Unit,
) {
    put(name, buildArraySpec(minimumDigests, action).sdElement())
}

//
// JWT registered claims
//

/**
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun DisclosableObjectSpecBuilder.sub(value: String) = notSd(RFC7519.SUBJECT, value)

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun DisclosableObjectSpecBuilder.iss(value: String) = notSd(RFC7519.ISSUER, value)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun DisclosableObjectSpecBuilder.iat(value: Long) = notSd(RFC7519.ISSUED_AT, value)

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun DisclosableObjectSpecBuilder.exp(value: Long) = notSd(RFC7519.EXPIRATION_TIME, value)

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun DisclosableObjectSpecBuilder.jti(value: String) = notSd(RFC7519.JWT_ID, value)

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun DisclosableObjectSpecBuilder.nbf(value: Long) = notSd(RFC7519.NOT_BEFORE, value)

/**
 * Adds the JWT publicly registered AUD claim (Audience), in plain
 */
fun DisclosableObjectSpecBuilder.aud(vararg value: String) {
    val aud = value.asList()
    val action = { name: String, element: JsonElement -> notSd(name, element) }
    when (aud.size) {
        0 -> Unit
        1 -> action(RFC7519.AUDIENCE, JsonPrimitive(aud[0]))
        else -> action(RFC7519.AUDIENCE, JsonArray(aud.map { JsonPrimitive(it) }))
    }
}

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * No checks are performed for the [jwk]
 *
 * @param jwk the key to put in confirmation claim.
 */
fun DisclosableObjectSpecBuilder.cnf(jwk: JsonObject) = notSd("cnf", buildJsonObject { put("jwk", jwk) })
