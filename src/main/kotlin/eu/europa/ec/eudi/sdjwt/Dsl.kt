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
    val content: List<SdArrayElement>,
    val minimumDigests: MinimumDigests?,
) : List<SdArrayElement> by content

/**
 * Adds to then current [DisclosableObjectSpec] another [DisclosableObjectSpec] producing
 * a new [DisclosableObjectSpec] containing the merged claims.
 *
 * If the two [DisclosableObjectSpec] contain claims with common names, then the resulting [DisclosableObjectSpec]
 * will preserve the claims of [that]
 *
 * ```
 *   val sdObj1 = buildSdObject(minimumDigests=2) {
 *      sd{
 *          put("a", "foo")
 *          put("b", "bar")
 *      }
 *   }
 *
 *   val sdObj2 = buildSdObject(minimumDigests=2) {
 *      plain {
 *          put("a", "ddd")
 *      }
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
 * A [JsonElement] that is either always or selectively disclosable
 */
sealed interface Disclosable<out T> {
    /**
     * A [JsonElement] that is always disclosable
     */
    @JvmInline
    value class Plain<out T>(val value: T) : Disclosable<T>

    /**
     * A [JsonElement] that is selectively disclosable (as a whole)
     */
    @JvmInline
    value class Sd<out T>(val value: T) : Disclosable<T>
}

/**
 * The elements of a selectively disclosable array
 */
sealed interface SdArrayElement {
    /**
     * An element which contains any [JsonElement] that is either always or selectively (as a whole) disclosable
     */
    @JvmInline
    value class DisclosableJson(val disclosable: Disclosable<JsonElement>) : SdArrayElement

    /**
     * An element that is a selectively disclosable object
     */
    @JvmInline
    value class DisclosableObj(val sdObject: DisclosableObjectSpec) : SdArrayElement

    companion object {
        fun plain(content: JsonElement): SdArrayElement = DisclosableJson(Disclosable.Plain(content))
        fun sd(content: JsonElement): SdArrayElement = DisclosableJson(Disclosable.Sd(content))
        fun sd(obj: DisclosableObjectSpec): SdArrayElement = DisclosableObj(obj)
    }
}

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
        fun plain(content: JsonElement): DisclosableJson = DisclosableJson(Disclosable.Plain(content))
        fun sd(content: JsonElement): DisclosableJson = DisclosableJson(Disclosable.Sd(content))
        fun sd(es: List<SdArrayElement>, minimumDigests: Int?): DisclosableArray = DisclosableArray(
            Disclosable.Plain(
                DisclosableArraySpec(es, minimumDigests.atLeastDigests()),
            ),
        )
        fun sdRec(es: List<SdArrayElement>, minimumDigests: Int?): DisclosableArray =
            DisclosableArray(
                Disclosable.Sd(DisclosableArraySpec(es, minimumDigests.atLeastDigests())),
            )
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdElementDsl

/**
 * [DisclosableArraySpec] is actually a [List] of [elements][SdArrayElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildArraySpec
 */
typealias DisclosableArraySpecBuilder = (@SdElementDsl MutableList<SdArrayElement>)

/**
 * [DisclosableObjectSpec] is actually a [Map] of [elements][DisclosableElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildObjectSpec
 */
typealias DisclosableObjectSpecBuilder = (@SdElementDsl MutableMap<String, DisclosableElement>)
typealias SdOrPlainJsonObjectBuilder = (@SdElementDsl JsonObjectBuilder)

//
// Methods for building sd arrays
//
/**
 * A convenient method for building a [SdArray] given a [builderAction]
 * ```
 * val arr = buildSdArray{
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
fun DisclosableArraySpecBuilder.plain(value: String) = plain(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.plain(value: Number) = plain(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.plain(value: Boolean) = plain(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.plain(value: JsonElement) = add(SdArrayElement.plain(value))

/**
 * Adds a plain claim to a [DisclosableArraySpec] using KotlinX Serialization DSL
 *
 * ```
 * sdJwt {
 *   // GR is plain, DE is selectively disclosable
 *   sdArray("nationalities) {
 *      plain("GR")
 *      sd("DE")
 *   }
 *   // work phone is plain, home phone is selectively disclosable
 *   sdArray("phone_numbers") {
 *      plain {
 *          put("number", "+30 12345667")
 *          put("type", "work")
 *      }
 *      sd {
 *          put("number", "+30 55555555")
 *          put("type", "home")
 *      }
 *   }
 * }
 * ```
 */
