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
package eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def

import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.vc.*

/**
 * The definition of a SD-JWT-VC credential
 *
 * The SD-JWT-VC Type metadata, or credential configurations for SD-JWT-VC and MDoc
 *  as defined in OpenId4VCI, are fundamentally flat, suitable for serialization.
 *
 * On the other hand, [SdJwtDefinition] is hierarchical and can represent
 * accurately the disclosure and display properties of SD-JWT-VC or even JWT credentials.
 */
data class SdJwtDefinition(
    override val content: Map<String, DisclosableElement<String, AttributeMetadata>>,
    val metadata: VctMetadata,
) : DisclosableObject<String, AttributeMetadata> {
    companion object
}

/**
 * Describes the attributes of a map-like data structure (like a credential)
 * and especially their disclosure properties.
 * In addition, it contains display information for the container
 */
data class SdJwtObjectDefinition(
    override val content: Map<String, DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata,
) : DisclosableObject<String, AttributeMetadata>

/**
 * Describes the elements of an array-like data structure
 * and especially their disclosure properties.
 *
 * The [content] list defines the characteristics and disclosure properties
 *  of the elements that may appear within instances of this array type.
 *  If the list contains a single [DisclosableElement], it describes homogeneous array elements.
 *  If the list contains multiple [DisclosableElement]s, it implies a union of possible element types.
 *
 * In addition, contains display information][metadata] for the container
 */
data class SdJwtArrayDefinition(
    override val content: List<DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata,
) : DisclosableArray<String, AttributeMetadata>

typealias SdJwtElementDefinition = Disclosable<DisclosableValue<String, AttributeMetadata>>

data class VctMetadata(
    val vct: Vct,
    val name: String?,
    val description: String?,
    val display: List<DisplayMetadata>,
    val schemas: List<JsonSchema>,
)

data class AttributeMetadata(
    val display: List<ClaimDisplay>? = null,
    val svgId: SvgId? = null,
)
