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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 */
object DefaultDisclosuresCreatorFactory {

    /**
     *  @param hashAlgorithm the hash algorithm to be used for hashing disclosures
     *  @param saltProvider the [Salt] generator used to the calculation of [Disclosure]
     *
     */
    fun create(
        hashAlgorithm: HashAlgorithm = DefaultHashedDisclosureCreator.hashAlgorithm,
        saltProvider: SaltProvider = SaltProvider.Default,
        numOfDecoys: Int,
    ): DisclosuresCreator<JsonElement, JsonObject> =
        DisclosuresCreator(
            jsonOperations = KotlinXSerializationOps,
            hashedDisclosureCreator = hashedDisclosureCreator(hashAlgorithm, saltProvider),
            numOfDecoys = numOfDecoys,
        )

    private fun hashedDisclosureCreator(hashAlgorithm: HashAlgorithm, saltProvider: SaltProvider) =
        HashedDisclosureCreatorImpl(hashAlgorithm, saltProvider, KotlinxDisclosureCreator)

    private val KotlinxDisclosureCreator: DisclosureCreator<JsonElement> =
        DisclosureCreator { saltProvider: SaltProvider, claim: Pair<String, JsonElement> ->
            Disclosure.encode(saltProvider, claim)
        }

    private val DefaultHashedDisclosureCreator: HashedDisclosureCreator<JsonElement> =
        hashedDisclosureCreator(HashAlgorithm.SHA_256, SaltProvider.Default)

    @OptIn(ExperimentalSerializationApi::class)
    private val KotlinXSerializationOps = object : JsonOperations<JsonElement, JsonObject> {
        override val zero: JsonObject = JsonObject(emptyMap())
        override fun createArrayOfStrings(ss: Collection<String>): JsonElement = buildJsonArray { addAll(ss) }
        override fun stringElement(s: String): JsonElement = JsonPrimitive(s)
        override fun nestClaims(claimName: String, claims: JsonObject): JsonObject =
            buildJsonObject { put(claimName, claims) }

        override fun createObjectFromClaims(cs: Claims<JsonElement>): JsonObject = JsonObject(cs)
        override fun add(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a + b)
    }
}

/**
 * Allows the convenient use of [SdJwtElementsBuilder]
 * @param builderAction the usage of the builder
 * @return the set of [SD-JWT elements][SdJwtElement]
 */
@OptIn(ExperimentalContracts::class)
inline fun sdJwt(builderAction: SdJwtElementsBuilder.() -> Unit): Set<SdJwtElement<JsonElement>> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val v = SdJwtElementsBuilder()
    v.builderAction()
    return v.build()
}

/**
 * Builder for conveniently assembling
 * a set of [SD-JWT elements][SdJwtElement]
 */
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
         * Accumulates claims to be disclosed in structured manner
         */
        private val structuredClaims = mutableSetOf<SdJwtElement.StructuredDisclosed<JsonElement>>()

        /**
         * Adds plain claims
         * @param cs claims to add
         */
        fun plain(cs: Claims<JsonElement>) {
            plainClaims.putAll(cs)
        }

        /**
         * Adds plain claims
         * @param builderAction a usage of a json builder
         */
        fun plain(builderAction: JsonObjectBuilder.() -> Unit) {
            plain(buildJsonObject(builderAction))
        }

        fun flat(cs: Claims<JsonElement>) {
            flatClaims.putAll(cs)
        }

        fun flat(builderAction: JsonObjectBuilder.() -> Unit) {
            flat(buildJsonObject(builderAction))
        }

        /**
         * Adds a structured claim where the given [flatSubClaims] will be flat disclosed.
         * That is the structured claim won't contain neither plain nor structured subclaims
         * @param claimName the name of the structured claim
         * @param flatSubClaims the usage of the builder
         */
        fun structuredWithFlatClaims(claimName: String, flatSubClaims: Claims<JsonElement>) {
            val element = SdJwtElement.StructuredDisclosed(claimName, setOf(SdJwtElement.FlatDisclosed(flatSubClaims)))
            structuredClaims.add(element)
        }

        /**
         * Adds a structured claim using, recursively, the builder
         * @param claimName the name of the structured claim
         * @param builderAction the usage of the builder
         */
        fun structured(claimName: String, builderAction: SdJwtElementsBuilder.() -> Unit) {
            val element = SdJwtElement.StructuredDisclosed(claimName, sdJwt(builderAction))
            structuredClaims.add(element)
        }

        fun build(): Set<SdJwtElement<JsonElement>> =
            buildSet {
                add(SdJwtElement.Plain(plainClaims))
                add(SdJwtElement.FlatDisclosed(flatClaims))
                addAll(structuredClaims)
            }
    }
