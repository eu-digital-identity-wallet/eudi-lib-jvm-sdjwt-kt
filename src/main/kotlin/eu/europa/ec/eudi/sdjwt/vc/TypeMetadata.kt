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
package eu.europa.ec.eudi.sdjwt.vc

import com.nimbusds.jose.shaded.gson.annotations.SerializedName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import java.net.URI

@Serializable
data class SdJwtVcTypeMetadata(
    /**
     * A human-readable name for the type, intended for developers reading the JSON document
     */
    @SerialName(SdJwtVcSpec.NAME) val name: String? = null,
    /**
     * A human-readable description for the type, intended for developers reading the JSON document
     */
    @SerialName(SdJwtVcSpec.DESCRIPTION) val description: String? = null,

    /**
     * A URI of another type that this type extends
     */
    @Serializable(with = URISerializer::class) @SerialName(SdJwtVcSpec.EXTENDS) val extends: URI? = null,

    @SerialName(SdJwtVcSpec.EXTENDS_INTEGRITY) val extendsIntegrity: DocumentIntegrity? = null,

    /**
     * A list containing display information for the type.
     * It MUST contain an object for each language that is supported by the type.
     */
    @SerialName(SdJwtVcSpec.DISPLAY) val display: Display? = null,

    /**
     * An embedded JSON Schema document describing the structure of the Verifiable Credential
     * MUST NOT be used if schema_uri is present
     */
    @SerialName(SdJwtVcSpec.SCHEMA) val schema: JsonSchema? = null,

    /**
     * A URL pointing to a JSON Schema document describing the structure of the Verifiable Credential
     * MUST NOT be used if schema is present
     */
    @Serializable(with = URISerializer::class)
    @SerialName(SdJwtVcSpec.SCHEMA_URI) val schemaUri: URI? = null,

    @SerialName(SdJwtVcSpec.SCHEMA_URI_INTEGRITY) val schemaUriIntegrity: DocumentIntegrity? = null,

) {
    init {
        if (schema != null) {
            require(schemaUri == null)
        }
        ensureIntegrityIsNotPresent(SdJwtVcSpec.EXTENDS, extends, extendsIntegrity)
        ensureIntegrityIsNotPresent(SdJwtVcSpec.SCHEMA_URI, schemaUri, schemaUriIntegrity)
    }
}

private fun ensureIntegrityIsNotPresent(
    attributeName: String,
    attributeValue: Any?,
    integrityValue: DocumentIntegrity?,
) {
    if (attributeValue == null) {
        require(integrityValue == null) {
            "`$attributeName#integrity` must not be provided, if `$attributeName` is not present"
        }
    }
}

@Serializable
@JvmInline
value class JsonSchema(val value: JsonObject)

@Serializable
@JvmInline
value class Display(val value: List<DisplayMetadata>) {
    init {
        value.requireUniqueLang()
    }

    override fun toString(): String = value.toString()

    companion object {
        fun List<DisplayMetadata>.requireUniqueLang() {
            val uniqueLangEntries = map { it.lang }.toSet().count()
            require(size == uniqueLangEntries) {
                "The list display must contain a single item per language"
            }
        }
    }
}

@Serializable
data class DisplayMetadata(

    // TODO Check https://www.rfc-editor.org/info/rfc5646
    /**
     * A language tag
     */
    @SerialName(SdJwtVcSpec.LANG) @Required val lang: String,

    /**
     * A human-readable name for the type, intended for end users
     */
    @SerialName(SdJwtVcSpec.NAME) @Required val name: String,

    /**
     * A human-readable description for the type, intended for end users.
     */
    @SerialName(SdJwtVcSpec.DESCRIPTION) val description: String? = null,

    /**
     * Rendering information for the type
     */
    @SerialName(SdJwtVcSpec.RENDERING) val rendering: RenderingMetadata? = null,
)

@Serializable
data class RenderingMetadata(
    @SerialName(SdJwtVcSpec.SIMPLE) val simple: SimpleRenderingMethod? = null,

    // TODO Check this
    //  No clear description
    @SerialName(SdJwtVcSpec.SVG_TEMPLATE) val svgTemplate: SvgTemplate? = null,
)

