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
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement
import kotlin.test.*

class FindElementTest {

    @Test
    fun failsWhenFirstClaimPathElementIsNotClaim() {
        listOf(
            ClaimPath(listOf(ClaimPathElement.AllArrayElements)),
        ).forEach {
            assertNull(PidDefinition.findElement(it))
        }
    }

    @Test
    fun failsWhenArrayElementsAreUsed() {
        assertFailsWith(IllegalArgumentException::class) {
            PidDefinition.findElement(ClaimPath.claim("address").arrayElement(0))
        }
    }

    @Test
    fun testWithUnknownAttributesOrArrayIndexes() {
        listOf(
            ClaimPath.claim("a").claim("b").claim("c"),
            ClaimPath.claim("address").claim("b").claim("c"),
        ).forEach {
            assertNull(PidDefinition.findElement(it))
        }
    }

    @Test
    fun testWithKnownPidAttributes() {
        fun findElement(path: ClaimPath) = PidDefinition.findElement(path)

        val nationalitiesDef = findElement(ClaimPath.claim("nationalities"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, AttributeMetadata>>>(nationalitiesDef)

        val nationalityDef = findElement(ClaimPath.claim("nationalities").allArrayElements())
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(nationalityDef)

        val addressDef = findElement(ClaimPath.claim("address"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(addressDef)

        val countryDef = findElement(ClaimPath.claim("address").claim("country"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(countryDef)

        val ageOverOrEqual = findElement(ClaimPath.claim("age_equal_or_over"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(ageOverOrEqual)

        val eighteenDef = findElement(ClaimPath.claim("age_equal_or_over").claim("18"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(eighteenDef)
    }

    @Test
    fun testWithKnownPidAttributesDisplay() {
        fun labelOf(path: ClaimPath, lang: String = "en") =
            PidDefinition.findElement(path)
                ?.value
                ?.attributeMetadata()
                ?.display
                ?.first { it.lang.value == lang }
                ?.label

        listOf(
            ClaimPath.claim("nationalities"),
            ClaimPath.claim("address"),
            ClaimPath.claim("age_equal_or_over"),
            ClaimPath.claim("age_equal_or_over").claim("18"),
        ).forEach {
            val label = labelOf(it).also(::println)
            assertNotNull(label)
        }
    }

    @Test
    fun testWithArrays() {
        fun findElement(path: ClaimPath) = AddressDefinition.findElement(path)

        val addressesDef = findElement(ClaimPath.claim("addresses"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Arr<String, AttributeMetadata>>>(addressesDef)

        val addressDef = findElement(ClaimPath.claim("addresses").allArrayElements())
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(addressDef)

        val countryDef = findElement(ClaimPath.claim("addresses").allArrayElements().claim("country"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(countryDef)

        val localityDef = findElement(ClaimPath.claim("addresses").allArrayElements().claim("locality"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(localityDef)
    }

    @Test
    fun testArraysWithUnknownAttributesOrArrayIndexes() {
        listOf(
            ClaimPath.claim("addresses").claim("country").allArrayElements(),
            ClaimPath.claim("addresses").allArrayElements().claim("country").allArrayElements(),
        ).forEach {
            assertNull(AddressDefinition.findElement(it))
        }
    }
}
