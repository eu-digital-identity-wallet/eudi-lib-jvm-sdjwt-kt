/*
 * Copyright (c) 2023-2026 European Commission
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
import eu.europa.ec.eudi.sdjwt.vc.ResolvedTypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SdJwtObjectDefinitionFromSdJwtVcMetadataTest {

    private fun commonChecks(
        resolvedTypeMetadata: ResolvedTypeMetadata,
        sdJwtDefinition: SdJwtDefinition,
    ) {
        val vctMetadata = sdJwtDefinition.metadata
        assertEquals(resolvedTypeMetadata.vct, vctMetadata.vct)
        assertEquals(resolvedTypeMetadata.name, vctMetadata.name)
        assertEquals(resolvedTypeMetadata.description, vctMetadata.description)
        assertEquals(resolvedTypeMetadata.display, vctMetadata.display)
    }

    @Test
    fun checkPidDefinition() {
        val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(pidMeta).resolve()
        val sdJwtVcDefinition = SdJwtDefinition.fromSdJwtVcMetadata(sdJwtVcTypeMetadata)

        val nationalities = sdJwtVcDefinition.content["nationalities"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, AttributeMetadata>>>(nationalities)

        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(nationalities.value.value.content)

        val address = sdJwtVcDefinition.content["address"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(address)
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
            assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(attribute)
        }

        val placeOfBirth = sdJwtVcDefinition.content["place_of_birth"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(placeOfBirth)
        listOf(
            "locality",
            "region",
            "country",
        ).forEach { path ->
            val attribute = placeOfBirth.value.value.content[path]
            assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(attribute)
        }

        commonChecks(sdJwtVcTypeMetadata, sdJwtVcDefinition)
    }

    @Test
    fun checkAddressDefinition() {
        val sdJwtVcTypeMetadata = sdJwtVcTypeMetadata(addressMeta).resolve()
        val sdJwtVcDefinition = SdJwtDefinition.fromSdJwtVcMetadata(sdJwtVcTypeMetadata)

        val addressArrayDef = sdJwtVcDefinition.content["addresses"]
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, AttributeMetadata>>>(addressArrayDef)
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(
            addressArrayDef.value.value.content,
        )

        commonChecks(sdJwtVcTypeMetadata, sdJwtVcDefinition)
    }
}
