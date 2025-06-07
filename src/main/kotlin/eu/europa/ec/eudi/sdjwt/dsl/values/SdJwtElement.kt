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
package eu.europa.ec.eudi.sdjwt.dsl.values

import eu.europa.ec.eudi.sdjwt.MinimumDigests
import eu.europa.ec.eudi.sdjwt.atLeastDigests
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * Represents the structure of an SD-JWT object (a JSON object)
 * at a specific level of the claim hierarchy.
 *
 * This class holds the actual claims and their disclosable properties,
 * and can optionally specify a [minimumDigests] hint for decoy generation
 * at this level.
 *
 * @param content A map of claim names to their corresponding [SdJwtElement]s.
 * @param minimumDigests An optional hint indicating the minimum number of
 * decoy digests that should be generated for the claims
 * at this object level.
 */
data class SdJwtObject(
    override val content: Map<String, SdJwtElement>,
    val minimumDigests: MinimumDigests?,
) : DisclosableObject<String, JsonElement>

/**
 * Represents the structure of an SD-JWT array (a JSON array)
 * at a specific level of the claim hierarchy.
 *
 * This class holds the actual array elements and their disclosable properties,
 * and can optionally specify a [minimumDigests] hint for decoy generation
 * for the elements within this array.
 *
 * @param content A list of [SdJwtElement]s representing the elements of the array.
 * @param minimumDigests An optional hint indicating the minimum number of
 * decoy digests that should be generated for the elements
 * within this array.
 */
data class SdJwtArray(
    override val content: List<SdJwtElement>,
    val minimumDigests: MinimumDigests?,
) : DisclosableArray<String, JsonElement>

/**
 * A type alias for a [DisclosableElement] that represents a single claim or nested structure
 * within an SD-JWT object or array.
 *
 * The key type is [String] for claim names, and the metadata type is [JsonElement],
 * allowing for flexible attachment of arbitrary JSON-based metadata to each element's definition.
 */
typealias SdJwtElement = DisclosableElement<String, JsonElement>

@PublishedApi
internal fun factory(
    minimumDigests: Int?,
) = object : DisclosableContainerFactory<String, JsonElement, SdJwtObject, SdJwtArray> {

    override fun obj(elements: Map<String, DisclosableElement<String, JsonElement>>): SdJwtObject =
        SdJwtObject(elements, minimumDigests.atLeastDigests())

    override fun arr(elements: List<DisclosableElement<String, JsonElement>>): SdJwtArray =
        SdJwtArray(elements, minimumDigests.atLeastDigests())
}

/**
 * A builder for creating an [SdJwtArray] using a fluent DSL.
 *
 * Provides methods to add regular (non-selectively disclosable) and
 * selectively disclosable (SD) claims of various types, including nested
 * objects and arrays.
 *
 * @property elements The list of [SdJwtElement]s built by this builder.
 */
@DisclosableElementDsl
class SdJwtArrayBuilder(elements: MutableList<SdJwtElement>) {

    private val claims = DisclosableArraySpecBuilder(factory = factory(null), elements)

    val elements: List<SdJwtElement>
        get() = claims.elements

    /**
     * Adds a regular (non-selectively disclosable) JSON element to the array.
     * The value will always be revealed.
     * @param value The [JsonElement] to add.
     */
    fun claim(value: JsonElement): Unit = claims.claim(value)

    /**
     * Adds a regular (non-selectively disclosable) JSON element to the array.
     * The value will always be revealed.
     * @param value The to add.
     * @param serializer The serializer of the value
     * @param V the type of the value
     */
    fun <V>claim(value: V, serializer: KSerializer<V>): Unit = claims.claim(Json.encodeToJsonElement(serializer, value))

    /**
     * Adds a regular (non-selectively disclosable) JSON element to the array.
     * The value will always be revealed.
     * @param value The to add.
     * @param V the type of the value
     */
    inline fun <reified V>claim(value: V): Unit = claim(value, serializer())