/**
 * The simple rendering method is intended for use in applications that do not support SVG rendering
 */
@Serializable
data class SimpleRenderingMethod(
    /**
     * An object containing information about the logo to be displayed for the type
     */
    @SerialName(SdJwtVcSpec.LOGO) val logo: LogoMetadata? = null,
    @SerialName(SdJwtVcSpec.BACKGROUND_COLOR) val backgroundColor: CssColor? = null,
    @SerialName(SdJwtVcSpec.TEXT_COLOR) val textColor: CssColor? = null,
)

@Serializable
data class SvgTemplate(
    /**
     * A URI pointing to the SVG template
     */
    @Serializable(with = URISerializer::class)
    @SerialName(SdJwtVcSpec.SVG_URI)
    @Required val uri: URI,

    @SerialName(SdJwtVcSpec.SVG_URI_INTEGRITY) val uriIntegrity: DocumentIntegrity? = null,

    // TODO We need to check this with the spec
    //  There is no clear description
    @SerialName(SdJwtVcSpec.SVG_PROPERTIES) val properties: SvgTemplateProperties? = null,
) {
    init {
        ensureIntegrityIsNotPresent(SdJwtVcSpec.SVG_URI, uri, uriIntegrity)
    }
}

@Serializable
enum class SvgOrientation {
    @SerializedName(SdJwtVcSpec.SVG_ORIENTATION_PORTRAIT)
    Portrait,

    @SerializedName(SdJwtVcSpec.SVG_ORIENTATION_LANDSCAPE)
    Landscape,
}

@Serializable
enum class SvgColorScheme {
    @SerialName(SdJwtVcSpec.SVG_COLOR_SCHEME_LIGHT)
    Light,

    @SerialName(SdJwtVcSpec.SVG_COLOR_SCHEME_DARK)
    Dark,
}

@Serializable
enum class SvgContrast {
    @SerialName(SdJwtVcSpec.SVG_CONTRAST_NORMAL)
    Normal,

    @SerialName(SdJwtVcSpec.SVG_CONTRAST_HIGH)
    High,
}

/**
 * Properties for the [SVG template][SvgTemplate]
 */
@Serializable
data class SvgTemplateProperties(
    /**
     * The orientation for which the SVG template is optimized
     */
    @SerialName(SdJwtVcSpec.SVG_ORIENTATION) val orientation: SvgOrientation? = null,

    /**
     * The color scheme for which the SVG template is optimized
     */
    @SerialName(SdJwtVcSpec.SVG_COLOR_SCHEME) val colorScheme: SvgColorScheme? = null,

    /**
     * The contrast for which the SVG template is optimized
     */
    @SerialName(SdJwtVcSpec.SVG_CONTRAST) val contrast: SvgContrast? = null,
) {
    init {
        require(orientation != null || colorScheme != null || contrast != null) {
            "At least one of `orientation`, `color_scheme`, `contrast` is required"
        }
    }
}

@Serializable
data class LogoMetadata(
    /**
     * A URI pointing to the logo image
     */
    @Serializable(with = URISerializer::class)
    @SerialName(SdJwtVcSpec.LOGO_URI)
    @Required val uri: URI,

    @SerialName(SdJwtVcSpec.LOGO_URI_INTEGRITY) val uriIntegrity: DocumentIntegrity? = null,
    /**
     * A string containing alternative text for the logo image
     */
    @SerialName(SdJwtVcSpec.LOGO_ALT_TEXT) val altText: String? = null,
) {
    init {
        ensureIntegrityIsNotPresent(SdJwtVcSpec.LOGO_URI, uri, uriIntegrity)
    }
}

// TODO Check this
@Serializable
@JvmInline
value class DocumentIntegrity(val value: String)

/**
 * An RGB color value as defined in W3C.CSS-COLOR
 */
@Serializable
@JvmInline
value class CssColor(val value: String) {
    init {
        require(value.isNotBlank()) { "Color must not be blank" }
    }

    override fun toString() = value
}

object URISerializer : KSerializer<URI> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URI", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: URI) {
        encoder.encodeString(value.toASCIIString())
    }

    override fun deserialize(decoder: Decoder): URI {
        return decoder.decodeString().let { rawString -> URI.create(rawString) }
    }
}
