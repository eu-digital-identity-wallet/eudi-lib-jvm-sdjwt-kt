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

class ClaimPathTest {
    val jsonSupport = Json { ignoreUnknownKeys = true }

    @Test
    fun `parsing happy path`() {
        val jsonExamples = listOf(
            """["name"]""",
            """["address"]""",
            """["address", "street_address"]""",
            """["degrees", null, "type"]""",
        )

        jsonExamples.forEach { example ->
            val path = jsonSupport.decodeFromString(ClaimPath.serializer(), example)
            println(path)
        }
    }
}
