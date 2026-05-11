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
package eu.europa.ec.eudi.sdjwt

import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DefaultSdJwtOpsTest {

    @Nested
    inner class UnverifiedIssuanceFromTest {

        @Test
        fun `when sd-jwt contains disclosures with claims with reserved names, verification fails`() {
            fun test(disclosure: String) {
                val exception = assertFailsWith<SdJwtVerificationException> {
                    DefaultSdJwtOps.unverifiedIssuanceFrom("$jwt~$disclosure~").getOrThrow()
                }
                val error = assertIs<VerificationError.InvalidDisclosures>(exception.reason)
                val invalidDisclosures = error.invalidDisclosures
                assertEquals(1, invalidDisclosures.size)
                assertEquals("Given claim should not contain an attribute named _sd_alg, or _sd, or ...", invalidDisclosures.keys.first())
            }

            // _sd
            test("WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsIl9zZCIsIk3DtmJpdXMiXQ")

            // ...
            test("WyJfMjZiYzRMVC1hYzZxMktJNmNCVzVlcyIsIi4uLiIsIk3DtmJpdXMiXQ")
        }
    }
}

private val jwt =
    """
        eyJ0eXAiOiJzZCtqd3QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGU
        uY29tL2lzc3VlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxODA0MjI5MDUzLCJzdWIiOiI
        2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCJuYmYiOjE1MTYyMzkwMjI
        sImF1ZCI6InRlc3QifQ.r1To6Mgu64GUuKdTngt0ElcqQOZS8tGIZ39BhyzM5xGF5TFVeuVr
        yr46v-tnfBsSa9PX9bQDCmkEsPpzyQaLJA
    """.trimIndent().removeNewLine()
