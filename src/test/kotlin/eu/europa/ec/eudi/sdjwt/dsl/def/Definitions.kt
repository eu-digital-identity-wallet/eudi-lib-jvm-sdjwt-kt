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

import eu.europa.ec.eudi.sdjwt.vc.ResolvedTypeMetadata
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcTypeMetadata
import kotlinx.serialization.json.Json

internal fun SdJwtVcTypeMetadata.resolve(): ResolvedTypeMetadata {
    return ResolvedTypeMetadata(
        vct = vct,
        name = name,
        description = description,
        display = checkNotNull(display).value,
        claims = checkNotNull(claims),
    )
}

@Suppress("SameParameterValue")
fun sdJwtVcTypeMetadata(json: String): SdJwtVcTypeMetadata = Json.decodeFromString(json)

internal val addressMeta = """
    {
      "vct": "https://example.com/addresses",
      "name": "Addresses",
      "display": [
        {
          "locale": "en",
          "name": "Addresses"
        }
      ],
      "claims": [
        { "path": [ "addresses" ], "sd": "always" },
        { "path": [ "addresses", null ], "sd": "always" },
        { "path": [ "addresses", null, "house_number" ], "sd": "always" },
        { "path": [ "addresses", null, "street_address" ], "sd": "always" },
        { "path": [ "addresses", null, "locality" ], "sd": "always" },
        { "path": [ "addresses", null, "region" ], "sd": "always" },
        { "path": [ "addresses", null, "postal_code" ], "sd": "always" },
        { "path": [ "addresses", null, "country" ], "sd": "always" },
        { "path": [ "addresses", null, "formatted" ], "sd": "always" }
      ]
    }
""".trimIndent()

internal val pidMeta = """
    {
      "vct": "urn:eudi:pid:1",
      "name": "Type Metadata for Person Identification Data",
      "display": [
        {
          "locale": "en",
          "name": "PID",
          "description": "Person Identification Data"
        }
      ],
      "claims": [
        {
          "path": [
            "family_name"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Family Name(s)"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "given_name"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Given Name(s)"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "birthdate"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Birth Date"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "place_of_birth"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Birth Place"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "place_of_birth",
            "locality"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Locality"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "place_of_birth",
            "region"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Region"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "place_of_birth",
            "country"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Country"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "nationalities"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Nationality"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "nationalities",
            null
          ],
          "sd": "always"
        },
        {
          "path": [
            "address"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Address"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "house_number"
          ],
          "display": [
            {
              "locale": "en",
              "label": "House Number"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "street_address"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Street"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "locality"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Locality"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "region"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Region"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "postal_code"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Postal Code"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "country"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Country"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "address",
            "formatted"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Full Address"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "personal_administrative_number"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Personal Administrative Number"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "picture"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Portrait Image"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "birth_family_name"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Birth Family Name(s)"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "birth_given_name"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Birth Given Name(s)"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "sex"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Sex"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "email"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Email Address"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "phone_number"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Mobile Phone Number"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "date_of_expiry"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Expiry Date"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "issuing_authority"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Issuing Authority"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "issuing_country"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Issuing Country"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "document_number"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Document Number"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "issuing_jurisdiction"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Issuing Jurisdiction"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "date_of_issuance"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Issuance Date"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "age_equal_or_over"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Age Equal or Over"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "age_equal_or_over",
            "18"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Age Over 18"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "age_in_years"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Age in Years"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "age_birth_year"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Age Year of Birth"
            }
          ],
          "sd": "always"
        },
        {
          "path": [
            "trust_anchor"
          ],
          "display": [
            {
              "locale": "en",
              "label": "Trust Anchor"
            }
          ],
          "sd": "always"
        }
      ]
      
    }
""".trimIndent()

internal val countriesMeta = """
    {
      "vct": "https://example.com/countries",
      "name": "Countries",
      "display": [
        {
          "locale": "en",
          "name": "Countries"
        }
      ],
      "claims": [
        { "path": [ "countries" ], "sd": "always" },
        { "path": [ "countries", null ], "sd": "always" },
        { "path": [ "countries", null, null ], "sd": "always" }
      ]
    }
""".trimIndent()

internal val PidDefinition: SdJwtDefinition by lazy {
    val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(pidMeta)
    val resolvedTypeMetadata = sdJwtVcTypeMetadata.resolve()
    SdJwtDefinition.fromSdJwtVcMetadata(resolvedTypeMetadata)
}

internal val AddressDefinition: SdJwtDefinition by lazy {
    val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(addressMeta)
    val resolvedTypeMetadata = sdJwtVcTypeMetadata.resolve()
    SdJwtDefinition.fromSdJwtVcMetadata(resolvedTypeMetadata)
}

internal val CountriesDefinition: SdJwtDefinition by lazy {
    val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(countriesMeta)
    val resolvedTypeMetadata = sdJwtVcTypeMetadata.resolve()
    SdJwtDefinition.fromSdJwtVcMetadata(resolvedTypeMetadata)
}
