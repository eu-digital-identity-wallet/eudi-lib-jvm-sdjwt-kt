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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RecreateClaimsTest {

    @Test
    fun `recreating plain claims should return the plain claims`() {
        val plainClaims = buildJsonObject {
            put("iss", "iss")
            put("sub", "sub")
        }

        val sdJwtElements = sdJwt { plain(plainClaims) }
        val actual = discloseAndRecreate(sdJwtElements)

        assertEquals(plainClaims, actual)
    }

    private fun discloseAndRecreate(sdJwtElements: List<SdJwtElement>): Claims =
        DisclosuresCreator().discloseSdJwt(sdJwtElements).getOrThrow().recreateClaims()

    @Test
    fun `recreating plain and flat disclosed claims should return their combination`() {
        val plainClaims = buildJsonObject {
            put("iss", "iss")
            put("sub", "sub")
        }
        val flatClaims = buildJsonObject {
            put("foo", "bar")
            put("tsou", "la")
            put("d1", 1)
        }

        val sdJwtElements = sdJwt {
            plain(plainClaims)
            flat(flatClaims)
        }
        val expected = JsonObject(plainClaims + flatClaims)
        val actual = discloseAndRecreate(sdJwtElements)

        assertEquals(expected, actual)
    }

    @Test
    fun `recreating plain and structure which contains some plain and flats`() {
        val plainClaims = buildJsonObject {
            put("iss", "iss")
            put("sub", "sub")
        }
        val structuredPlainSubClaims = buildJsonObject {
            put("test123", 123)
        }
        val structuredSubClaims = buildJsonObject {
            put("foo", "bar")
            put("foo1", "bar1")
            put("d1", 1)
        }

        val sdJwtElements = sdJwt {
            plain(plainClaims)
            structured("structured") {
                plain(structuredPlainSubClaims)
                flat(structuredSubClaims)
            }
        }

        val expected = plainClaims + buildJsonObject {
            put("structured", JsonObject(structuredSubClaims + structuredPlainSubClaims))
        }

        val actual = discloseAndRecreate(sdJwtElements)
        assertEquals(expected, actual)
    }
}
