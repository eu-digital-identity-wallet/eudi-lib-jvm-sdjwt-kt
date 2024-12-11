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

import eu.europa.ec.eudi.sdjwt.SdObjectElement.*
import kotlinx.serialization.json.*

/**
 * Selectively disclosable claims that will be encoded with the flat option.
 * Effectively, this is a specification that will feed [SdJwtFactory] in
 * order to produce the [SdJwt]
 *
 * @param content the content of the object. Each of its claims could be always or selectively disclosable
 * @param minimumDigests This is an optional hint, that expresses the minimum number of digests at the immediate level
 * of this [SdObject], that the [SdJwtFactory] will try to satisfy. [SdJwtFactory] will add decoy digests if
 * the number of actual [DisclosureDigest] is less than the [hint][minimumDigests]
 */
class SdObject(
    private val content: Map<String, SdObjectElement>,
    val minimumDigests: MinimumDigests?,
) : Map<String, SdObjectElement> by content, SdObjectElement

/**
 * Adds to then current [SdObject] another [SdObject] producing
 * a new [SdObject] containing the merged claims.
 *
 * If the two [SdObject] contain claims with common names, then the resulting [SdObject]
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
 * @param that the other [SdObject]
 * @receiver the current [SdObject]
 * @return a new [SdObject] as described above
 */
operator fun SdObject.plus(that: SdObject): SdObject {
    fun MinimumDigests?.valueOrZero() = this?.value ?: 0
    val newMinimumDigests =
        if (this.minimumDigests == null && that.minimumDigests == null) null
        else MinimumDigests(this.minimumDigests.valueOrZero() + that.minimumDigests.valueOrZero())
    return SdObject(
        (this as Map<String, SdObjectElement>) + (that as Map<String, SdObjectElement>),
        newMinimumDigests,
    )
}

/**
 * A [JsonElement] that is either always or selectively disclosable
 */
sealed interface DisclosableJsonElement {
    /**
     * A [JsonElement] that is always disclosable
     */
    @JvmInline
    value class Plain(val value: JsonElement) : DisclosableJsonElement

    /**
     * A [JsonElement] that is selectively disclosable (as a whole)
     */
    @JvmInline
    value class Sd(val value: JsonElement) : DisclosableJsonElement {
        init {
            require(value != JsonNull) { "Null cannot be selectively disclosable" }
        }
    }
}

/**
 * The elements of a selectively disclosable array
 */
sealed interface SdArrayElement {
    /**
     * An element which contains any [JsonElement] that is either always or selectively (as a whole) disclosable
     */
    @JvmInline
    value class Disclosable(val disclosable: DisclosableJsonElement) : SdArrayElement

    /**
     * An element that is a selectively disclosable object
     */
    @JvmInline
    value class DisclosableObj(val sdObject: SdObject) : SdArrayElement

    companion object {
        fun plain(content: JsonElement): SdArrayElement = Disclosable(DisclosableJsonElement.Plain(content))
        fun sd(content: JsonElement): SdArrayElement = Disclosable(DisclosableJsonElement.Sd(content))
        fun sd(obj: SdObject): SdArrayElement = DisclosableObj(obj)
    }
}

/**
 * The elements within a [disclosable object][SdObject]
 */
sealed interface SdObjectElement {

    data class Disclosable(val disclosable: DisclosableJsonElement) : SdObjectElement

    /**
     * A selectively disclosable array
     * Each of its elements could be always or selectively disclosable
     */
    class SdArray(private val content: List<SdArrayElement>, val minimumDigests: MinimumDigests?) :
        SdObjectElement, List<SdArrayElement> by content

    /**
     * Selectively disclosable array that will be encoded with the recursive option
     */
    data class RecursiveSdArray(val content: SdArray) : SdObjectElement

    /**
     * Selectively disclosable claims that will be encoded with the recursive option
     */
    data class RecursiveSdObject(val content: SdObject) : SdObjectElement

