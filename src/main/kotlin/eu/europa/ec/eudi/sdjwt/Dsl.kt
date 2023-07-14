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
 * A domain specific language for describing the payload of a SD-JWT
 */
sealed interface SdElement {

    /**
     * Represents a [JsonElement] that is either selectively disclosable or not
     */
    sealed interface SdOrPlain : SdElement

    /**
     * A [JsonElement] that is always disclosable
     * @param
     */
    data class Plain(val content: JsonElement) : SdOrPlain

    /**
     * A [JsonElement] that is selectively disclosable
     */
    data class Sd(val content: JsonElement) : SdOrPlain {
        init {
            require(content != JsonNull)
        }
    }

    /**
     * Selectively disclosable claims that will be encoded with flat option
     *
     * Each of its claims could be always or selectively disclosable
     */
    class SdObject(private val content: Map<String, SdElement>) : SdElement, Map<String, SdElement> by content

    /**
     * Selectively disclosable array
     * Each of its elements could be always or selectively disclosable
     */
    class SdArray(private val content: List<SdOrPlain>) : SdElement, List<SdOrPlain> by content

    /**
     * Selectively disclosable claims that will be encoded with structured option
     */
    data class StructuredSdObject(val content: SdObject) : SdElement

    /**
     * Selectively disclosable claims that will be encoded with recursive option
     */
    data class RecursiveSdObject(val content: SdObject) : SdElement

    /**
     * Selectively disclosable array that will be encoded with recursive option
     */
    data class RecursiveSdArray(val content: SdArray) : SdElement
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdElementDsl

/**
 * [SdArray] is actually a [List] of [SdOrPlain]
 *
 * So we can use as builder a [MutableList]
 */
typealias SdArrayBuilder = (@SdElementDsl MutableList<SdOrPlain>)

/**
 * [SdObject] is actually a [Map] of [SdElement]
 *
 * So we can use as a builder [MutableMap]
 */
typealias SdObjectBuilder = (@SdElementDsl MutableMap<String, SdElement>)
typealias SdOrPlainJsonObjectBuilder = (@SdElementDsl JsonObjectBuilder)

//
// Methods for building sd array
//

inline fun buildSdArray(builderAction: SdArrayBuilder.() -> Unit): SdArray = SdArray(buildList(builderAction))
fun SdArrayBuilder.plain(value: String) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Number) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Boolean) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: JsonElement) = add(Plain(value))

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
inline fun <reified E> SdArrayBuilder.plain(claims: E) {
    plain(Json.encodeToJsonElement(claims))
}

fun SdArrayBuilder.sd(value: String) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Number) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Boolean) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: JsonElement) = add(Sd(value))

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
inline fun <reified E> SdArrayBuilder.sd(claims: E) {
    sd(Json.encodeToJsonElement(claims))
}

//
// Methods for building sd array
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
 *           put("locality", "Anytown")
 *           put("region", "Anystate")
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
 *           put("locality", "Anytown")
 *           put("region", "Anystate")
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

fun SdObjectBuilder.recursiveArr(name: String, action: SdArrayBuilder.() -> Unit) {
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
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun SdObjectBuilder.sub(value: String) = plain("sub", value)

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun SdObjectBuilder.iss(value: String) = plain("iss", value)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun SdObjectBuilder.iat(value: Long) = plain("iat", value)

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun SdObjectBuilder.exp(value: Long) = plain("exp", value)

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun SdObjectBuilder.jti(value: String) = plain("jti", value)

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun SdObjectBuilder.nbe(nbe: Long) = plain("nbe", nbe)

/**
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
 */
fun SdObjectBuilder.aud(vararg aud: String) {
    when (aud.size) {
        0 -> {}
        1 -> plain("aud", aud[0])
        else -> plain("aud", JsonArray(aud.map { JsonPrimitive(it) }))
    }
}
