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
import org.junit.jupiter.api.Test

class TypeMetadataTest {
    val jsonSupport = Json { ignoreUnknownKeys = true }

    @Test
    fun `simple parsing`() {
        val json = """
            {
              "vct":"https://betelgeuse.example.com/education_credential",
              "name":"Betelgeuse Education Credential - Preliminary Version",
              "description":"This is our development version of the education credential. Don't panic.",
              "extends":"https://galaxy.example.com/galactic-education-credential-0.9",
              "extends#integrity":"sha256-9cLlJNXN-TsMk-PmKjZ5t0WRL5ca_xGgX3c1VLmXfh-WRL5",
              "schema_uri":"https://exampleuniversity.com/public/credential-schema-0.9",
              "schema_uri#integrity":"sha256-o984vn819a48ui1llkwPmKjZ5t0WRL5ca_xGgX3c1VLmXfh"
            }
        """.trimIndent()
        val metadata = jsonSupport.decodeFromString<SdJwtVcTypeMetadata>(json)
    }
}