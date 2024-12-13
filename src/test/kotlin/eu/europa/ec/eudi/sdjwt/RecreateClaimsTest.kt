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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RecreateClaimsTest {

    @Test
    fun `recreating plain claims should return the plain claims`() {
        val plainClaims = buildJsonObject {
            put("iss", "iss")
            put("sub", "sub")
        }

        val sdJwtElements = sdJwt {
            plainClaims.forEach { claim(it.key, it.value) }
        }
        val actual = discloseAndRecreate(sdJwtElements)

        assertEquals(plainClaims, actual)
    }

    private fun discloseAndRecreate(sdElements: DisclosableObject): JsonObject {
        val sdJwt = SdJwtFactory().createSdJwt(sdElements).getOrThrow()
        return with(SdJwtRecreateClaimsOps { claims: JsonObject -> claims }) {
            sdJwt.recreateClaims(visitor = null).also {
                println(json.encodeToString(it))
            }
        }
    }

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
            plainClaims.forEach { claim(it.key, it.value) }
            flatClaims.forEach { sdClaim(it.key, it.value) }
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
            plainClaims.forEach { claim(it.key, it.value) }
            objClaim("structured") {
                structuredPlainSubClaims.forEach { claim(it.key, it.value) }
                structuredSubClaims.forEach { sdClaim(it.key, it.value) }
            }
        }

        val expected = plainClaims + buildJsonObject {
            put("structured", JsonObject(structuredSubClaims + structuredPlainSubClaims))
        }

        val actual = discloseAndRecreate(sdJwtElements)
        assertEquals(expected, actual)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `recreating plain and recursive which contains some plain and flats`() {
        val plainClaims = buildJsonObject {
            put("iss", "iss")
            put("sub", "sub")
        }
        val subClaims = buildJsonObject {
            put("test123", 123)
            putJsonArray("foo") { addAll(listOf(true, false, true)) }
            putJsonArray("addresses") {
                add(
                    buildJsonObject {
                        put("type", "work")
                        put("street", "workStreet")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "home")
                        put("street", "homeStreet")
                    },
                )
            }
        }
        val sdJwtElements = sdJwt {
            plainClaims.forEach { claim(it.key, it.value) }
            sdObjClaim("rec") {
                subClaims.forEach { sdClaim(it.key, it.value) }
            }
        }

        val expected = plainClaims + buildJsonObject {
            put("rec", subClaims)
        }

        val actual = discloseAndRecreate(sdJwtElements)
        assertEquals(expected, actual)
    }
}
