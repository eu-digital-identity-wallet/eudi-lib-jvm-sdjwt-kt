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

/**
 * A domain specific language for describing the payload of an SD-JWT
 *
 */
sealed interface SdJwtDsl {
    class Plain internal constructor(val claims: Map<String, JsonElement>) : SdJwtDsl
    class Flat internal constructor(val claims: Map<String, JsonElement>) : SdJwtDsl
    class Structured internal constructor(
        val claimName: String,
        val plainSubClaims: Plain = Empty,
        val flatSubClaims: Flat,
    ) : SdJwtDsl

    class SdJwt internal constructor(
        val plainClaims: Plain = Empty,
        val flatClaims: Flat? = null,
        val structuredClaims: Set<Structured> = emptySet(),
    ) : SdJwtDsl

    companion object {
        val Empty: Plain = Plain(emptyMap())

        fun plain(claim: Map<String, JsonElement>): Plain = Plain(claim)
        fun flat(claims: Map<String, JsonElement>): Flat = Flat(claims)
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

        fun sdJwtAllFlat(plain: Map<String, JsonElement> = emptyMap(), flat: Map<String, JsonElement>): SdJwt =
            SdJwt(plain(plain), flat(flat), emptySet())
    }
}

private interface CmnBuilder {
    val plainClaims: MutableMap<String, JsonElement>
    val flatClaims: MutableMap<String, JsonElement>

    //
    // Basic operations
    //
    fun plain(c: Map<String, JsonElement>) {
        if (c.isNotEmpty()) plainClaims.putAll(c)
    }

    fun flat(c: Map<String, JsonElement>) {
        if (c.isNotEmpty()) flatClaims.putAll(c)
    }

    //
    // Derived operators
    //
    fun flat(c: Claim) {
        flat(mapOf(c))
    }

    //
    // Kotlinx Serialization operators
    //
    fun plain(builderAction: JsonObjectBuilder.() -> Unit) {
        plain(buildJsonObject(builderAction))
    }

    fun flat(builderAction: JsonObjectBuilder.() -> Unit) {
        flat(buildJsonObject(builderAction))
    }
}

class SdJwtBuilder
    @PublishedApi
    internal constructor() : CmnBuilder {
        override val plainClaims: MutableMap<String, JsonElement> = mutableMapOf()
        override val flatClaims: MutableMap<String, JsonElement> = mutableMapOf()
        private val structuredClaims: MutableSet<SdJwtDsl.Structured> = mutableSetOf()

        fun structured(
            claimName: String,
            plainSubClaims: Map<String, JsonElement> = emptyMap(),
            flatSubClaims: Map<String, JsonElement>,
        ) {
            structuredClaims.add(SdJwtDsl.structured(claimName, plainSubClaims, flatSubClaims))
        }

        fun structured(claimName: String, builderAction: StructuredBuilder.() -> Unit) {
            structuredClaims.add(buildStructured(claimName, builderAction))
        }

        //
        // Derived operators
        //

        fun structured(claimName: String, subClaims: Map<String, JsonElement>, plaintFilter: (Claim) -> Boolean) {
            val plainSubClaims = subClaims.filter { plaintFilter(it.toPair()) }
            val subClaimsToBeDisclosed = subClaims - plainSubClaims.keys
            structured(claimName) {
                if (plainSubClaims.isNotEmpty()) plain(plainSubClaims)
                if (subClaimsToBeDisclosed.isNotEmpty()) flat(subClaimsToBeDisclosed)
            }
        }

        fun build(): SdJwtDsl.SdJwt =
            SdJwtDsl.SdJwt(
                plainClaims = SdJwtDsl.Plain(JsonObject(plainClaims)),
                flatClaims = if (flatClaims.isEmpty()) null else SdJwtDsl.Flat(flatClaims),
                structuredClaims = structuredClaims,
            )
    }

class StructuredBuilder
    @PublishedApi
    internal constructor() : CmnBuilder {
        override val plainClaims: MutableMap<String, JsonElement> = mutableMapOf()
        override val flatClaims: MutableMap<String, JsonElement> = mutableMapOf()

        fun build(claimName: String): SdJwtDsl.Structured =
            SdJwtDsl.structured(claimName, plainClaims, flatClaims)
    }

@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtBuilder.() -> Unit): SdJwtDsl.SdJwt {
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
