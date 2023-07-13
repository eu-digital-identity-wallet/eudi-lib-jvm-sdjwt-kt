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
import java.time.Instant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract




@SdJwtElementDsl
class ObjBuilder @PublishedApi internal constructor() {
    private val content = mutableMapOf<String, SdJsonElement>()

    fun plain(name: String, value: JsonElement) {
        content += (name to SdJsonElement.Flat(false, value))
    }


    fun sd(name: String, value: JsonElement) {
        content += (name to SdJsonElement.Flat(true, value))
    }

    fun sd(name: String, value: SdJsonElement) {
        content += (name to value)
    }

    @PublishedApi
    internal fun build(): SdJsonElement.Obj = SdJsonElement.Obj(content)

}

@OptIn(ExperimentalContracts::class)
inline fun buildObj(builderAction: ObjBuilder.() -> Unit): SdJsonElement.Obj {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val b = ObjBuilder()
    b.builderAction()
    return b.build()
}


inline fun sdJwt(builderAction: ObjBuilder.() -> Unit): SdJsonElement.Obj {
    return buildObj(builderAction)
}

@OptIn(ExperimentalContracts::class)
inline fun buildArr(builderAction: (@SdJwtElementDsl MutableList<SdJsonElement.Flat>).() -> Unit): SdJsonElement.Arr {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val content = mutableListOf<SdJsonElement.Flat>()
    content.builderAction()
    return SdJsonElement.Arr(content.toList())
}


fun ObjBuilder.sd(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    sd(buildJsonObject(action))
}

fun ObjBuilder.sd(obj: Claims) {
    obj.forEach { (k, v) -> sd(k, v) }
}

fun ObjBuilder.sd(name: String, value: String) {
    sd(name, JsonPrimitive(value))
}

fun ObjBuilder.sd(name: String, value: Number) {
    sd(name, JsonPrimitive(value))
}

fun ObjBuilder.sd(name: String, value: Boolean) {
    sd(name, JsonPrimitive(value))
}


fun ObjBuilder.plain(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    plain(buildJsonObject(action))
}

fun ObjBuilder.plain(obj: Claims) {
    obj.forEach { (k, v) -> plain(k, v) }
}

fun ObjBuilder.plain(name: String, value: String) {
    plain(name, JsonPrimitive(value))
}

fun ObjBuilder.plain(name: String, value: Number) {
    plain(name, JsonPrimitive(value))
}

fun ObjBuilder.plain(name: String, value: Boolean) {
    plain(name, JsonPrimitive(value))
}


fun ObjBuilder.sdArray(name: String, action: (@SdJwtElementDsl MutableList<SdJsonElement.Flat>).() -> Unit) {
    sd(name, buildArr(action))
}




fun ObjBuilder.structured(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, SdJsonElement.StructuredObj(obj))
}

fun ObjBuilder.recursiveArr(name: String, action: (@SdJwtElementDsl MutableList<SdJsonElement.Flat>).() -> Unit) {
    val arr = buildArr(action)

    sd(name, SdJsonElement.RecursiveArr(arr))
}

fun ObjBuilder.recursive(name: String, action: (@SdJwtElementDsl ObjBuilder).() -> Unit) {
    val obj = buildObj(action)
    sd(name, SdJsonElement.RecursiveObj(obj))
}

@Deprecated(message = "Remove it")
fun ObjBuilder.recursiveOld(name: String, claims: Claims) {
    val obj = buildObj{
        sd(claims)
    }
    sd(name, SdJsonElement.RecursiveObj(obj))
}


fun MutableList<SdJsonElement.Flat>.sd(value: String) {
    sd(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.sd(value: Boolean) {
    sd(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.sd(value: Number) {
    sd(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.plain(value: String) {
    plain(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.plain(value: Boolean) {
    plain(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.plain(value: Number) {
    plain(JsonPrimitive(value))
}

fun MutableList<SdJsonElement.Flat>.sd(value: JsonElement) {
    add(SdJsonElement.Flat(true, value))
}

fun MutableList<SdJsonElement.Flat>.plain(value: JsonElement) {
    add(SdJsonElement.Flat(false, value))
}

fun MutableList<SdJsonElement.Flat>.sd(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    sd(buildJsonObject(action))
}

fun MutableList<SdJsonElement.Flat>.plain(action: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    plain(buildJsonObject(action))
}



//
// JWT registered claims
//

/**
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun ObjBuilder.sub(value: String) {
    plain("sub", value)
}

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun ObjBuilder.iss(value: String) {
    plain("iss", value)
}
/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun ObjBuilder.iat(value: Long) {
    plain("iat", value)
}


/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun ObjBuilder.iat(iat: Instant) {
    iat(iat.toEpochMilli())
}

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun ObjBuilder.exp(value: Long) {
    plain("exp", value)
}

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun ObjBuilder.exp(exp: Instant) {
    exp(exp.toEpochMilli())
}


/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun ObjBuilder.jti(value: String) {
    plain("jti", value)
}

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun ObjBuilder.nbe(nbe: Instant) {
    nbe(nbe.epochSecond)
}

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun ObjBuilder.nbe(nbe: Long) {
    plain("nbe", nbe)
}

/**
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
 */
fun ObjBuilder.aud(aud: String) {
    plain("aud", aud)
}


@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdJwtElementDsl
