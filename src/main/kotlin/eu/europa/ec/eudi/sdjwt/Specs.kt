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
package eu.europa.ec.eudi.sdjwt

/**
 * [Selective Disclosure for JWTs (SD-JWT)](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/)
 */
@Suppress("UNUSED")
object SdJwtSpec {
    /**
     * Digests of Disclosures for object properties
     */
    const val CLAIM_SD: String = "_sd"

    /**
     *  Hash algorithm used to generate Disclosure digests and digest over presentation
     */
    const val CLAIM_SD_ALG: String = "_sd_alg"

    /**
     * Digest of the SD-JWT to which the KB-JWT is tied
     */
    const val CLAIM_SD_HASH: String = "sd_hash"

    /**
     * Digest of the Disclosure for an array element
     */
    const val CLAIM_ARRAY_ELEMENT_DIGEST: String = "..."

    //
    //  Header parameters, for JWS JSON
    //
    /**
     * An array of strings where each element is an individual Disclosure
     */
    const val JWS_JSON_DISCLOSURES = "disclosures"

    /**
     * Present only in an SD-JWT+KB, the Key Binding JWT
     */
    const val JWS_JSON_KB_JWT = "kb_jwt"

    //
    // Other
    //
    const val DISCLOSURE_SEPARATOR: Char = '~'
    const val DEFAULT_SD_ALG = "sha-256"

    //
    // Media types
    //

    const val MEDIA_SUBTYPE_SD_JWT: String = "sd-jwt"

    /**
     * To indicate that the content is an SD-JWT
     */
    const val MEDIA_TYPE_APPLICATION_SD_JWT: String = "application/$MEDIA_SUBTYPE_SD_JWT"

    const val MEDIA_SUBTYPE_SD_JWT_JSON: String = "sd-jwt+json"

    /**
     * To indicate that the content is a JWS JSON serialized SD-JWT
     */
    const val MEDIA_TYPE_APPLICATION_SD_JWT_JSON: String = "application/$MEDIA_SUBTYPE_SD_JWT_JSON"

    const val MEDIA_SUBTYPE_KB_JWT: String = "kb+jwt"

    /**
     * To indicate that the content is a Key Binding JWT
     */
    const val MEDIA_TYPE_APPLICATION_KB_JWT_JSON: String = "application/$MEDIA_SUBTYPE_KB_JWT"

    const val SUFFIX_SD_JWT: String = "+sd-jwt"
}

/**
 * [JSON Web Signature (JWS)](https://datatracker.ietf.org/doc/html/rfc7515)
 */
object RFC7515 {
    const val JWS_JSON_HEADER = "header"
    const val JWS_JSON_PROTECTED = "protected"
    const val JWS_JSON_SIGNATURE = "signature"
    const val JWS_JSON_SIGNATURES = "signatures"
    const val JWS_JSON_PAYLOAD = "payload"
}

/**
 * [SD-JWT-based Verifiable Credentials](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/06/)
 */
object SdJwtVcSpec {

    const val WELL_KNOWN_SUFFIX_JWT_VC_ISSUER: String = "jwt-vc-issuer"
    const val WELL_KNOWN_JWT_VC_ISSUER: String = "/.well-known/$WELL_KNOWN_SUFFIX_JWT_VC_ISSUER"

    @Deprecated(
        message = "Removed from SD-JWT-VC",
        replaceWith = ReplaceWith("SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT"),
        level = DeprecationLevel.WARNING,
    )
    const val MEDIA_SUBTYPE_VC_SD_JWT: String = "vc+sd-jwt"

    //
    // Media types
    //
    const val MEDIA_SUBTYPE_DC_SD_JWT: String = "dc+sd-jwt"

    /**
     * The Internet media type for an SD-JWT VC
     */
    const val MEDIA_TYPE_APPLICATION_DC_SD_JWT: String = "application/$MEDIA_SUBTYPE_DC_SD_JWT"

    //
    // Issuer metadata
    //
    const val ISSUER: String = "issuer"

    //
    // Type metadata
    //
    const val HASH_INTEGRITY: String = "#integrity"
    const val VCT: String = "vct"
    const val VCT_INTEGRITY: String = "$VCT$HASH_INTEGRITY"
    const val NAME: String = "name"
    const val DESCRIPTION: String = "description"
    const val EXTENDS: String = "extends"
    const val EXTENDS_INTEGRITY: String = "$EXTENDS$HASH_INTEGRITY"
    const val DISPLAY: String = "display"
    const val CLAIMS: String = "claims"
    const val SCHEMA: String = "schema"
    const val SCHEMA_URI: String = "schema_uri"
    const val SCHEMA_URI_INTEGRITY: String = "$SCHEMA_URI$HASH_INTEGRITY"
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
    const val LOGO_URI_INTEGRITY: String = "$LOGO_URI$HASH_INTEGRITY"
    const val LOGO_ALT_TEXT: String = "alt_text"
    const val BACKGROUND_COLOR: String = "background_color"
    const val TEXT_COLOR: String = "text_color"
    const val SVG_URI: String = "uri"
    const val SVG_URI_INTEGRITY: String = "$SVG_URI$HASH_INTEGRITY"
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

/**
 * [JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
 */
object RFC7519 {
    const val ISSUER: String = "iss"
    const val SUBJECT: String = "sub"
    const val AUDIENCE: String = "aud"
    const val EXPIRATION_TIME: String = "exp"
    const val NOT_BEFORE: String = "nbf"
    const val ISSUED_AT: String = "iat"
    const val JWT_ID: String = "jti"
}
