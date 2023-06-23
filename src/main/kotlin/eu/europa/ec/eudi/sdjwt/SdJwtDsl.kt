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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed interface SdJwtDsl {

    data class Plain(val claim: Map<String, JsonElement>) : SdJwtDsl
    data class Flat(val claims: Set<Claim>) : SdJwtDsl
    data class Structured(
        val claimName: String,
        val plainSubClaims: Plain = Empty,
        val flatSubClaims: Flat,
    ) : SdJwtDsl

    data class TopLevel(
        val plain: Plain = Empty,
        val flat: Flat? = null,
        val structured: Set<Structured> = emptySet(),
    ) : SdJwtDsl

    companion object {
        val Empty: Plain = Plain(emptyMap())

        fun plain(claim: Map<String, JsonElement>): Plain = Plain(claim)
        fun flat(claims: Set<Claim>): Flat = Flat(claims)
        fun flat(claims: Map<String, JsonElement>): Flat = Flat(claims.asSetOfClaims())
        fun structured(
            claimName: String,
            plainSubClaims: Map<String, JsonElement>,
            flatSubClaims: Map<String, JsonElement>,
        ): Structured =
            Structured(claimName, plain(plainSubClaims), flat(flatSubClaims))

        fun structured(
            claimName: String,
            subClaims: Map<String, JsonElement>,
            plainClaimsFilter: (Claim) -> Boolean,
        ): Structured {
            val plainSubClaims = subClaims.filter { plainClaimsFilter(it.toPair()) }
            val subClaimsToBeDisclosed = subClaims - plainSubClaims.keys
            return structured(claimName, plainSubClaims, subClaimsToBeDisclosed)
        }

        fun allFlat(plain: Map<String, JsonElement> = emptyMap(), flat: Map<String, JsonElement>): TopLevel =
            TopLevel(plain(plain), flat(flat), emptySet())

        private fun Map<String, JsonElement>.asSetOfClaims(): Set<Claim> = entries.map { it.toPair() }.toSet()
    }
}

class SdJwtBuilder
    @PublishedApi
    internal constructor() {
        val plain: MutableMap<String, JsonElement> = mutableMapOf()
        val flat: MutableMap<String, JsonElement> = mutableMapOf()
        val structured: MutableSet<SdJwtDsl.Structured> = mutableSetOf()

        fun plain(builderAction: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(builderAction))
        }

        fun flat(builderAction: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(builderAction))
        }

        fun plain(c: Map<String, JsonElement>) {
            plain.putAll(c)
        }

        fun flat(c: Claim) {
            flat[c.name()] = c.value()
        }

        fun flat(c: Map<String, JsonElement>) {
            flat.putAll(c)
        }

        fun structured(claimName: String, builderAction: StructuredBuilder.() -> Unit) {
            structured.add(buildStructured(claimName, builderAction))
        }

        fun structured(claimName: String, subClaimsToBeDisclosed: Map<String, JsonElement>) {
            structured.add(
                SdJwtDsl.structured(
                    claimName = claimName,
                    plainSubClaims = emptyMap(),
                    flatSubClaims = subClaimsToBeDisclosed,
                ),
            )
        }

        fun Pair<String, Map<String, JsonElement>>.structured() {
            val (claimName, claimValue) = this
            structured(claimName, claimValue)
        }

        fun structured(claimName: String, subClaims: Map<String, JsonElement>, plaintFilter: (Claim) -> Boolean) {
            val plainSubClaims = subClaims.filter { plaintFilter(it.toPair()) }
            val subClaimsToBeDisclosed = subClaims - plainSubClaims.keys
            structured(claimName) {
                if (plainSubClaims.isNotEmpty()) plain(plainSubClaims)
                if (subClaimsToBeDisclosed.isNotEmpty()) flat(subClaimsToBeDisclosed)
            }
        }

        fun build(): SdJwtDsl {
            return SdJwtDsl.TopLevel(
                plain = SdJwtDsl.Plain(JsonObject(plain)),
                flat = if (flat.isEmpty()) null else SdJwtDsl.Flat(flat.entries.map { it.toPair() }.toSet()),
                structured = structured,
            )
        }
    }

class StructuredBuilder
    @PublishedApi
    internal constructor() {
        val plain: MutableMap<String, JsonElement> = mutableMapOf()
        val flat: MutableMap<String, JsonElement> = mutableMapOf()

        fun plain(builderAction: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(builderAction))
        }

        fun flat(builderAction: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(builderAction))
        }

        fun plain(c: JsonObject) {
            plain.putAll(c)
        }

        fun flat(c: Claim) {
            flat[c.name()] = c.value()
        }

        fun flat(c: Map<String, JsonElement>) {
            flat.putAll(c)
        }

        fun build(claimName: String): SdJwtDsl.Structured {
            return SdJwtDsl.Structured(
                claimName = claimName,
                plainSubClaims = SdJwtDsl.Plain(JsonObject(plain)),
                flatSubClaims = SdJwtDsl.Flat(flat.entries.map { it.toPair() }.toSet()),

            )
        }
    }

@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtBuilder.() -> Unit): SdJwtDsl {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val b = SdJwtBuilder()
    b.builderAction()
    return b.build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildStructured(claimName: String, builderAction: StructuredBuilder.() -> Unit): SdJwtDsl.Structured {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val b = StructuredBuilder()
    b.builderAction()
    return b.build(claimName)
}