    /**
     * Adds a regular (non-selectively disclosable) string value to the array.
     * @param value The [String] to add.
     */
    fun claim(value: String): Unit = claims.claim(JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) number value to the array.
     * @param value The [Number] to add.
     */
    fun claim(value: Number): Unit = claims.claim(JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) boolean value to the array.
     * @param value The [Boolean] to add.
     */
    fun claim(value: Boolean): Unit = claims.claim(JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) JSON element to the array.
     * The value will be hidden by default and can be selectively disclosed.
     * @param value The [JsonElement] to add.
     */
    fun sdClaim(value: JsonElement): Unit = claims.sdClaim(value)

    /**
     * Adds a selectively disclosable (SD) JSON element to the array.
     * The value will be hidden by default and can be selectively disclosed.
     * @param value The value to add.
     * @param serializer The serializer of the value
     * @param V the type of the value
     */
    fun <V>sdClaim(value: V, serializer: KSerializer<V>): Unit = claims.sdClaim(Json.encodeToJsonElement(serializer, value))

    /**
     * Adds a selectively disclosable (SD) JSON element to the array.
     * The value will be hidden by default and can be selectively disclosed.
     * @param value The value to add.
     * @param V the type of the value
     */
    inline fun <reified V>sdClaim(value: V): Unit = sdClaim(value, serializer())

    /**
     * Adds a selectively disclosable (SD) string value to the array.
     * @param value The [String] to add.
     */
    fun sdClaim(value: String): Unit = claims.sdClaim(JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) number value to the array.
     * @param value The [Number] to add.
     */
    fun sdClaim(value: Number): Unit = claims.sdClaim(JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) boolean value to the array.
     * @param value The [Boolean] to add.
     */
    fun sdClaim(value: Boolean): Unit = claims.sdClaim(JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) nested object to the array.
     * The object content will always be revealed.
     * @param minimumDigests An optional hint for decoy digests within this nested object.
     * @param action A lambda with [SdJwtObjectBuilder] as its receiver to define the object's content.
     */
    fun objClaim(minimumDigests: Int? = null, action: SdJwtObjectBuilder.() -> Unit): Unit =
        claims.objClaim(buildSdJwtObject(minimumDigests, action))

    /**
     * Adds a selectively disclosable (SD) nested object to the array.
     * The object content will be hidden by default and can be selectively disclosed.
     * @param minimumDigests An optional hint for decoy digests within this nested object.
     * @param action A lambda with [SdJwtObjectBuilder] as its receiver to define the object's content.
     */
    fun sdObjClaim(minimumDigests: Int? = null, action: SdJwtObjectBuilder.() -> Unit): Unit =
        claims.sdObjClaim(buildSdJwtObject(minimumDigests, action))

    /**
     * Adds a regular (non-selectively disclosable) nested array to the array.
     * The array content will always be revealed.
     * @param minimumDigests An optional hint for decoy digests within this nested array.
     * @param action A lambda with [SdJwtArrayBuilder] as its receiver to define the array's content.
     */
    fun arrClaim(minimumDigests: Int? = null, action: SdJwtArrayBuilder.() -> Unit): Unit =
        claims.arrClaim(buildSdJwtArray(minimumDigests, action))

    /**
     * Adds a selectively disclosable (SD) nested array to the array.
     * The array content will be hidden by default and can be selectively disclosed.
     * @param minimumDigests An optional hint for decoy digests within this nested array.
     * @param action A lambda with [SdJwtArrayBuilder] as its receiver to define the array's content.
     */
    fun sdArrClaim(minimumDigests: Int? = null, action: SdJwtArrayBuilder.() -> Unit): Unit =
        claims.sdArrClaim(buildSdJwtArray(minimumDigests, action))
}

/**
 * Builds an [SdJwtArray] using a fluent DSL.
 *
 * This top-level function provides a convenient way to define the content
 * of an SD-JWT array, including its disclosable elements and nested structures.
 *
 * @param minimumDigests An optional hint for the minimum number of decoy digests
 * that should be generated for the elements within this array.
 * @param builderAction A lambda with [SdJwtArrayBuilder] as its receiver,
 * used to define the array's elements.
 * @return An [SdJwtArray] representing the defined array structure.
 */
