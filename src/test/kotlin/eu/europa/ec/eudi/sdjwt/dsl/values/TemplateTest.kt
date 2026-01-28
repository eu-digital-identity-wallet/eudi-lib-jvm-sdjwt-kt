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
package eu.europa.ec.eudi.sdjwt.dsl.values

import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.dsl.def.AddressDefinition
import eu.europa.ec.eudi.sdjwt.dsl.def.PidDefinition
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateTest {

    @Test
    fun test() {
        val sdJwtFactory = SdJwtFactory(
            saltProvider = { "salt" },
            decoyGen = object : DecoyGen {
                override fun gen(hashingAlgorithm: HashAlgorithm): DisclosureDigest {
                    throw UnsupportedOperationException("Decoy generation not supported for this test")
                }

                override fun gen(hashingAlgorithm: HashAlgorithm, numOfDecoys: Int): Set<DisclosureDigest> =
                    emptySet()
            },
        )

        val spec =
            sdJwtVc(PidDefinition) {
                put("given_name", "Foo")
                put("family_name", "Bar")
                putJsonArray("nationalities") {
                    add("GR")
                }
                putJsonObject("age_equal_or_over") { put("18", true) }
                putJsonObject("address") {
                    put("country", "GR")
                    put("street_address", "12345 Main Street")
                }
            }.getOrThrow()

        val expectedSpec = sdJwt {
            claim(SdJwtVcSpec.VCT, PidDefinition.metadata.vct)
            sdClaim("given_name", "Foo")
            sdClaim("family_name", "Bar")
            sdArrClaim("nationalities") {
                sdClaim("GR")
            }
            sdObjClaim("age_equal_or_over") {
                sdClaim("18", true)
            }
            sdObjClaim("address") {
                sdClaim("country", "GR")
                sdClaim("street_address", "12345 Main Street")
            }
        }

        val expectedUnsigned = sdJwtFactory.createSdJwt(expectedSpec).getOrThrow()
        val expectedRecreated = expectedUnsigned.recreateClaimsAndDisclosuresPerClaim().getOrThrow().first

        val unsigned = sdJwtFactory.createSdJwt(spec).getOrThrow()
        val actualRecreated = unsigned.recreateClaimsAndDisclosuresPerClaim().getOrThrow().first

        assertEquals(expectedRecreated.toSortedMap(), actualRecreated.toSortedMap())
    }

    @Test
    fun useJsonFile() {
        val addressesJsonStr = """
            {
                "addresses": [
                    { "street_address": "12345 Main Street", "country": "GR" },
                    { "locality": "Athens", "country": "GR" }
                ]
            }
        """.trimIndent()

        val addresses = Json.parseToJsonElement(addressesJsonStr).jsonObject
        val spec =
            with(DefinitionBasedSdJwtObjectBuilder(AddressDefinition)) {
                val (spec, errors) = build(addresses)
                errors.forEach { println(it) }
                assertEquals(0, errors.size)
                spec
            }
        val unsigned = SdJwtFactory.Default.createSdJwt(spec).getOrThrow()
        assertEquals(7, unsigned.disclosures.size)
        assertEquals(3, unsigned.jwtPayload.size)
        val recreated = unsigned.recreateClaimsAndDisclosuresPerClaim().getOrThrow().first - SdJwtVcSpec.VCT
        assertEquals(addresses.toSortedMap(), recreated.toSortedMap())
    }
}
