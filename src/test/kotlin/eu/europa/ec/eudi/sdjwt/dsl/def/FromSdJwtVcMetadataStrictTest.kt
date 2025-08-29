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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FromSdJwtVcMetadataStrictTest {

    @Test
    fun testWithSimpleArray() {
        val resolvedMetadata = sdJwtVcTypeMetadata(metadataWithSimpleArray).resolve()
        val definition = SdJwtDefinition.fromSdJwtVcMetadataStrict(resolvedMetadata)
        assertEquals(3, definition.content.size)
        val nameDef = definition.content["name"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, *>>>(nameDef)
        val addressDef = definition.content["address"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, *>>>(addressDef)
        val degreesDef =
            assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, *>>>(definition.content["degrees"])
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, *>>>(degreesDef.value.value.content)
    }

    private val metadataWithSimpleArray = """
        {
          "vct": "https://betelgeuse.example.com/education_credential",
          "name": "Betelgeuse Education Credential - Preliminary Version",
          "description": "This is our development version of the education credential. Don't panic.",
          "extends": "https://galaxy.example.com/galactic-education-credential-0.9",
          "extends#integrity": "sha256-9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1VLmXfhWRL5",
          "display": [
            {
              "lang": "en-US",
              "name": "Betelgeuse Education Credential",
              "description": "An education credential for all carbon-based life forms on Betelgeusians",
              "rendering": {
                "simple": {
                  "logo": {
                    "uri": "https://betelgeuse.example.com/public/education-logo.png",
                    "uri#integrity": "sha256-LmXfh9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1V",
                    "alt_text": "Betelgeuse Ministry of Education logo"
                  },
                  "background_color": "#12107c",
                  "text_color": "#FFFFFF"
                },
                "svg_templates": [
                  {
                    "uri": "https://betelgeuse.example.com/public/credential-english.svg",
                    "uri#integrity": "sha256-8cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1VLmXfh9c",
                    "properties": {
                      "orientation": "landscape",
                      "color_scheme": "light",
                      "contrast": "high"
                    }
                  }
                ]
              }
            },
            {
              "lang": "de-DE",
              "name": "Betelgeuse-Bildungsnachweis",
              "rendering": {
                "simple": {
                  "logo": {
                    "uri": "https://betelgeuse.example.com/public/education-logo-de.png",
                    "uri#integrity": "sha256-LmXfh9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1V",
                    "alt_text": "Logo des Betelgeusischen Bildungsministeriums"
                  },
                  "background_color": "#12107c",
                  "text_color": "#FFFFFF"
                },
                "svg_templates": [
                  {
                    "uri": "https://betelgeuse.example.com/public/credential-german.svg",
                    "uri#integrity": "sha256-8cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1VLmXfh9c",
                    "properties": {
                      "orientation": "landscape",
                      "color_scheme": "light",
                      "contrast": "high"
                    }
                  }
                ]
              }
            }
          ],
          "claims": [
            {
              "path": ["name"],
              "display": [
                {
                  "lang": "de-DE",
                  "label": "Vor- und Nachname",
                  "description": "Der Name des Studenten"
                },
                {
                  "lang": "en-US",
                  "label": "Name",
                  "description": "The name of the student"
                }
              ],
              "sd": "allowed"
            },
            {
              "path": ["address"],
              "display": [
                {
                  "lang": "de-DE",
                  "label": "Adresse",
                  "description": "Adresse zum Zeitpunkt des Abschlusses"
                },
                {
                  "lang": "en-US",
                  "label": "Address",
                  "description": "Address at the time of graduation"
                }
              ],
              "sd": "always"
            },
            {
              "path": ["address", "street_address"],
              "display": [
                {
                  "lang": "de-DE",
                  "label": "Straße"
                },
                {
                  "lang": "en-US",
                  "label": "Street Address"
                }
              ],
              "sd": "always",
              "svg_id": "address_street_address"
            },
            {
              "path": ["degrees", null],
              "display": [
                {
                  "lang": "de-DE",
                  "label": "Abschluss",
                  "description": "Der Abschluss des Studenten"
                },
                {
                  "lang": "en-US",
                  "label": "Degree",
                  "description": "Degree earned by the student"
                }
              ],
              "sd": "allowed"
            }
          ],
          "schema_uri": "https://exampleuniversity.com/public/credential-schema-0.9",
          "schema_uri#integrity": "sha256-o984vn819a48ui1llkwPmKjZ5t0WRL5caxGgX3c1VLmXfh"
        }
    """.trimIndent()

    @Test
    fun testWithComplexArray() {
        val resolvedMetadata = sdJwtVcTypeMetadata(metadataWithComplexArray).resolve()
        val definition = SdJwtDefinition.fromSdJwtVcMetadataStrict(resolvedMetadata)
        assertEquals(1, definition.content.size)
        val residencesDef = assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, *>>>(definition.content["residences"])
        val residenceDef = assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, *>>>(residencesDef.value.value.content)
        assertEquals(2, residenceDef.value.value.content.size)
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, *>>>(residenceDef.value.value.content["postal_code"])
        val countryDef = assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, *>>>(residenceDef.value.value.content["country"])
        assertEquals(2, countryDef.value.value.content.size)
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, *>>>(countryDef.value.value.content["code"])
        val countryNamesDef =
            assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, *>>>(countryDef.value.value.content["names"])
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, *>>>(countryNamesDef.value.value.content)
    }

    private val metadataWithComplexArray = """
        {
          "vct": "https://example.com/residences",
          "name": "Residences",
          "display": [
            {
              "lang": "en",
              "name": "Residences"
            }
          ],
          "claims": [
            { "path": [ "residences", null ], "sd": "always" },
            { "path": [ "residences", null, "postal_code" ], "sd": "always" },
            { "path": [ "residences", null, "country" ], "sd": "always" },
            { "path": [ "residences", null, "country", "code" ], "sd": "always" },
            { "path": [ "residences", null, "country", "names", null ], "sd": "always" }
          ]
        }
    """.trimIndent()
}