inline fun buildSdJwtArray(
    minimumDigests: Int? = null,
    builderAction: SdJwtArrayBuilder.() -> Unit,
): SdJwtArray {
    val builder = SdJwtArrayBuilder(mutableListOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).arr(content.elements)
}

/**
 * A builder for creating an [SdJwtObject] using a fluent DSL.
 *
 * Provides methods to add regular (non-selectively disclosable) and
 * selectively disclosable (SD) claims for named attributes, including nested
 * objects and arrays.
 *
 * @property elements The map of [String] keys to [SdJwtElement]s built by this builder.
 */
@DisclosableElementDsl
class SdJwtObjectBuilder(elements: MutableMap<String, SdJwtElement>) {

    private val claims = DisclosableObjectSpecBuilder(factory(null), elements)
    val elements: Map<String, SdJwtElement> get() = claims.elements

    /**
     * Adds a regular (non-selectively disclosable) JSON element claim to the object.
     * The claim's value will always be revealed.
     * @param name The name of the claim.
     * @param value The [JsonElement] value of the claim.
     */
    fun claim(name: String, value: JsonElement): Unit = claims.claim(name, value)

    /**
     * Adds a regular (non-selectively disclosable) JSON element claim to the object.
     * The claim's value will always be revealed.
     * @param name The name of the claim.
     * @param value The value of the claim.
     * @param V the type of the value
     */
    fun<V> claim(name: String, value: V, serializer: KSerializer<V>): Unit = claim(name, Json.encodeToJsonElement(serializer, value))

    /**
     * Adds a regular (non-selectively disclosable) JSON element claim to the object.
     * The claim's value will always be revealed.
     * @param name The name of the claim.
     * @param value The value of the claim.
     * @param V the type of the value
     */
    inline fun<reified V> claim(name: String, value: V): Unit = claim(name, value, serializer())

    /**
     * Adds a regular (non-selectively disclosable) string claim to the object.
     * @param name The name of the claim.
     * @param value The [String] value of the claim.
     */
    fun claim(name: String, value: String): Unit = claim(name, JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) number claim to the object.
     * @param name The name of the claim.
     * @param value The [Number] value of the claim.
     */
    fun claim(name: String, value: Number): Unit = claim(name, JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) boolean claim to the object.
     * @param name The name of the claim.
     * @param value The [Boolean] value of the claim.
     */
    fun claim(name: String, value: Boolean): Unit = claim(name, JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) JSON element claim to the object.
     * The claim's value will be hidden by default and can be selectively disclosed.
     * @param name The name of the claim.
     * @param value The [JsonElement] value of the claim.
     */
    fun sdClaim(name: String, value: JsonElement): Unit = claims.sdClaim(name, value)

    /**
     * Adds a selectively disclosable (SD) JSON element claim to the object.
     * The claim's value will be hidden by default and can be selectively disclosed.
     * @param name The name of the claim.
     * @param value The value of the claim.
     * @param serializer The serializer of the value
     * @param V the type of the value
     */
    fun<V> sdClaim(name: String, value: V, serializer: KSerializer<V>): Unit = sdClaim(name, Json.encodeToJsonElement(serializer, value))

    /**
     * Adds a selectively disclosable (SD) JSON element claim to the object.
     * The claim's value will be hidden by default and can be selectively disclosed.
     * @param name The name of the claim.
     * @param value The value of the claim.
     * @param V the type of the value
     */
    inline fun<reified V> sdClaim(name: String, value: V): Unit = sdClaim(name, value, serializer())

    /**
     * Adds a selectively disclosable (SD) string claim to the object.
     * @param name The name of the claim.
     * @param value The [String] value of the claim.
     */
    fun sdClaim(name: String, value: String): Unit = sdClaim(name, JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) number claim to the object.
     * @param name The name of the claim.
     * @param value The [Number] value of the claim.
     */
    fun sdClaim(name: String, value: Number): Unit = sdClaim(name, JsonPrimitive(value))

