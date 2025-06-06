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
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FindElementTest {

    @Test
    fun testWithUnknownAttributes() {
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

        val addressDef = findElement(ClaimPath.claim("address"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Obj<String, AttributeMetadata>>>(addressDef)

        val eighteenDef = findElement(ClaimPath.claim("age_equal_or_over").claim("18"))
        assertIs<Disclosable.AlwaysSelectively<DisclosableDef.Id<String, AttributeMetadata>>>(eighteenDef)
    }

    @Test
    fun testWithKnownPidAttributesDispla() {
        fun labelOf(path: ClaimPath, lang: String = "en") =
            PidDefinition.findElement(path)
                ?.value?.attributeMetadata()
                ?.display
                ?.first { it.lang.value == lang }?.label

        listOf(
            ClaimPath.claim("nationalities"),
            ClaimPath.claim("address"),
            ClaimPath.claim("age_equal_or_over").claim("18"),
        ).forEach {
            val label = labelOf(it).also { println(it) }
            assertNotNull(label)
        }
    }
}
