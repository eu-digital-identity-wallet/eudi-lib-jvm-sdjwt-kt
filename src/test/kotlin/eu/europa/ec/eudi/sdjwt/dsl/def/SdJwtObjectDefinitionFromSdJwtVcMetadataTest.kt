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

import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.AttributeMetadata
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.claimPaths
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.fromSdJwtVcMetadata
import eu.europa.ec.eudi.sdjwt.vc.ResolvedTypeMetadata
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcTypeMetadata
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SdJwtObjectDefinitionFromSdJwtVcMetadataTest {

    @Test
    fun test() {
        val pidSdJwtVcTypeMetadata = sdJwtVcTypeMetadata(pidMeta).resolve()
        val pidDefinition = SdJwtDefinition.fromSdJwtVcMetadata(pidSdJwtVcTypeMetadata)

        val vctMetadata = pidDefinition.metadata
        assertEquals(pidSdJwtVcTypeMetadata.vct, vctMetadata.vct)
        assertEquals(pidSdJwtVcTypeMetadata.name, vctMetadata.name)
        assertEquals(pidSdJwtVcTypeMetadata.description, vctMetadata.description)
        assertEquals(pidSdJwtVcTypeMetadata.display, vctMetadata.display)

        assertContentEquals(
            pidSdJwtVcTypeMetadata.claims.map { it.path },
            pidDefinition.claimPaths(),
        )

        val nationalities = pidDefinition.content["nationalities"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Arr<String, AttributeMetadata>>>(nationalities)
        assertEquals(1, nationalities.value.value.content.size)
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Id<String, AttributeMetadata>>>(nationalities.value.value.content.first())

        val address = pidDefinition.content["address"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Obj<String, AttributeMetadata>>>(address)
        listOf(
            "street_address",
            "locality",
            "region",
            "postal_code",
            "country",
            "formatted",
            "house_number",
        ).forEach { path ->
            val attribute = address.value.value.content[path]
            assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Id<String, AttributeMetadata>>>(attribute)
        }

        val placeOfBirth = pidDefinition.content["place_of_birth"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Obj<String, AttributeMetadata>>>(placeOfBirth)
        listOf(
            "locality",
            "region",
            "country",
        ).forEach { path ->
            val attribute = placeOfBirth.value.value.content[path]
            assertIs<Disclosable.AlwaysSelectively<DisclosableValue.Id<String, AttributeMetadata>>>(attribute)
        }
    }
}

internal val PidDefinition: SdJwtDefinition by lazy {
    val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(pidMeta)
    val resolvedTypeMetadata = sdJwtVcTypeMetadata.resolve()
    SdJwtDefinition.fromSdJwtVcMetadata(resolvedTypeMetadata)
}
private fun SdJwtVcTypeMetadata.resolve(): ResolvedTypeMetadata {
    return ResolvedTypeMetadata(
        vct = vct,
        name = name,
        description = description,
        display = checkNotNull(display).value,
        claims = checkNotNull(claims),
        schemas = schema?.let { listOf(it) }.orEmpty(),
    )
}

@Suppress("SameParameterValue")
private fun sdJwtVcTypeMetadata(json: String): SdJwtVcTypeMetadata = Json.decodeFromString(json)

private val pidMeta = """
    {
      "vct": "urn:eudi:pid:1",
      "name": "Type Metadata for Person Identification Data",
      "display": [
        {
          "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
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
              "lang": "en",
              "label": "Trust Anchor"
            }
          ],
          "sd": "always"
        }
      ]
      
    }
""".trimIndent()
