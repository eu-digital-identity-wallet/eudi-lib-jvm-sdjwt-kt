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

import kotlinx.serialization.json.Json
import kotlin.test.Test

class TypeMetadataTest {
    val jsonSupport = Json { ignoreUnknownKeys = false }

    @Test
    fun `simple parsing`() {
        val json = """
            {
              "vct":"https://betelgeuse.example.com/education_credential",
              "name":"Betelgeuse Education Credential - Preliminary Version",
              "description":"This is our development version of the education credential. Don't panic.",
              "extends":"https://galaxy.example.com/galactic-education-credential-0.9",
              "extends#integrity":"sha256-9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1VLmXfhWRL5"
            }
        """.trimIndent()
        jsonSupport.decodeFromString<SdJwtVcTypeMetadata>(json)
    }

    @Test
    fun `claim metadata`() {
        val json = """
            {
      "path": ["name"],
      "display": [
        {
          "locale": "de-DE",
          "label": "Vor- und Nachname",
          "description": "Der Name des Studenten"
        },
        {
          "locale": "en-US",
          "label": "Name",
          "description": "The name of the student"
        }
      ],
      "sd": "allowed"
    }
        """.trimIndent()
        jsonSupport.decodeFromString<ClaimMetadata>(json)
    }

    @Test
    fun `extended example`() {
        val json = """
            {
              "vct": "https://betelgeuse.example.com/education_credential",
              "name": "Betelgeuse Education Credential - Preliminary Version",
              "description": "This is our development version of the education credential. Don't panic.",
              "extends": "https://galaxy.example.com/galactic-education-credential-0.9",
              "extends#integrity": "sha256-9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1VLmXfhWRL5",
              "display": [
                {
                  "locale": "en-US",
                  "name": "Betelgeuse Education Credential",
                  "description": "An education credential for all carbon-based life forms on Betelgeusians",
                  "rendering": {
                    "simple": {
                      "logo": {
                        "uri": "https://betelgeuse.example.com/public/education-logo.png",
                        "uri#integrity": "sha256-LmXfh9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1V",
                        "alt_text": "Betelgeuse Ministry of Education logo"
                      },
                      "background_image": {
                        "uri": "https://betelgeuse.example.com/public/education-logo.png",
                        "uri#integrity": "sha256-LmXfh9cLlJNXNTsMkPmKjZ5t0WRL5caxGgX3c1V"
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
                  "locale": "de-DE",
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
                      "locale": "de-DE",
                      "label": "Vor- und Nachname",
                      "description": "Der Name des Studenten"
                    },
                    {
                      "locale": "en-US",
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
                      "locale": "de-DE",
                      "label": "Adresse",
                      "description": "Adresse zum Zeitpunkt des Abschlusses"
                    },
                    {
                      "locale": "en-US",
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
                      "locale": "de-DE",
                      "label": "Straße"
                    },
                    {
                      "locale": "en-US",
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
                      "locale": "de-DE",
                      "label": "Abschluss",
                      "description": "Der Abschluss des Studenten"
                    },
                    {
                      "locale": "en-US",
                      "label": "Degree",
                      "description": "Degree earned by the student"
                    }
                  ],
                  "sd": "allowed"
                }
              ]
            }
        """.trimIndent()
        jsonSupport.decodeFromString<SdJwtVcTypeMetadata>(json).also(::println)
    }
}
