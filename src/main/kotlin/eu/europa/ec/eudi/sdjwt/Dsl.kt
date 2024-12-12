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

import eu.europa.ec.eudi.sdjwt.DisclosableElement.*
import kotlinx.serialization.json.*

/**
 * Selectively disclosable claims that will be encoded with the flat option.
 * Effectively, this is a specification that will feed [SdJwtFactory] in
 * order to produce the [SdJwt]
 *
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint, that expresses the minimum number of digests at the immediate level
 * of this [DisclosableObjectSpec], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class DisclosableObjectSpec(
    private val content: Map<String, DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : Map<String, DisclosableElement> by content

class DisclosableArraySpec(
    val content: List<DisclosableElement>,
    val minimumDigests: MinimumDigests?,
) : List<DisclosableElement> by content

/**
 * Adds to then current [DisclosableObjectSpec] another [DisclosableObjectSpec] producing
 * a new [DisclosableObjectSpec] containing the merged claims.
 *
 * If the two [DisclosableObjectSpec] contain claims with common names, then the resulting [DisclosableObjectSpec]
 * will preserve the claims of [that]
 *
 * ```
 *   val sdObj1 = buildSdObject(minimumDigests=2) {
 *      sd("a", "foo")
 *      sd("b", "bar")
 *   }
 *
 *   val sdObj2 = buildSdObject(minimumDigests=2) {
 *      plain("a", "ddd")
 *   }
 *
 *   sdObj1 + sdObj2 // will contain "a" to Plain("ddd") and "b" to Sd("bar")
 *
 * ```
 * @param that the other [DisclosableObjectSpec]
 * @receiver the current [DisclosableObjectSpec]
 * @return a new [DisclosableObjectSpec] as described above
 */
operator fun DisclosableObjectSpec.plus(that: DisclosableObjectSpec): DisclosableObjectSpec {
    fun MinimumDigests?.valueOrZero() = this?.value ?: 0
    val newMinimumDigests =
        if (this.minimumDigests == null && that.minimumDigests == null) null
        else MinimumDigests(this.minimumDigests.valueOrZero() + that.minimumDigests.valueOrZero())
    return DisclosableObjectSpec(
        (this as Map<String, DisclosableElement>) + (that as Map<String, DisclosableElement>),
        newMinimumDigests,
    )
}

/**
 * An element that is either always or selectively disclosable
 */
sealed interface Disclosable<out T> {
    /**
     * An element that is always disclosable
     */
    @JvmInline
    value class Always<out T>(val value: T) : Disclosable<T>

    /**
     * An element that is selectively disclosable (as a whole)
     */
    @JvmInline
    value class Selectively<out T>(val value: T) : Disclosable<T>
}

internal fun <T : Any> T.alwaysDisclosable(): Disclosable.Always<T> = Disclosable.Always(this)
internal fun <T : Any> T.selectivelyDisclosable(): Disclosable.Selectively<T> = Disclosable.Selectively(this)
internal fun JsonElement.alwaysDisclosableJson(): DisclosableJson = DisclosableJson(this.alwaysDisclosable())
internal fun JsonElement.selectivelyDisclosableJson(): DisclosableJson = DisclosableJson(this.selectivelyDisclosable())
internal fun DisclosableObjectSpec.alwaysDisclosableObj(): DisclosableObject = DisclosableObject(this.alwaysDisclosable())
internal fun DisclosableObjectSpec.selectivelyDisclosableObj(): DisclosableObject = DisclosableObject(this.selectivelyDisclosable())
internal fun DisclosableArraySpec.alwaysDisclosableArray(): DisclosableArray = DisclosableArray(this.alwaysDisclosable())
internal fun DisclosableArraySpec.selectivelyDisclosableArray(): DisclosableArray = DisclosableArray(this.selectivelyDisclosable())

/**
 * The elements within a [disclosable object][DisclosableObjectSpec]
 */
sealed interface DisclosableElement {

