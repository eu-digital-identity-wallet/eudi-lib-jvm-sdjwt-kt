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
package eu.europa.ec.eudi.sdjwt.dsl.sdjwt

import eu.europa.ec.eudi.sdjwt.MinimumDigests
import eu.europa.ec.eudi.sdjwt.atLeastDigests
import eu.europa.ec.eudi.sdjwt.dsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class SdJwtObject(
    override val content: Map<String, SdJwtElement>,
    val minimumDigests: MinimumDigests?,
) : DisclosableObject<String, JsonElement>
data class SdJwtArray(
    override val content: List<SdJwtElement>,
    val minimumDigests: MinimumDigests?,
) : DisclosableArray<String, JsonElement>

typealias SdJwtElement = DisclosableElement<String, JsonElement>

private fun factory(
    minimumDigests: Int?,
) = object : DisclosableContainerFactory<String, JsonElement, SdJwtObject, SdJwtArray> {

    override fun obj(elements: Map<String, DisclosableElement<String, JsonElement>>): SdJwtObject =
        SdJwtObject(elements, minimumDigests.atLeastDigests())

    override fun arr(elements: List<DisclosableElement<String, JsonElement>>): SdJwtArray =
        SdJwtArray(elements, minimumDigests.atLeastDigests())
}
class SdJwtArrayBuilder(
    elements: MutableList<SdJwtElement>,
) {

    private val claims = DisclosableArraySpecBuilder(factory = factory(null), elements)

    val elements: List<SdJwtElement>
        get() = claims.elements

    fun claim(value: JsonElement): Unit = claims.claim(value)
    fun claim(value: String): Unit = claims.claim(JsonPrimitive(value))
    fun claim(value: Number): Unit = claims.claim(JsonPrimitive(value))
    fun claim(value: Boolean): Unit = claims.claim(JsonPrimitive(value))
    fun sdClaim(value: JsonElement): Unit = claims.sdClaim(value)
    fun sdClaim(value: String): Unit = claims.sdClaim(JsonPrimitive(value))
    fun sdClaim(value: Number): Unit = claims.sdClaim(JsonPrimitive(value))
    fun sdClaim(value: Boolean): Unit = claims.sdClaim(JsonPrimitive(value))

    fun objClaim(minimumDigests: Int? = null, action: SdJwtObjectBuilder.() -> Unit): Unit =
        claims.objClaim(buildSdJwtObject(minimumDigests, action))

    fun sdObjClaim(minimumDigests: Int? = null, action: SdJwtObjectBuilder.() -> Unit): Unit =
        claims.sdObjClaim(buildSdJwtObject(minimumDigests, action))

    fun arrClaim(minimumDigests: Int? = null, action: SdJwtArrayBuilder.() -> Unit): Unit =
        claims.arrClaim(buildSdJwtArray(minimumDigests, action))

    fun sdArrClaim(minimumDigests: Int? = null, action: SdJwtArrayBuilder.() -> Unit): Unit =
        claims.sdArrClaim(buildSdJwtArray(minimumDigests, action))
}

fun buildSdJwtArray(
    minimumDigests: Int? = null,
    builderAction: SdJwtArrayBuilder.() -> Unit,
): SdJwtArray {
    val builder = SdJwtArrayBuilder(mutableListOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).arr(content.elements)
}

@DisclosableElementDsl
class SdJwtObjectBuilder(
    elements: MutableMap<String, SdJwtElement>,
) {
    private val claims = DisclosableObjectSpecBuilder(factory(null), elements)
    val elements: Map<String, SdJwtElement>
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
        action: (SdJwtObjectBuilder).() -> Unit,
    ): Unit =
        claims.objClaim(name, buildSdJwtObject(minimumDigests, action))

    fun sdObjClaim(
        name: String,
        minimumDigests: Int? = null,
        action: (SdJwtObjectBuilder).() -> Unit,
    ): Unit =
        claims.sdObjClaim(name, buildSdJwtObject(minimumDigests, action))

    fun arrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: SdJwtArrayBuilder.() -> Unit,
    ): Unit =
        claims.arrClaim(name, buildSdJwtArray(minimumDigests, action))

    fun sdArrClaim(
        name: String,
        minimumDigests: Int? = null,
        action: SdJwtArrayBuilder.() -> Unit,
    ): Unit =
        claims.sdArrClaim(name, buildSdJwtArray(minimumDigests, action))
}

fun buildSdJwtObject(
    minimumDigests: Int? = null,
    builderAction: SdJwtObjectBuilder.() -> Unit,
): SdJwtObject {
    val builder = SdJwtObjectBuilder(mutableMapOf())
    val content = builder.apply(builderAction)
    return factory(minimumDigests).obj(content.elements)
}

fun sdJwt(
    minimumDigests: Int? = null,
    builderAction: SdJwtObjectBuilder.() -> Unit,
): SdJwtObject = buildSdJwtObject(minimumDigests, builderAction)
