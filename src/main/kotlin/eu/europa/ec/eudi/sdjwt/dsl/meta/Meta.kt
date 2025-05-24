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

data class DisclosableObjectMetadata(
    override val content: Map<String, DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata,
) : DisclosableObject<String, AttributeMetadata>

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
    DisclosableContainerFactory<String, AttributeMetadata> {
        override fun obj(
            elements: Map<String, DisclosableElement<String, AttributeMetadata>>,
        ): DisclosableObject<String, AttributeMetadata> {
            return DisclosableObjectMetadata(elements, metadata)
        }

        override fun arr(
            elements: List<DisclosableElement<String, AttributeMetadata>>,
        ): DisclosableArray<String, AttributeMetadata> {
            return DisclosableArrayMetadata(elements, metadata)
        }
    }
