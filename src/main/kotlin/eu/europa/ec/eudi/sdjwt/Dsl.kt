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

import eu.europa.ec.eudi.sdjwt.SdJwtElement.*
import kotlinx.serialization.json.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias SdClaim = Pair<String, SdJwtElement>

/**
 * A domain specific language for describing the payload of a SD-JWT
 */
sealed interface SdJwtElement {

    /**
     * Represents an [JsonElement] that is [either][sd] disclosed or not, as a whole.
     */
    sealed interface SdOrPlain : SdJwtElement
    data class Plain(val content: JsonElement) : SdOrPlain
    data class SelectivelyDisclosed(val content: JsonElement) : SdOrPlain {
        init {
            require(content != JsonNull)
        }
    }

    class Obj(private val content: Map<String, SdJwtElement>) : SdJwtElement, Map<String, SdJwtElement> by content
    class Arr(private val content: List<SdOrPlain>) : SdJwtElement, List<SdOrPlain> by content
    data class StructuredObj(val content: Obj) : SdJwtElement
    data class RecursiveObj(val content: Obj) : SdJwtElement
    data class RecursiveArr(val content: Arr) : SdJwtElement
}

typealias SdArrayBuilder = (@SdJwtElementDsl MutableList<SdOrPlain>)

@SdJwtElementDsl
class ObjBuilder
@PublishedApi
internal constructor() {
    private val content = mutableMapOf<String, SdJwtElement>()

    fun plain(name: String, element: JsonElement) {
        content[name] = Plain(element)
    }

    fun sd(name: String, element: JsonElement) {
        content[name] =SelectivelyDisclosed(element)
    }

    fun sd(name: String, element: SdJwtElement) {
        content[name] = element
    }

    @PublishedApi
    internal fun build(): Obj = Obj(content)
}

@OptIn(ExperimentalContracts::class)
inline fun buildObj(builderAction: ObjBuilder.() -> Unit): Obj {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val b = ObjBuilder()
    b.builderAction()
    return b.build()
}

inline fun sdJwt(builderAction: ObjBuilder.() -> Unit): Obj = buildObj(builderAction)

@OptIn(ExperimentalContracts::class)
inline fun buildArr(builderAction: (@SdJwtElementDsl MutableList<SdOrPlain>).() -> Unit): Arr {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val content = mutableListOf<SdOrPlain>()
    content.builderAction()
    return Arr(content.toList())
}

fun ObjBuilder.sd(name: String, value: String) = sd(name, JsonPrimitive(value))
fun ObjBuilder.sd(name: String, value: Number) = sd(name, JsonPrimitive(value))
fun ObjBuilder.sd(name: String, value: Boolean) = sd(name, JsonPrimitive(value))
fun ObjBuilder.sd(obj: Claims) = obj.forEach { (k, v) -> sd(k, v) }
fun ObjBuilder.sd(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) = sd(buildJsonObject(action))
fun ObjBuilder.plain(name: String, value: String) = plain(name, JsonPrimitive(value))
fun ObjBuilder.plain(name: String, value: Number) = plain(name, JsonPrimitive(value))
fun ObjBuilder.plain(name: String, value: Boolean) = plain(name, JsonPrimitive(value))
fun ObjBuilder.plain(obj: Claims) = obj.forEach { (k, v) -> plain(k, v) }
fun ObjBuilder.plain(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) = plain(buildJsonObject(action))
fun ObjBuilder.sdArray(name: String, action: SdArrayBuilder.() -> Unit) {
    sd(name, buildArr(action))
}

fun ObjBuilder.structured(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, StructuredObj(obj))
}

fun ObjBuilder.recursiveArr(name: String, action:  SdArrayBuilder.() -> Unit) {
    val arr = buildArr(action)
    sd(name, RecursiveArr(arr))
}

fun ObjBuilder.recursive(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, RecursiveObj(obj))
}

//
// Methods for building arrays
//
fun SdArrayBuilder.plain(value: String) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Number) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: Boolean) = plain(JsonPrimitive(value))
fun SdArrayBuilder.plain(value: JsonElement) = add(Plain(value))
fun SdArrayBuilder.plain(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) = plain(buildJsonObject(action))
fun SdArrayBuilder.sd(value: String) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Number) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: Boolean) = sd(JsonPrimitive(value))
fun SdArrayBuilder.sd(value: JsonElement) = add(SelectivelyDisclosed(value))
fun SdArrayBuilder.sd(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) = sd(buildJsonObject(action))

//
// JWT registered claims
//

/**
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun ObjBuilder.sub(value: String) = plain("sub", value)

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun ObjBuilder.iss(value: String) = plain("iss", value)

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun ObjBuilder.iat(value: Long) = plain("iat", value)

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun ObjBuilder.exp(value: Long) = plain("exp", value)

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun ObjBuilder.jti(value: String) = plain("jti", value)

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun ObjBuilder.nbe(nbe: Long) = plain("nbe", nbe)

/**
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
 */
fun ObjBuilder.aud(vararg aud: String) {
    when (aud.size) {
        0 -> {}
        1 -> plain("aud", aud[0])
        else -> plain("aud", JsonArray(aud.map { JsonPrimitive(it) }))
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdJwtElementDsl
