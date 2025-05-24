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
package eu.europa.ec.eudi.sdjwt.dsl.json

import eu.europa.ec.eudi.sdjwt.MinimumDigests
import eu.europa.ec.eudi.sdjwt.atLeastDigests
import eu.europa.ec.eudi.sdjwt.dsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

typealias JsonDisclosableElement = DisclosableElement<String, JsonElement>
data class JsonElementDisclosableObject(
    override val content: Map<String, DisclosableElement<String, JsonElement>>,
    val minimumDigests: MinimumDigests?,
) : DisclosableObject<String, JsonElement>
data class JsonElementDisclosableArray(
    override val content: List<DisclosableElement<String, JsonElement>>,
    val minimumDigests: MinimumDigests?,
) : DisclosableArray<String, JsonElement>

private fun factory(
    minimumDigests: Int?,
) = object : DisclosableContainerFactory<String, JsonElement, JsonElementDisclosableObject, JsonElementDisclosableArray> {

    override fun obj(elements: Map<String, DisclosableElement<String, JsonElement>>): JsonElementDisclosableObject =
        JsonElementDisclosableObject(elements, minimumDigests.atLeastDigests())

    override fun arr(elements: List<DisclosableElement<String, JsonElement>>): JsonElementDisclosableArray =
        JsonElementDisclosableArray(elements, minimumDigests.atLeastDigests())
}
class JsonElementDisclosableArraySpecBuilder(
    elements: MutableList<JsonDisclosableElement>,
) {

    private val claims = DisclosableArraySpecBuilder(factory = factory(null), elements)

    val elements: List<JsonDisclosableElement>
        get() = claims.elements

    fun claim(value: JsonElement): Unit = claims.claim(value)
    fun claim(value: String): Unit = claims.claim(JsonPrimitive(value))
    fun claim(value: Number): Unit = claims.claim(JsonPrimitive(value))
    fun claim(value: Boolean): Unit = claims.claim(JsonPrimitive(value))
    fun sdClaim(value: JsonElement): Unit = claims.sdClaim(value)
    fun sdClaim(value: String): Unit = claims.sdClaim(JsonPrimitive(value))
    fun sdClaim(value: Number): Unit = claims.sdClaim(JsonPrimitive(value))
    fun sdClaim(value: Boolean): Unit = claims.sdClaim(JsonPrimitive(value))

    fun objClaim(minimumDigests: Int? = null, action: JsonElementDisclosableObjectSpecBuilder.() -> Unit): Unit =
        claims.objClaim(buildJsonElementDisclosableObject(minimumDigests, action))

    fun sdObjClaim(minimumDigests: Int? = null, action: JsonElementDisclosableObjectSpecBuilder.() -> Unit): Unit =
        claims.sdObjClaim(buildJsonElementDisclosableObject(minimumDigests, action))

    fun arrClaim(minimumDigests: Int? = null, action: JsonElementDisclosableArraySpecBuilder.() -> Unit): Unit =
        claims.arrClaim(buildJsonElementDisclosableArray(minimumDigests, action))

    fun sdArrClaim(minimumDigests: Int? = null, action: JsonElementDisclosableArraySpecBuilder.() -> Unit): Unit =
        claims.sdArrClaim(buildJsonElementDisclosableArray(minimumDigests, action))
}

fun buildJsonElementDisclosableArray(
    minimumDigests: Int? = null,
    builderAction: JsonElementDisclosableArraySpecBuilder.() -> Unit,
): JsonElementDisclosableArray {
    val builder = JsonElementDisclosableArraySpecBuilder(mutableListOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).arr(content.elements)
}

@DisclosableElementDsl
class JsonElementDisclosableObjectSpecBuilder(
    elements: MutableMap<String, JsonDisclosableElement>,
) {
    private val claims = DisclosableObjectSpecBuilder(factory(null), elements)
    val elements: Map<String, JsonDisclosableElement>
        get() = claims.elements

    fun claim(name: String, value: JsonElement): Unit = claims.claim(name, value)
    fun claim(name: String, value: String): Unit = claim(name, JsonPrimitive(value))
    fun claim(name: String, value: Number): Unit = claim(name, JsonPrimitive(value))
    fun claim(name: String, value: Boolean): Unit = claim(name, JsonPrimitive(value))

    fun sdClaim(name: String, value: JsonElement): Unit = claims.sdClaim(name, value)
    fun sdClaim(name: String, value: String): Unit = sdClaim(name, JsonPrimitive(value))
    fun sdClaim(name: String, value: Number): Unit = sdClaim(name, JsonPrimitive(value))
    fun sdClaim(name: String, value: Boolean): Unit = sdClaim(name, JsonPrimitive(value))

    fun objClaim(
        name: String,
        minimumDigests: Int? = null,
        action: (JsonElementDisclosableObjectSpecBuilder).() -> Unit,
    ): Unit =
        claims.objClaim(name, buildJsonElementDisclosableObject(minimumDigests, action))

    fun sdObjClaim(
        name: String,
        minimumDigests: Int? = null,
        action: (JsonElementDisclosableObjectSpecBuilder).() -> Unit,
    ): Unit =
        claims.sdObjClaim(name, buildJsonElementDisclosableObject(minimumDigests, action))

    fun arrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: JsonElementDisclosableArraySpecBuilder.() -> Unit,
    ): Unit =
        claims.arrClaim(name, buildJsonElementDisclosableArray(minimumDigests, action))

    fun sdArrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: JsonElementDisclosableArraySpecBuilder.() -> Unit,
    ): Unit =
        claims.sdArrClaim(name, buildJsonElementDisclosableArray(minimumDigests, action))
}

fun buildJsonElementDisclosableObject(
    minimumDigests: Int? = null,
    builderAction: JsonElementDisclosableObjectSpecBuilder.() -> Unit,
): JsonElementDisclosableObject {
    val builder = JsonElementDisclosableObjectSpecBuilder(mutableMapOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).obj(content.elements)
}

fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: JsonElementDisclosableObjectSpecBuilder.() -> Unit,
): JsonElementDisclosableObject = buildJsonElementDisclosableObject(minimumDigests, builderAction)
