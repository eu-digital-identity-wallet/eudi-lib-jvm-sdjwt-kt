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
package eu.europa.ec.eudi.sdjwt.examples

import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlin.test.*

/**
* [sdJwt]
*/
class ExampleFlatSdJwt01Test {

    @Test
    fun testExampleFlatSdJwt01() {
        flatSdJwt.assertThat("Example 1: Flat SD-JWT", 1)
    }
}
