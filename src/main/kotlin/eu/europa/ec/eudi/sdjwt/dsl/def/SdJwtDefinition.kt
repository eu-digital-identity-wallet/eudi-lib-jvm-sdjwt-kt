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
package eu.europa.ec.eudi.sdjwt.dsl.def

import eu.europa.ec.eudi.sdjwt.RFC7519
import eu.europa.ec.eudi.sdjwt.RFC7800
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.TokenStatusListSpec
import eu.europa.ec.eudi.sdjwt.dsl.not
import eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableElement
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
    override val content: Map<String, SdJwtElementDefinition>,
    val metadata: VctMetadata,
) : DisclosableDefObject<String, AttributeMetadata> {

    /**
     * Returns a new [SdJwtDefinition] with updates to ensure that specific claims are marked
     * as "Never Selectively Disclosable". The claims targeted for this update are predefined
     * by the SD-JWT-VC specification [`iss`, `nbf`, `exp`, `cnf`, `vct`, `vct_integrity`, `status`]
     *
     * @return A new instance of [SdJwtDefinition] where the targeted claims have been updated
     *         to be "Never Selectively Disclosable".
     */
    fun plusSdJwtVcNeverSelectivelyDisclosableClaims(): SdJwtDefinition {
        val newContents = content.toMutableMap()
        SdJwtVcNeverSelectivelyDisclosableClaims.forEach { claim ->
            val definition = newContents[claim]?.value ?: DisclosableDef.Id(AttributeMetadata())
            newContents[claim] = !definition
        }
        return SdJwtDefinition(newContents, metadata)
    }

    companion object {
        private val SdJwtVcNeverSelectivelyDisclosableClaims: Set<String>
            get() = setOf(
                RFC7519.ISSUER,
                RFC7519.NOT_BEFORE,
                RFC7519.EXPIRATION_TIME,
                RFC7800.CNF,
                SdJwtVcSpec.VCT,
                SdJwtVcSpec.VCT_INTEGRITY,
                TokenStatusListSpec.STATUS,
            )
    }
}

/**
 * Describes the attributes of a map-like data structure (like a credential)
 * and especially their disclosure properties.
 * In addition, it contains display information for the container
 */
data class SdJwtObjectDefinition(
    override val content: Map<String, SdJwtElementDefinition>,
    val metadata: AttributeMetadata,
) : DisclosableDefObject<String, AttributeMetadata>

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
    override val content: SdJwtElementDefinition,
    val metadata: AttributeMetadata,
) : DisclosableDefArray<String, AttributeMetadata>

typealias SdJwtElementDefinition = DisclosableElementDefinition<String, AttributeMetadata>

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
