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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test cases for [SingleClaimJsonPath].
 */
internal class SingleClaimJsonPathTest {

    @Test
    internal fun `verify fromJson parses valid paths`() {
        validPaths.forEach {
            assertEquals(it, SingleClaimJsonPath.fromJsonPath(it)?.asJsonPath())
        }
    }

    @Test
    internal fun `verify fromJson does not parse invalid paths`() {
        invalidPaths.forEach {
            assertNull(SingleClaimJsonPath.fromJsonPath(it))
        }
    }

    private val validPaths = setOf(
        "\$.family",
        "\$.family.children",
        "\$.family.children[2]",
        "\$.family.children[2].name",
        "\$.family.children[2].siblings",
        "\$.family.children[2].siblings[0]",
        "\$.family.children[2].siblings[0].name",
    )

    private val invalidPaths = setOf(
        "\$.family['children']",
        "\$.family.children[-1]",
        "\$.family.children[-3]",
        "\$.family.children[1:3]",
        "\$.family.children[:3]",
        "\$.family.children[:-1]",
        "\$.family.children[2:]",
        "\$.family.children[-2:]",
        "\$..name",
        "\$.family..name",
        "\$.family.children[:3]..age",
        "\$..['name','nickname']",
        "\$.family.children[0].*",
    )
}