    companion object {
        fun plain(content: JsonElement): Disclosable = Disclosable(DisclosableJsonElement.Plain(content))
        fun sd(content: JsonElement): Disclosable = Disclosable(DisclosableJsonElement.Sd(content))
        fun sd(es: List<SdArrayElement>, minimumDigests: Int?): SdArray = SdArray(es, minimumDigests.atLeastDigests())
        fun sdRec(es: List<SdArrayElement>, minimumDigests: Int?): RecursiveSdArray = RecursiveSdArray(sd(es, minimumDigests))
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdElementDsl

/**
 * [SdArray] is actually a [List] of [elements][SdArrayElement]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildSdArray
 */
typealias SdArrayBuilder = (@SdElementDsl MutableList<SdArrayElement>)

/**
 * [SdObject] is actually a [Map] of [elements][SdObjectElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildSdObject
 */
typealias SdObjectBuilder = (@SdElementDsl MutableMap<String, SdObjectElement>)
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
 * @return the [SdArray] described by the [builderAction]
 */
inline fun buildSdArray(
    minimumDigests: Int?,
    builderAction: SdArrayBuilder.() -> Unit,
): SdArray = SdArray(buildList(builderAction), minimumDigests.atLeastDigests())
fun SdArrayBuilder.plain(value: String) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Number) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Boolean) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: JsonElement) = add(SdArrayElement.plain(value))

/**
 * Adds a plain claim to a [SdArray] using KotlinX Serialization DSL
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
fun SdArrayBuilder.plain(action: SdOrPlainJsonObjectBuilder.() -> Unit) = plain(buildJsonObject(action))

/**
 * Adds into an [SdArray] an element [claims] that will be translated into a
 * set of claims, in plain, using KotlinX Serialization
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> SdArrayBuilder.plain(claims: E) {
    plain(Json.encodeToJsonElement(claims))
}

fun SdArrayBuilder.sd(value: String) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Number) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Boolean) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: JsonElement) = add(SdArrayElement.sd(value))

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
fun SdArrayBuilder.sd(action: SdOrPlainJsonObjectBuilder.() -> Unit) = sd(buildJsonObject(action))
fun SdArrayBuilder.buildSdObject(minimumDigests: Int? = null, action: SdObjectBuilder.() -> Unit) {
    add(SdArrayElement.sd(eu.europa.ec.eudi.sdjwt.buildSdObject(minimumDigests, action)))
}

/**
 * Adds into an [SdArray] an element [claims] that will be translated into a
 * set of claims, all of them individually selectively disclosable, using KotlinX Serialization
 *
 * @param claims an instance of a kotlin class serializable via KotlinX Serialization
 * @receiver the builder to which the [claims] will be added
 */
inline fun <reified E> SdArrayBuilder.sd(claims: E) {
    sd(Json.encodeToJsonElement(claims))
}

//
// Methods for building sd arrays
//

/**
 * Factory method for creating a [SdObject] using the [SdObjectBuilder]
 * @param minimumDigests check [SdObject.minimumDigests]
 * @param builderAction some usage/action of the [SdObjectBuilder]
 * @return the [SdObject]
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: SdObjectBuilder.() -> Unit,
): SdObject = buildSdObject(minimumDigests, builderAction)

/**
 * Factory method for creating a [SdObject] using the [SdObjectBuilder]
 * @param minimumDigests check [SdObject.minimumDigests]
 * @param builderAction some usage/action of the [SdObjectBuilder]
 * @return the [SdObject]
 */
inline fun buildSdObject(
    minimumDigests: Int? = null,
    builderAction: SdObjectBuilder.() -> Unit,
): SdObject = SdObject(buildMap(builderAction), minimumDigests.atLeastDigests())

fun SdObjectBuilder.plain(name: String, element: JsonElement) = put(name, SdObjectElement.plain(element))
fun SdObjectBuilder.sd(name: String, element: JsonElement) = put(name, SdObjectElement.sd(element))
fun SdObjectBuilder.sd(name: String, element: SdObjectElement) = put(name, element)
fun SdObjectBuilder.sd(name: String, value: String) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(name: String, value: Number) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(name: String, value: Boolean) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(obj: JsonObject) = obj.forEach { (k, v) -> sd(k, v) }