    @JvmInline
    value class DisclosableJson(val disclosable: Disclosable<JsonElement>) : DisclosableElement

    @JvmInline
    value class DisclosableObject(val disclosable: Disclosable<DisclosableObjectSpec>) : DisclosableElement

    @JvmInline
    value class DisclosableArray(val disclosable: Disclosable<DisclosableArraySpec>) : DisclosableElement

    companion object {
        fun sd(es: List<DisclosableElement>, minimumDigests: Int?): DisclosableArray = DisclosableArray(
            Disclosable.Always(
                DisclosableArraySpec(es, minimumDigests.atLeastDigests()),
            ),
        )
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class DisclosableElementDsl

/**
 * [DisclosableArraySpec] is actually a [List] of [elements][SdArrayElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildArraySpec
 */
typealias DisclosableArraySpecBuilder = (@DisclosableElementDsl MutableList<DisclosableElement>)

/**
 * [DisclosableObjectSpec] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildObjectSpec
 */
typealias DisclosableObjectSpecBuilder = (@DisclosableElementDsl MutableMap<String, DisclosableElement>)
typealias SdOrPlainJsonObjectBuilder = (@DisclosableElementDsl JsonObjectBuilder)

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

fun DisclosableArraySpecBuilder.plain(value: String) = add(JsonPrimitive(value).alwaysDisclosableJson())
fun DisclosableArraySpecBuilder.plain(value: Number) = add(JsonPrimitive(value).alwaysDisclosableJson())
fun DisclosableArraySpecBuilder.plain(value: Boolean) = add(JsonPrimitive(value).alwaysDisclosableJson())
fun DisclosableArraySpecBuilder.plain(value: JsonElement) = add(value.alwaysDisclosableJson())

fun DisclosableArraySpecBuilder.sd(value: String) = add(JsonPrimitive(value).selectivelyDisclosableJson())
fun DisclosableArraySpecBuilder.sd(value: Number) = add(JsonPrimitive(value).selectivelyDisclosableJson())
fun DisclosableArraySpecBuilder.sd(value: Boolean) = add(JsonPrimitive(value).selectivelyDisclosableJson())
fun DisclosableArraySpecBuilder.sd(value: JsonElement) = add(value.selectivelyDisclosableJson())

fun DisclosableArraySpecBuilder.plainObject(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) =
    add(buildObjectSpec(minimumDigests, action).alwaysDisclosableObj())
fun DisclosableArraySpecBuilder.sdObject(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) =
    add(buildObjectSpec(minimumDigests, action).selectivelyDisclosableObj())

fun DisclosableArraySpecBuilder.plainArray(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) =
    add(buildArraySpec(minimumDigests, action).alwaysDisclosableArray())
fun DisclosableArraySpecBuilder.sdArray(minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) =
    add(buildArraySpec(minimumDigests, action).selectivelyDisclosableArray())

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
fun DisclosableObjectSpecBuilder.plain(name: String, element: JsonElement) = put(name, element.alwaysDisclosableJson())
fun DisclosableObjectSpecBuilder.plain(name: String, value: String) = put(name, JsonPrimitive(value).alwaysDisclosableJson())
fun DisclosableObjectSpecBuilder.plain(name: String, value: Number) = put(name, JsonPrimitive(value).alwaysDisclosableJson())
fun DisclosableObjectSpecBuilder.plain(name: String, value: Boolean) = put(name, JsonPrimitive(value).alwaysDisclosableJson())

fun DisclosableObjectSpecBuilder.sd(name: String, element: JsonElement) = put(name, element.selectivelyDisclosableJson())
fun DisclosableObjectSpecBuilder.sd(name: String, value: String) = put(name, JsonPrimitive(value).selectivelyDisclosableJson())
fun DisclosableObjectSpecBuilder.sd(name: String, value: Number) = put(name, JsonPrimitive(value).selectivelyDisclosableJson())
fun DisclosableObjectSpecBuilder.sd(name: String, value: Boolean) = put(name, JsonPrimitive(value).selectivelyDisclosableJson())

//
// JsonObject
//
fun DisclosableObjectSpecBuilder.plain(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    put(name, buildObjectSpec(minimumDigests, action).alwaysDisclosableObj())
}
fun DisclosableObjectSpecBuilder.sd(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    put(name, buildObjectSpec(minimumDigests, action).selectivelyDisclosableObj())
}

//
// JsonArray
//
fun DisclosableObjectSpecBuilder.plainArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    put(name, buildArraySpec(minimumDigests, action).alwaysDisclosableArray())
}
fun DisclosableObjectSpecBuilder.sd_Array(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    put(name, buildArraySpec(minimumDigests, action).selectivelyDisclosableArray())
}

// TODO CHeck this
fun DisclosableObjectSpecBuilder.sd(name: String, element: DisclosableElement) = put(name, element)

@Deprecated(
    message = "Deprecated in favor of this function",
    replaceWith = ReplaceWith("plainArray(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.sdArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    plainArray(name, minimumDigests, action)
}

@Deprecated(
    message = "Deprecated in favor of this function",
    replaceWith = ReplaceWith("sd_Array(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.recursiveArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    sd_Array(name, minimumDigests, action)
}

@Deprecated(
    message = "Just use plain",
    replaceWith = ReplaceWith("plain(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.structured(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    plain(name, minimumDigests, action)
}

@Deprecated(
    message = "Just use sd",
    replaceWith = ReplaceWith("sd(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.recursive(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    sd(name, minimumDigests, action)
}

//
// JWT registered claims
//

/**
 * Represents a build action that puts a claim into a container
 * such as a [JsonObject] or [DisclosableObjectSpec].
 *
 * Thus, this alias represent an action of [JsonObjectBuilder] or/and
 * [DisclosableObjectSpecBuilder] respectively
 */
private typealias BuilderAction<V> = (String, V) -> Unit

private fun sub(value: String, action: BuilderAction<String>) = action(RFC7519.SUBJECT, value)
private fun iss(value: String, action: BuilderAction<String>) = action(RFC7519.ISSUER, value)
private fun iat(value: Long, action: BuilderAction<Long>) = action(RFC7519.ISSUED_AT, value)
private fun exp(value: Long, action: BuilderAction<Long>) = action(RFC7519.EXPIRATION_TIME, value)
private fun jti(value: String, action: BuilderAction<String>) = action(RFC7519.JWT_ID, value)
private fun nbf(value: Long, action: BuilderAction<Long>) = action(RFC7519.NOT_BEFORE, value)
private fun aud(aud: List<String>, action: BuilderAction<JsonElement>) = when (aud.size) {
    0 -> Unit
    1 -> action(RFC7519.AUDIENCE, JsonPrimitive(aud[0]))
    else -> action(RFC7519.AUDIENCE, JsonArray(aud.map { JsonPrimitive(it) }))
}

/**
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun DisclosableObjectSpecBuilder.sub(value: String) = sub(value, this::plain)

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun DisclosableObjectSpecBuilder.iss(value: String) = iss(value, this::plain)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun DisclosableObjectSpecBuilder.iat(value: Long) = iat(value, this::plain)

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun DisclosableObjectSpecBuilder.exp(value: Long) = exp(value, this::plain)

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun DisclosableObjectSpecBuilder.jti(value: String) = jti(value, this::plain)

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun DisclosableObjectSpecBuilder.nbf(value: Long) = nbf(value, this::plain)

/**
 * Adds the JWT publicly registered AUD claim (Audience), in plain
 */
fun DisclosableObjectSpecBuilder.aud(vararg value: String) = aud(value.asList(), this::plain)

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * No checks are performed for the [jwk]
 *
 * @param jwk the key to put in confirmation claim.
 */
fun DisclosableObjectSpecBuilder.cnf(jwk: JsonObject) = plain("cnf", buildJsonObject { put("jwk", jwk) })
