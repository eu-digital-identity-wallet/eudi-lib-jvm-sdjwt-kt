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

import eu.europa.ec.eudi.sdjwt.SdElement.*
import kotlinx.serialization.json.*

/**
 * Selectively disclosable claims that will be encoded with the flat option
 *
 * Each of its claims could be always or selectively disclosable
 */
class SdObject(private val content: Map<String, SdElement>) : Map<String, SdElement> by content

/**
 * Adds to then current [SdObject] another [SdObject] producing
 * a new [SdObject] containing the merged claims.
 *
 * If the two [SdObject] contain claims with common names, then the resulting [SdObject]
 * will preserve the claims of [that]
 *
 * ```
 *   val sdObj1 = buildSdObject {
 *      sd{
 *          put("a", "foo")
 *          put("b", "bar")
 *      }
 *   }
 *
 *   val sdObj2 = buildSdObject {
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
operator fun SdObject.plus(that: SdObject): SdObject =
    SdObject((this as Map<String, SdElement>) + (that as Map<String, SdElement>))

/**
 * Represents a [JsonElement] that is either selectively disclosable or not
 */
sealed interface SdOrPlain {
    data class PLainArrEl(val content: JsonElement) : SdOrPlain
    data class SdArrayEl(val content: JsonElement) : SdOrPlain

    data class SdObjArrayEl(val content: SdObject) : SdOrPlain
}

/**
 * A domain-specific language for describing the payload of a SD-JWT
 *
 * @see sdJwt for defining the claims of an SD-JWT
 */
sealed interface SdElement {

    /**
     * A [JsonElement] that is always disclosable
     * @param
     */
    data class Plain(val content: JsonElement) : SdElement

    /**
     * A [JsonElement] that is selectively disclosable
     */
    data class Sd(val content: JsonElement) : SdElement {
        init {
            require(content != JsonNull) { "Null cannot be selectively disclosable" }
        }
    }

    /**
     * A selectively disclosable array
     * Each of its elements could be always or selectively disclosable
     */
    class SdArray(private val content: List<SdOrPlain>) : SdElement, List<SdOrPlain> by content

    /**
     * Selectively disclosable claims that will be encoded with the structured option
     */
    data class StructuredSdObject(val content: SdObject) : SdElement

    /**
     * Selectively disclosable claims that will be encoded with the recursive option
     */
    data class RecursiveSdObject(val content: SdObject) : SdElement

    /**
     * Selectively disclosable array that will be encoded with the recursive option
     */
    data class RecursiveSdArray(val content: SdArray) : SdElement
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdElementDsl

/**
 * [SdArray] is actually a [List] of [elements][SdOrPlain]
 *
 * So we can use as builder a [MutableList]
 *
 * @see buildSdArray
 */
typealias SdArrayBuilder = (@SdElementDsl MutableList<SdOrPlain>)

/**
 * [SdObject] is actually a [Map] of [elements][SdElement]
 *
 * So we can use as a builder [MutableMap]
 *
 * @see sdJwt
 * @see buildSdObject
 */
typealias SdObjectBuilder = (@SdElementDsl MutableMap<String, SdElement>)
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
inline fun buildSdArray(builderAction: SdArrayBuilder.() -> Unit): SdArray = SdArray(buildList(builderAction))
fun SdArrayBuilder.plain(value: String) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Number) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Boolean) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: JsonElement) = add(SdOrPlain.PLainArrEl(value))

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
fun SdArrayBuilder.sd(value: JsonElement) = add(SdOrPlain.SdArrayEl(value))

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
fun SdArrayBuilder.sd2(action: SdObjectBuilder.() -> Unit) {
    add(SdOrPlain.SdObjArrayEl(buildSdObject(action)))
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
 * @param builderAction some usage/action of the [SdObjectBuilder]
 * @return the [SdObject]
 */
inline fun sdJwt(builderAction: SdObjectBuilder.() -> Unit): SdObject = buildSdObject(builderAction)

/**
 * Factory method for creating a [SdObject] using the [SdObjectBuilder]
 * @param builderAction some usage/action of the [SdObjectBuilder]
 * @return the [SdObject]
 */
inline fun buildSdObject(builderAction: SdObjectBuilder.() -> Unit): SdObject = SdObject(buildMap(builderAction))
fun SdObjectBuilder.plain(name: String, element: JsonElement) = put(name, Plain(element))
fun SdObjectBuilder.sd(name: String, element: JsonElement) = put(name, Sd(element))
fun SdObjectBuilder.sd(name: String, element: SdElement) = put(name, element)
fun SdObjectBuilder.sd(name: String, value: String) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(name: String, value: Number) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(name: String, value: Boolean) = sd(name, JsonPrimitive(value))
fun SdObjectBuilder.sd(obj: Claims) = obj.forEach { (k, v) -> sd(k, v) }

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
fun SdObjectBuilder.plain(obj: Claims) = obj.forEach { (k, v) -> plain(k, v) }

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
fun SdObjectBuilder.sdArray(name: String, action: SdArrayBuilder.() -> Unit) {
    sd(name, buildSdArray(action))
}

fun SdObjectBuilder.structured(name: String, action: (SdObjectBuilder).() -> Unit) {
    val obj = buildSdObject(action)
    sd(name, StructuredSdObject(obj))
}

fun SdObjectBuilder.recursiveArray(name: String, action: SdArrayBuilder.() -> Unit) {
    val arr = buildSdArray(action)
    sd(name, RecursiveSdArray(arr))
}

fun SdObjectBuilder.recursive(name: String, action: (SdObjectBuilder).() -> Unit) {
    val obj = buildSdObject(action)
    sd(name, RecursiveSdObject(obj))
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

private fun sub(value: String, action: BuilderAction<String>) = action("sub", value)
private fun iss(value: String, action: BuilderAction<String>) = action("iss", value)
private fun iat(value: Long, action: BuilderAction<Long>) = action("iat", value)
private fun exp(value: Long, action: BuilderAction<Long>) = action("exp", value)
private fun jti(value: String, action: BuilderAction<String>) = action("jti", value)
private fun nbf(value: Long, action: BuilderAction<Long>) = action("nbf", value)
private fun aud(aud: List<String>, action: BuilderAction<JsonElement>) = when (aud.size) {
    0 -> Unit
    1 -> action("aud", JsonPrimitive(aud[0]))
    else -> action("aud", JsonArray(aud.map { JsonPrimitive(it) }))
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