    /**
     * Adds a selectively disclosable (SD) boolean claim to the object.
     * @param name The name of the claim.
     * @param value The [Boolean] value of the claim.
     */
    fun sdClaim(name: String, value: Boolean): Unit = sdClaim(name, JsonPrimitive(value))

    /**
     * Adds a regular (non-selectively disclosable) nested object claim to the current object.
     * The nested object's content will always be revealed.
     * @param name The name of the nested object claim.
     * @param minimumDigests An optional hint for decoy digests within this nested object.
     * @param action A lambda with [SdJwtObjectBuilder] as its receiver to define the nested object's content.
     */
    fun objClaim(
        name: String,
        minimumDigests: Int? = null,
        action: (SdJwtObjectBuilder).() -> Unit,
    ): Unit =
        claims.objClaim(name, buildSdJwtObject(minimumDigests, action))

    /**
     * Adds a selectively disclosable (SD) nested object claim to the current object.
     * The nested object's content will be hidden by default and can be selectively disclosed.
     * @param name The name of the nested object claim.
     * @param minimumDigests An optional hint for decoy digests within this nested object.
     * @param action A lambda with [SdJwtObjectBuilder] as its receiver to define the nested object's content.
     */
    fun sdObjClaim(
        name: String,
        minimumDigests: Int? = null,
        action: (SdJwtObjectBuilder).() -> Unit,
    ): Unit =
        claims.sdObjClaim(name, buildSdJwtObject(minimumDigests, action))

    /**
     * Adds a regular (non-selectively disclosable) nested array claim to the current object.
     * The nested array's content will always be revealed.
     * @param name The name of the nested array claim.
     * @param minimumDigests An optional hint for decoy digests within this nested array.
     * @param action A lambda with [SdJwtArrayBuilder] as its receiver to define the nested array's content.
     */
    fun arrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: SdJwtArrayBuilder.() -> Unit,
    ): Unit =
        claims.arrClaim(name, buildSdJwtArray(minimumDigests, action))

    /**
     * Adds a selectively disclosable (SD) nested array claim to the current object.
     * The nested array's content will be hidden by default and can be selectively disclosed.
     * @param name The name of the nested array claim.
     * @param minimumDigests An optional hint for decoy digests within this nested array.
     * @param action A lambda with [SdJwtArrayBuilder] as its receiver to define the nested array's content.
     */
    fun sdArrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: SdJwtArrayBuilder.() -> Unit,
    ): Unit =
        claims.sdArrClaim(name, buildSdJwtArray(minimumDigests, action))
}

/**
 * Builds an [SdJwtObject] using a fluent DSL.
 *
 * This top-level function provides a convenient way to define the content
 * of an SD-JWT object, including its named claims, disclosable properties,
 * and nested structures.
 *
 * @param minimumDigests An optional hint for the minimum number of decoy digests
 * that should be generated for the claims at this object level.
 * @param builderAction A lambda with [SdJwtObjectBuilder] as its receiver,
 * used to define the object's claims.
 * @return An [SdJwtObject] representing the defined object structure.
 */
inline fun buildSdJwtObject(
    minimumDigests: Int? = null,
    builderAction: SdJwtObjectBuilder.() -> Unit,
): SdJwtObject {
    val builder = SdJwtObjectBuilder(mutableMapOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).obj(content.elements)
}

/**
 * A convenience top-level function to start building an [SdJwtObject] with a more
 * domain-specific name. It is equivalent to [buildSdJwtObject].
 *
 * @param minimumDigests An optional hint for the minimum number of decoy digests
 * that should be generated for the claims at this object level.
 * @param builderAction A lambda with [SdJwtObjectBuilder] as its receiver,
 * used to define the object's claims.
 * @return An [SdJwtObject] representing the defined object structure.
 */
inline fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: SdJwtObjectBuilder.() -> Unit,
): SdJwtObject = buildSdJwtObject(minimumDigests, builderAction)
