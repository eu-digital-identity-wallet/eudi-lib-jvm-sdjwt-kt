package eu.europa.ec.eudi.sdjwt.dsl.meta

import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.vc.ClaimDisplay
import eu.europa.ec.eudi.sdjwt.vc.SvgId

data class DisclosableObjectMetadata(
    override val content: Map<String, DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata
) : DisclosableObject<String, AttributeMetadata>

data class DisclosableArrayMetadata(
    override val content: List<DisclosableElement<String, AttributeMetadata>>,
    val metadata: AttributeMetadata
) : DisclosableArray<String, AttributeMetadata>

typealias DisclosableElementMetadata = Disclosable<DisclosableValue<String, AttributeMetadata>>

data class AttributeMetadata(
    val display: List<ClaimDisplay>? = null,
    val svgId: SvgId? = null,
)


class DisclosableContainerMetadataFactory(private val metadata: AttributeMetadata)
    : DisclosableContainerFactory<String, AttributeMetadata> {
    override fun obj(elements: Map<String, DisclosableElement<String, AttributeMetadata>>): DisclosableObject<String, AttributeMetadata> {
       return DisclosableObjectMetadata(elements, metadata )
    }

    override fun arr(elements: List<DisclosableElement<String, AttributeMetadata>>): DisclosableArray<String, AttributeMetadata> {
        return DisclosableArrayMetadata(elements, metadata )
    }
}








