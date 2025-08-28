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

import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.vc.ClaimMetadata.Companion.DefaultSelectivelyDisclosable
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SdJwtVcTypeMetadata(

    @SerialName(SdJwtVcSpec.VCT) @Required val vct: Vct,

    @SerialName(SdJwtVcSpec.VCT_INTEGRITY) val vctIntegrity: DocumentIntegrities? = null,

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
    @SerialName(SdJwtVcSpec.EXTENDS) val extends: String? = null,

    @SerialName(SdJwtVcSpec.EXTENDS_INTEGRITY) val extendsIntegrity: DocumentIntegrities? = null,

    /**
     * A list containing display information for the type.
     * It MUST contain an object for each language that is supported by the type.
     */
    @SerialName(SdJwtVcSpec.DISPLAY) val display: Display? = null,

    /**
     *  List containing claim information for the type
     */
    @SerialName(SdJwtVcSpec.CLAIMS) val claims: List<ClaimMetadata>? = null,

    /**
     * An embedded JSON Schema document describing the structure of the Verifiable Credential
     * MUST NOT be used if schema_uri is present
     */
    @SerialName(SdJwtVcSpec.SCHEMA) val schema: JsonSchema? = null,

    /**
     * A URL pointing to a JSON Schema document describing the structure of the Verifiable Credential
     * MUST NOT be used if schema is present
     */
    @SerialName(SdJwtVcSpec.SCHEMA_URI) val schemaUri: String? = null,

    @SerialName(SdJwtVcSpec.SCHEMA_URI_INTEGRITY) val schemaUriIntegrity: DocumentIntegrities? = null,

) {
    init {
        if (schema != null) {
            require(schemaUri == null)
        }
        ensureIntegrityIsNotPresent(SdJwtVcSpec.VCT, vct, vctIntegrity)
        ensureIntegrityIsNotPresent(SdJwtVcSpec.EXTENDS, extends, extendsIntegrity)
        ensureIntegrityIsNotPresent(SdJwtVcSpec.SCHEMA_URI, schemaUri, schemaUriIntegrity)
        ensureValidPaths(claims.orEmpty())
    }

    companion object {
        fun ensureValidPaths(claims: List<ClaimMetadata>) {
            val paths = claims.map { it.path }
            ClaimPath.ensureObjectAttributes(paths)
            ensureNoIndexedPaths(paths)
        }

        private fun ensureNoIndexedPaths(paths: List<ClaimPath>) {
            val indexedPaths = paths.filter { path -> path.value.any { it is ClaimPathElement.ArrayElement } }
            require(indexedPaths.isEmpty()) {
                "Indexed paths are not allowed. Found: $indexedPaths"
            }
        }
    }
}

@Serializable
@JvmInline
value class Vct(val value: String) {
    init {
        require(value.isNotBlank()) { "Vct value must not be blank" }
    }

    override fun toString(): String = value
}
private fun ensureIntegrityIsNotPresent(
    attributeName: String,
    attributeValue: Any?,
    integrityValue: DocumentIntegrities?,
) {
    if (attributeValue == null) {
        require(integrityValue == null) {
            "`$attributeName${SdJwtVcSpec.HASH_INTEGRITY}` must not be provided, if `$attributeName` is not present"
        }
    }
}

@Serializable
@JvmInline
value class JsonSchema(val value: JsonObject)

@Serializable
data class ClaimMetadata(

    /**
     * The claim or claims that are being addressed
     */
    @SerialName(SdJwtVcSpec.CLAIM_PATH) @Required val path: ClaimPath,

    /**
     * display information for the claim
     */
    @SerialName(SdJwtVcSpec.CLAIM_DISPLAY) val display: List<ClaimDisplay>? = null,

    /**
     *  Indicates whether the claim is selectively disclosable.
     *  If omitted, the default value is [DefaultSelectivelyDisclosable]
     */
    @SerialName(SdJwtVcSpec.CLAIM_SD) val selectivelyDisclosable: ClaimSelectivelyDisclosable? = DefaultSelectivelyDisclosable,
    /**
     *The ID of the claim for reference in the SVG template
     */
    @SerialName(SdJwtVcSpec.CLAIM_SVG_ID) val svgId: SvgId? = null,
) {

    val selectivelyDisclosableOrDefault: ClaimSelectivelyDisclosable
        get() = selectivelyDisclosable ?: DefaultSelectivelyDisclosable

    companion object {
        /**
         * Default [ClaimSelectivelyDisclosable] value is [ClaimSelectivelyDisclosable.Allowed]
         */
        val DefaultSelectivelyDisclosable: ClaimSelectivelyDisclosable = ClaimSelectivelyDisclosable.Allowed
    }
}

@Serializable
data class ClaimDisplay(
    /**
     *  A language tag
     */
    @SerialName(SdJwtVcSpec.CLAIM_LANG) @Required val lang: LangTag,

    /**
     * A human-readable label for the claim, intended for end users
     */
    @SerialName(SdJwtVcSpec.CLAIM_LABEL) @Required val label: String,

    /**
     * A human-readable description for the claim, intended for end users
     */
    @SerialName(SdJwtVcSpec.CLAIM_DESCRIPTION) val description: String? = null,
)

// TODO Check https://www.rfc-editor.org/info/rfc5646
@Serializable
@JvmInline
value class LangTag(val value: String) {
    init {
        require(value.isNotBlank()) { "Lang tag cannot be blank" }
    }

    override fun toString(): String = value
}

@Suppress("UNUSED")
@Serializable
enum class ClaimSelectivelyDisclosable {
    /**
     *  The Issuer MUST make the claim selectively disclosable
     */
    @SerialName(SdJwtVcSpec.CLAIM_SD_ALWAYS)
    Always,

    /**
     * The Issuer MAY make the claim selectively disclosable
     */
    @SerialName(SdJwtVcSpec.CLAIM_SD_ALLOWED)
    Allowed,

    /**
     * The Issuer MUST NOT make the claim selectively disclosable
     */
    @SerialName(SdJwtVcSpec.CLAIM_SD_NEVER)
    Never,
}

/**
 * It MUST consist of only alphanumeric characters and underscores and MUST NOT start with a digit
 */
@Serializable
@JvmInline
value class SvgId(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "SvgId cannot be empty"
        }
        require(value.all { it.isLetterOrDigit() || it == '_' }) {
            "SvgId must consist of only alphanumeric characters and underscores"
        }
        require(!value[0].isDigit()) {
            "SvgId must not start with a digit"
        }
    }

    override fun toString() = value
}

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

    /**
     * A language tag
     */
    @SerialName(SdJwtVcSpec.LANG) @Required val lang: LangTag,

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
    @SerialName(SdJwtVcSpec.SVG_TEMPLATES) val svgTemplates: List<SvgTemplate>? = null,
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
    /**
     * An RGB color value
     */
    @SerialName(SdJwtVcSpec.BACKGROUND_COLOR) val backgroundColor: CssColor? = null,
    @SerialName(SdJwtVcSpec.TEXT_COLOR) val textColor: CssColor? = null,
)

@Serializable
data class SvgTemplate(
    /**
     * A URI pointing to the SVG template
     */
    @SerialName(SdJwtVcSpec.SVG_URI)
    @Required val uri: String,

    @SerialName(SdJwtVcSpec.SVG_URI_INTEGRITY) val uriIntegrity: DocumentIntegrities? = null,

    @SerialName(SdJwtVcSpec.SVG_PROPERTIES) val properties: SvgTemplateProperties? = null,
) {
    init {
        ensureIntegrityIsNotPresent(SdJwtVcSpec.SVG_URI, uri, uriIntegrity)
    }
}

@Suppress("UNUSED")
@Serializable
enum class SvgOrientation {
    @SerialName(SdJwtVcSpec.SVG_ORIENTATION_PORTRAIT)
    Portrait,

    @SerialName(SdJwtVcSpec.SVG_ORIENTATION_LANDSCAPE)
    Landscape,
}

@Suppress("UNUSED")
@Serializable
enum class SvgColorScheme {
    @SerialName(SdJwtVcSpec.SVG_COLOR_SCHEME_LIGHT)
    Light,

    @SerialName(SdJwtVcSpec.SVG_COLOR_SCHEME_DARK)
    Dark,
}

@Suppress("UNUSED")
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
            val attributes = listOf(
                SdJwtVcSpec.SVG_ORIENTATION,
                SdJwtVcSpec.SVG_COLOR_SCHEME,
                SdJwtVcSpec.SVG_CONTRAST,
            )
            "At least one of $attributes is required"
        }
    }
}

@Serializable
data class LogoMetadata(
    /**
     * A URI pointing to the logo image
     */
    @SerialName(SdJwtVcSpec.LOGO_URI)
    @Required val uri: String,

    @SerialName(SdJwtVcSpec.LOGO_URI_INTEGRITY) val uriIntegrity: DocumentIntegrities? = null,

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
value class DocumentIntegrities(val value: String)

data class DocumentIntegrity(
    val hashAlgorithm: IntegrityAlgorithm,
    val value: String,
)

fun DocumentIntegrities.toDocumentIntegrity(): List<DocumentIntegrity> {
    val documentIntegrities = value.split("\\s+".toRegex())
    val integritiesValues = documentIntegrities.mapNotNull {
        val (hashExpression, _) = it.split("?", limit = 2)
        val (algorithm, integrity) = hashExpression.split("-", limit = 2)
        IntegrityAlgorithm.fromString(algorithm)?.let { metadataHashAlgorithm ->
            DocumentIntegrity(metadataHashAlgorithm, integrity)
        }
    }

    require(integritiesValues.isNotEmpty()) { "No supported integrity algorithm found" }
    return integritiesValues
}

enum class IntegrityAlgorithm(val alias: String) {
    SHA256("sha256"),
    SHA384("sha384"),
    SHA512("sha512"),
    ;

    companion object {
        fun fromString(s: String): IntegrityAlgorithm? = IntegrityAlgorithm.entries.find { it.alias == s }
    }
}

// TODO Check this
//  https://www.w3.org/TR/css-color-3/
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