/**
 * Adds into an [SdObject] an element [claims] that will be translated into a
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
inline fun <reified E> SdObjectBuilder.sd(claims: E) {
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
fun SdObjectBuilder.sd(action: SdOrPlainJsonObjectBuilder.() -> Unit) = sd(buildJsonObject(action))
fun SdObjectBuilder.plain(name: String, value: String) = plain(name, JsonPrimitive(value))
fun SdObjectBuilder.plain(name: String, value: Number) = plain(name, JsonPrimitive(value))
fun SdObjectBuilder.plain(name: String, value: Boolean) = plain(name, JsonPrimitive(value))
fun SdObjectBuilder.plain(obj: JsonObject) = obj.forEach { (k, v) -> plain(k, v) }

/**
 * Adds into an [SdObject] an element [claims] that will be translated into a
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
inline fun <reified E> SdObjectBuilder.plain(claims: E) {
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
fun SdObjectBuilder.plain(action: SdOrPlainJsonObjectBuilder.() -> Unit) = plain(buildJsonObject(action))
fun SdObjectBuilder.sdArray(name: String, minimumDigests: Int? = null, action: SdArrayBuilder.() -> Unit) {
    sd(name, buildSdArray(minimumDigests, action))
}

fun SdObjectBuilder.plain(name: String, minimumDigests: Int? = null, action: (SdObjectBuilder).() -> Unit) {
    val obj = buildSdObject(minimumDigests, action)
    put(name, obj)
}

fun SdObjectBuilder.sd(name: String, minimumDigests: Int? = null, action: (SdObjectBuilder).() -> Unit) {
    val obj = buildSdObject(minimumDigests, action)
    sd(name, RecursiveSdObject(obj))
}

@Deprecated(
    message = "Just use plain",
    replaceWith = ReplaceWith("plain(name, minimumDigests, action)"),
)
fun SdObjectBuilder.structured(name: String, minimumDigests: Int? = null, action: (SdObjectBuilder).() -> Unit) {
    plain(name, minimumDigests, action)
}

@Deprecated(
    message = "Just use sd",
    replaceWith = ReplaceWith("sd(name, minimumDigests, action)"),
)
fun SdObjectBuilder.recursive(name: String, minimumDigests: Int? = null, action: (SdObjectBuilder).() -> Unit) {
    sd(name, minimumDigests, action)
}

fun SdObjectBuilder.recursiveArray(name: String, minimumDigests: Int? = null, action: SdArrayBuilder.() -> Unit) {
    val arr = buildSdArray(minimumDigests, action)
    sd(name, RecursiveSdArray(arr))
}

//
// JWT registered claims
//

/**
 * Represents a build action that puts a claim into a container
 * such as a [JsonObject] or [SdObject].
 *
 * Thus, this alias represent an action of [JsonObjectBuilder] or/and
 * [SdObjectBuilder] respectively
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
fun SdObjectBuilder.sub(value: String) = sub(value, this::plain)

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun SdObjectBuilder.iss(value: String) = iss(value, this::plain)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun SdObjectBuilder.iat(value: Long) = iat(value, this::plain)

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun SdObjectBuilder.exp(value: Long) = exp(value, this::plain)

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun SdObjectBuilder.jti(value: String) = jti(value, this::plain)

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun SdObjectBuilder.nbf(value: Long) = nbf(value, this::plain)

/**
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
 */
fun SdObjectBuilder.aud(vararg value: String) = aud(value.asList(), this::plain)

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * No checks are performed for the [jwk]
 *
 * @param jwk the key to put in confirmation claim.
 */
fun SdObjectBuilder.cnf(jwk: JsonObject) =
    plain("cnf", buildJsonObject { put("jwk", jwk) })
