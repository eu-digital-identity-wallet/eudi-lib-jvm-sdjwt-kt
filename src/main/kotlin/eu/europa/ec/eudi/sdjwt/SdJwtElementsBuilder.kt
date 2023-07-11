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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Allows the convenient use of [SdJwtElementsBuilder]
 * @param builderAction the usage of the builder
 * @return the set of [SD-JWT elements][SdJwtElement]
 */
@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtElementsBuilder.() -> Unit): List<SdJwtElement> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val v = SdJwtElementsBuilder()
    v.builderAction()
    return v.build()
}

/**
 * Builder for conveniently assembling
 * a set of [SD-JWT elements][SdJwtElement]
 */
@SdJwtElementDsl
class SdJwtElementsBuilder
    @PublishedApi
    internal constructor() {

        /**
         * Accumulates plain claims
         */
        private val plainClaims = mutableMapOf<String, JsonElement>()

        /**
         * Accumulates claims to be disclosed in flat manner
         */
        private val flatClaims = mutableMapOf<String, JsonElement>()

        /**
         * Claims to be disclosed in structured manner
         */
        private val structuredClaims = mutableListOf<SdJwtElement.StructuredDisclosed>()

        /**
         * Claims to be disclosed recursively
         */
        private val recursivelyClaims = mutableListOf<SdJwtElement.RecursivelyDisclosed>()

        private val arrays = mutableListOf<SdJwtElement.Array>()

        /**
         * Adds plain claims
         * @param cs claims to add
         */
        fun plain(cs: Claims) {
            plainClaims += cs
        }

        fun flat(cs: Claims) {
            flatClaims += cs
        }

        fun sdArray(a: SdJwtElement.Array) {
            arrays += a
        }

        internal fun structured(s: SdJwtElement.StructuredDisclosed) {
            structuredClaims += s
        }

        fun recursively(claimName: String, cs: Claims) {
            recursively(SdJwtElement.RecursivelyDisclosed(claimName, cs))
        }

        internal fun recursively(r: SdJwtElement.RecursivelyDisclosed) {
            recursivelyClaims += r
        }

        @PublishedApi
        internal fun build(): List<SdJwtElement> =
            buildList {
                add(SdJwtElement.Plain(plainClaims))
                addAll(arrays)
                add(SdJwtElement.FlatDisclosed(flatClaims))
                addAll(structuredClaims)
                addAll(recursivelyClaims)
            }
    }

/**
 * Adds plain claims, using [JsonObjectBuilder]
 * @param builderAction a usage of [JsonObjectBuilder]
 */
fun SdJwtElementsBuilder.plain(builderAction: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    plain(buildJsonObject(builderAction))
}

/**
 * Adds flat claims, using [JsonObjectBuilder]
 * @param builderAction a usage of [JsonObjectBuilder]
 */
fun SdJwtElementsBuilder.flat(builderAction: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit) {
    flat(buildJsonObject(builderAction))
}

fun SdJwtElementsBuilder.sdArray(claimName: String, builderAction: (@SdJwtElementDsl SdArrayBuilder).() -> Unit) {
    val elements = sdArray(builderAction)
    sdArray(SdJwtElement.Array(claimName, elements))
}

/**
 * Adds a structured claim using, recursively, the builder
 * @param claimName the name of the structured claim
 * @param builderAction the usage of the builder
 */
fun SdJwtElementsBuilder.structured(claimName: String, builderAction: SdJwtElementsBuilder.() -> Unit) {
    val element = SdJwtElement.StructuredDisclosed(claimName, sdJwt(builderAction))
    structured(element)
}

/**
 * Adds a structured claim where the given [flatSubClaims] will be flat disclosed.
 * That is the structured claim won't contain neither plain nor structured subclaims
 * @param claimName the name of the structured claim
 * @param flatSubClaims the usage of the builder
 */
fun SdJwtElementsBuilder.structuredWithFlatClaims(claimName: String, flatSubClaims: Claims) {
    val element = SdJwtElement.StructuredDisclosed(claimName, listOf(SdJwtElement.FlatDisclosed(flatSubClaims)))
    structured(element)
}

/**
 * Adds recursively claims
 * @param claimName the name of the top-level claim
 * @param builderAction a usage of [JsonObjectBuilder]
 */
fun SdJwtElementsBuilder.recursively(
    claimName: String,
    builderAction: (@SdJwtElementDsl JsonObjectBuilder).() -> Unit,
) {
    val element = SdJwtElement.RecursivelyDisclosed(claimName, buildJsonObject(builderAction))
    recursively(element)
}

/**
 * Adds the JWT publicly registered JTI claim, in plain
 */
fun SdJwtElementsBuilder.jti(jti: String) {
    plain(buildJsonObject { put("jti", jti) })
}

/**
 * Adds the JWT publicly registered ISS claim (Issuer), in plain
 */
fun SdJwtElementsBuilder.iss(issuer: String) {
    plain(buildJsonObject { put("iss", issuer) })
}

/**
 * Adds the JWT publicly registered SUB claim (Subject), in plain
 */
fun SdJwtElementsBuilder.sub(subject: String) {
    plain(buildJsonObject { put("sub", subject) })
}

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun SdJwtElementsBuilder.nbe(nbe: Instant) {
    nbe(nbe.epochSecond)
}

/**
 *  Adds the JWT publicly registered NBE claim (Not before), in plain
 */
fun SdJwtElementsBuilder.nbe(nbe: Long) {
    plain(buildJsonObject { put("nbe", nbe) })
}

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun SdJwtElementsBuilder.iat(iat: Instant) {
    iat(iat.toEpochMilli())
}

/**
 *  Adds the JWT publicly registered IAT claim (Issued At), in plain
 */
fun SdJwtElementsBuilder.iat(iat: Long) {
    plain(buildJsonObject { put("iat", iat) })
}

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun SdJwtElementsBuilder.exp(exp: Instant) {
    exp(exp.toEpochMilli())
}

/**
 *  Adds the JWT publicly registered EXP claim (Expires), in plain
 */
fun SdJwtElementsBuilder.exp(exp: Long) {
    plain(buildJsonObject { put("exp", exp) })
}

/**
 * Adds the JWT publicly registered AUD claim (single Audience), in plain
 */
fun SdJwtElementsBuilder.aud(aud: String) {
    plain(buildJsonObject { put("aud", aud) })
}

/**
 * Adds the JWT publicly registered AUD claim (multiple Audience), in plain
 */
@OptIn(ExperimentalSerializationApi::class)
fun SdJwtElementsBuilder.aud(aud: Collection<String>) {
    when {
        aud.size == 1 -> aud(aud.first())
        aud.size > 1 -> plain {
            buildJsonObject {
                putJsonArray("aud") { buildJsonArray { addAll(aud) } }
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun sdArray(builderAction: SdArrayBuilder.() -> Unit): List<SdArrayElement> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val v = SdArrayBuilder()
    v.builderAction()
    return v.build()
}

@SdJwtElementDsl
class SdArrayBuilder
    @PublishedApi
    internal constructor() {

        private val elements = mutableListOf<SdArrayElement>()

        fun plain(element: JsonElement) {
            elements.add(SdArrayElement.Plain(element))
        }

        fun sd(element: JsonElement) {
            elements.add(SdArrayElement.SelectivelyDisclosed(element))
        }

        @PublishedApi
        internal fun build(): List<SdArrayElement> = elements.toList()
    }

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdJwtElementDsl
