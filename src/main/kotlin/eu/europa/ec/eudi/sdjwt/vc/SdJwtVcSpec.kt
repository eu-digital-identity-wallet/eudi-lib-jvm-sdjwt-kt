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

/**
 * [SD-JWT-based Verifiable Credentials](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/06/)
 */
object SdJwtVcSpec {
    const val VCT: String = "vct"
    const val VCT_INTEGRITY: String = "vct#integrity"
    const val STATUS: String = "status"

    const val ISSUER: String = "issuer"

    const val INTEGRITY: String = "#integrity"
    const val NAME: String = "name"
    const val DESCRIPTION: String = "description"
    const val EXTENDS: String = "extends"
    const val EXTENDS_INTEGRITY: String = "$EXTENDS$INTEGRITY"
    const val DISPLAY: String = "display"
    const val CLAIMS: String = "claims"
    const val SCHEMA: String = "schema"
    const val SCHEMA_URI: String = "schema_uri"
    const val SCHEMA_URI_INTEGRITY: String = "$SCHEMA_URI$INTEGRITY"

    const val CLAIM_PATH: String = "path"
    const val CLAIM_DISPLAY: String = "display"
    const val CLAIM_SD: String = "sd"
    const val CLAIM_SD_ALWAYS: String = "always"
    const val CLAIM_SD_ALLOWED: String = "allowed"
    const val CLAIM_SD_NEVER: String = "never"

    const val CLAIM_SVG_ID: String = "svg_id"
    const val CLAIM_LANG: String = "lang"
    const val CLAIM_LABEL = "label"
    const val CLAIM_DESCRIPTION = DESCRIPTION

    const val LANG: String = "lang"
    const val RENDERING: String = "rendering"

    const val SIMPLE: String = "simple"
    const val SVG_TEMPLATES: String = "svg_templates"

    const val LOGO: String = "logo"
    const val LOGO_URI: String = "uri"
    const val LOGO_URI_INTEGRITY: String = "$LOGO_URI$INTEGRITY"
    const val LOGO_ALT_TEXT: String = "alt_text"

    const val BACKGROUND_COLOR: String = "background_color"
    const val TEXT_COLOR: String = "text_color"

    const val SVG_URI: String = "uri"
    const val SVG_URI_INTEGRITY: String = "$SVG_URI$INTEGRITY"
    const val SVG_PROPERTIES: String = "properties"
    const val SVG_ORIENTATION: String = "orientation"
    const val SVG_ORIENTATION_PORTRAIT: String = "portrait"
    const val SVG_ORIENTATION_LANDSCAPE: String = "landscape"

    const val SVG_COLOR_SCHEME: String = "color_scheme"
    const val SVG_COLOR_SCHEME_LIGHT: String = "light"
    const val SVG_COLOR_SCHEME_DARK: String = "dark"

    const val SVG_CONTRAST: String = "contrast"
    const val SVG_CONTRAST_NORMAL: String = "normal"
    const val SVG_CONTRAST_HIGH: String = "high"
}
