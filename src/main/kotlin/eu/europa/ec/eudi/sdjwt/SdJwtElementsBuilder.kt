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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
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

        internal fun structured(s: SdJwtElement.StructuredDisclosed) {
            structuredClaims += s
        }

        internal fun recursively(r: SdJwtElement.RecursivelyDisclosed) {
            recursivelyClaims += r
        }

        @PublishedApi
        internal fun build(): List<SdJwtElement> =
            buildList {
                add(SdJwtElement.Plain(plainClaims))
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

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class SdJwtElementDsl