fun DisclosableArraySpecBuilder.plain(action: SdOrPlainJsonObjectBuilder.() -> Unit) = plain(buildJsonObject(action))

/**
 * Adds into an [SdArray] an element [claims] that will be translated into a
 * set of claims, in plain, using KotlinX Serialization
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> DisclosableArraySpecBuilder.plain(claims: E) {
    plain(Json.encodeToJsonElement(claims))
}

fun DisclosableArraySpecBuilder.sd(value: String) = sd(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.sd(value: Number) = sd(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.sd(value: Boolean) = sd(JsonPrimitive(value))
fun DisclosableArraySpecBuilder.sd(value: JsonElement) = add(SdArrayElement.sd(value))

/**
 * Adds a selectively disclosable claim to a [SdArray] using KotlinX Serialization DSL
 *
 * ```
 * sdJwt {
 *   // GR is plain, DE is selectively disclosable
 *   sdArray("nationalities) {
 *      plain("GR")
 *      sd("DE")
 *   }
 *   // work phone is plain, home phone is selectively disclosable
 *   sdArray("phone_numbers") {
 *      plain {
 *          put("number", "+30 12345667")
 *          put("type", "work")
 *      }
 *      sd {
 *          put("number", "+30 55555555")
 *          put("type", "home")
 *      }
 *   }
 * }
 * ```
 */
fun DisclosableArraySpecBuilder.sd(action: SdOrPlainJsonObjectBuilder.() -> Unit) = sd(buildJsonObject(action))
fun DisclosableArraySpecBuilder.buildObjectSpec(minimumDigests: Int? = null, action: DisclosableObjectSpecBuilder.() -> Unit) {
    add(SdArrayElement.sd(eu.europa.ec.eudi.sdjwt.buildObjectSpec(minimumDigests, action)))
}

/**
 * Adds into an [DisclosableArraySpecBuilder] an element [claims] that will be translated into a
 * set of claims, all of them individually selectively disclosable, using KotlinX Serialization
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> DisclosableArraySpecBuilder.sd(claims: E) {
    sd(Json.encodeToJsonElement(claims))
}

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

fun DisclosableObjectSpecBuilder.plain(name: String, element: JsonElement) = put(name, DisclosableElement.plain(element))
fun DisclosableObjectSpecBuilder.sd(name: String, element: JsonElement) = put(name, DisclosableElement.sd(element))
fun DisclosableObjectSpecBuilder.sd(name: String, element: DisclosableElement) = put(name, element)
fun DisclosableObjectSpecBuilder.sd(name: String, value: String) = sd(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.sd(name: String, value: Number) = sd(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.sd(name: String, value: Boolean) = sd(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.sd(obj: JsonObject) = obj.forEach { (k, v) -> sd(k, v) }

/**
 * Adds into an [DisclosableObjectSpec] an element [claims] that will be translated into a
 * set of claims, each of them selectively disclosable individually, using KotlinX Serialization
 *
 * ```
 * @Serializable
 * data class Address(@SerialName("street_address") val streetAddress: String, @SerialName("postal_code") val postalCode: String)
 * val myAddress = Address("street", "15235")
 *
 * sdJwt {
 *    sd(myAddress)
 *    // is equivalent to
 *    sd {
 *       put("street_address", "street")
 *       put("postal_code", "15235")
 *    }
 * }
 * ```
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> DisclosableObjectSpecBuilder.sd(claims: E) {
    sd(Json.encodeToJsonElement(claims).jsonObject)
}

/**
 * Marks a set of claims expressed using KotlinX Serialization builder
 * as selectively disclosable
 *
 * ```
 * sdJwt {
 *   sd {
 *       put("given_name", "John")
 *       put("family_name", "Doe")
 *       put("email", "johndoe@example.com")
 *       put("phone_number", "+1-202-555-0101")
 *       put("phone_number_verified", true)
 *       putJsonObject("address") {
 *           put("street_address", "123 Main St")
 *           put("locality", "Any town")
 *           put("region", "Any state")
 *           put("country", "US")
 *       }
 *   }
 * }
 * ```
 */
fun DisclosableObjectSpecBuilder.sd(action: SdOrPlainJsonObjectBuilder.() -> Unit) = sd(buildJsonObject(action))
fun DisclosableObjectSpecBuilder.plain(name: String, value: String) = plain(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.plain(name: String, value: Number) = plain(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.plain(name: String, value: Boolean) = plain(name, JsonPrimitive(value))
fun DisclosableObjectSpecBuilder.plain(obj: JsonObject) = obj.forEach { (k, v) -> plain(k, v) }

/**
 * Adds into an [DisclosableObjectSpec] an element [claims] that will be translated into a
 * set of claims, in plain, using KotlinX Serialization
 *
 * ```
 * @Serializable
 * data class Address(@SerialName("street_address") val streetAddress: String, @SerialName("postal_code") val postalCode: String)
 * val myAddress = Address("street", "15235")
 *
 * sdJwt {
 *    plain(myAddress)
 *    // is equivalent to
 *    plain {
 *       put("street_address", "street")
 *       put("postal_code", "15235")
 *    }
 * }
 * ```
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> DisclosableObjectSpecBuilder.plain(claims: E) {
    plain(Json.encodeToJsonElement(claims).jsonObject)
}

/**
 * Marks a set of claims expressed using KotlinX Serialization builder
 * as disclosable (non-selectively)
 *
 * ```
 * sdJwt {
 *   plain {
 *       put("given_name", "John")
 *       put("family_name", "Doe")
 *       put("email", "johndoe@example.com")
 *       put("phone_number", "+1-202-555-0101")
 *       put("phone_number_verified", true)
 *       putJsonObject("address") {
 *           put("street_address", "123 Main St")
 *           put("locality", "Any town")
 *           put("region", "Any state")
 *           put("country", "US")
 *       }
 *   }
 * }
 * ```
 */
fun DisclosableObjectSpecBuilder.plain(action: SdOrPlainJsonObjectBuilder.() -> Unit) = plain(buildJsonObject(action))

@Deprecated(
    message = "Deprecated in favor of this function",
    replaceWith = ReplaceWith("plainArray(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.sdArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    plainArray(name, minimumDigests, action)
}

fun DisclosableObjectSpecBuilder.plainArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    val spec = buildArraySpec(minimumDigests, action)
    put(name, DisclosableArray(Disclosable.Plain(spec)))
}

@Deprecated(
    message = "Deprecated in favor of this function",
    replaceWith = ReplaceWith("sd_Array(name, minimumDigests, action)"),
)
fun DisclosableObjectSpecBuilder.recursiveArray(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    sd_Array(name, minimumDigests, action)
}
fun DisclosableObjectSpecBuilder.sd_Array(name: String, minimumDigests: Int? = null, action: DisclosableArraySpecBuilder.() -> Unit) {
    val spec = buildArraySpec(minimumDigests, action)
    put(name, DisclosableArray(Disclosable.Sd(spec)))
}

fun DisclosableObjectSpecBuilder.plain(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    val obj = buildObjectSpec(minimumDigests, action)
    put(name, DisclosableElement.DisclosableObject(Disclosable.Plain(obj)))
}

fun DisclosableObjectSpecBuilder.sd(name: String, minimumDigests: Int? = null, action: (DisclosableObjectSpecBuilder).() -> Unit) {
    val obj = buildObjectSpec(minimumDigests, action)
    put(name, DisclosableElement.DisclosableObject(Disclosable.Sd(obj)))
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
 * Adds the JWT publicly registered subclaim (Subject)
 */
fun JsonObjectBuilder.sub(value: String) = sub(value, this::put)

/**
 * Adds the JWT publicly registered ISS claim (Issuer)
 */
fun JsonObjectBuilder.iss(value: String) = iss(value, this::put)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At)
 */
fun JsonObjectBuilder.iat(value: Long) = iat(value, this::put)

/**
 *  Adds the JWT publicly registered EXP claim (Expires)
 */
fun JsonObjectBuilder.exp(value: Long) = exp(value, this::put)

/**
 * Adds the JWT publicly registered JTI claim
 */
fun JsonObjectBuilder.jti(value: String) = jti(value, this::put)

/**
 *  Adds the JWT publicly registered NBF claim (Not before)
 */
fun JsonObjectBuilder.nbf(value: Long) = nbf(value, this::put)

/**
 * Adds the JWT publicly registered AUD claim (single Audience)
 */
fun JsonObjectBuilder.aud(vararg value: String) = aud(value.asList(), this::put)

/**
 * Adds the JWT publicly registered subclaim (Subject), in plain
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
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
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
fun DisclosableObjectSpecBuilder.cnf(jwk: JsonObject) =
    plain("cnf", buildJsonObject { put("jwk", jwk) })
