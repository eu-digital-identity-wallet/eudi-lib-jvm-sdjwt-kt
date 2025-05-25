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
package eu.europa.ec.eudi.sdjwt.dsl.meta

import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.vc.ClaimDisplay
import eu.europa.ec.eudi.sdjwt.vc.SvgId

/**
 * Describes the attributes of a map-like data structure (like a credential)
 * and especially their disclosure properties.
 * In addition, contains [display information][metadata] for the container
 *
 * The SD-JWT-VC Type metadata, or credential configurations for SD-JWT-VC and MDoc
 * as defined in OpenId4VCI, are fundamentally flat, suitable for serialization.
 *
 * On the other hand, [DisclosableObjectMetadata] is hierarchical and can represent
 * accurately the disclosure and display properties of SD-JWT-VC or even JWT credentials.
 */
data class DisclosableObjectMetadata(
    override val content: Map<String, DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata,
) : DisclosableObject<String, AttributeMetadata>

/**
 * Describes the elements of an array-like data structure
 * and especially their disclosure properties.
 * In addition, contains display information][metadata] for the container
 */
data class DisclosableArrayMetadata(
    override val content: List<DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata,
) : DisclosableArray<String, AttributeMetadata>

typealias DisclosableElementMetadata = Disclosable<DisclosableValue<String, AttributeMetadata>>

data class AttributeMetadata(
    val display: List<ClaimDisplay>? = null,
    val svgId: SvgId? = null,
)

class DisclosableContainerMetadataFactory(private val metadata: AttributeMetadata) :
    DisclosableContainerFactory<String, AttributeMetadata, DisclosableObjectMetadata, DisclosableArrayMetadata> {
        override fun obj(
            elements: Map<String, DisclosableElement<String, AttributeMetadata>>,
        ): DisclosableObjectMetadata = DisclosableObjectMetadata(elements, metadata)

        override fun arr(
            elements: List<DisclosableElement<String, AttributeMetadata>>,
        ): DisclosableArrayMetadata = DisclosableArrayMetadata(elements, metadata)
    }
