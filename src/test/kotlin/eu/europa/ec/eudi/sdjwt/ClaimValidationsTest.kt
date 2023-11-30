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

import kotlinx.serialization.json.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaimValidationsTest {

    private val now = Instant.now()
    private val clock = Clock.fixed(now, Clock.systemUTC().zone)
    private val iatOffset = Duration.ofSeconds(30)

    @Test
    fun `check sdJwt happy path with combined form`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() - 10).epochSecond)
        val sdJwt = "foo"
        val clientId = "clientId"
        val claims = buildJsonObject {
            iat(iat.epochSecond)
            put("nonce", "nonce")
            put("aud", JsonArray(listOf(clientId, "foo").map { JsonPrimitive(it) }))
            put("_sd_jwt", sdJwt)
        }
        assertEquals(sdJwt, claims.envelopSdJwt(clock, iatOffset, clientId))
    }

    @Test
    fun `check sdJwt happy path with JwsJson Flatten`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() - 10).epochSecond)
        val header = "header"
        val payload = "paylaod"
        val signature = "signature"
        val ds = listOf("d1", "d2")
        val sdJwt = "$header.$payload.$signature${concatDisclosureValues(ds){it}}~"
        val clientId = "clientId"
        val claims = buildJsonObject {
            iat(iat.epochSecond)
            put("nonce", "nonce")
            put("aud", JsonArray(listOf(clientId, "foo").map { JsonPrimitive(it) }))
            putJsonObject("_js_sd_jwt") {
                put("payload", payload)
                put("protected", header)
                put("signature", signature)
                put("disclosures", JsonArray(ds.map { JsonPrimitive(it) }))
            }
        }
        assertEquals(sdJwt, claims.envelopSdJwt(clock, iatOffset, clientId))
    }

    @Test
    fun `check sdJwt happy path with JwsJson General`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() - 10).epochSecond)
        val header = "header"
        val payload = "paylaod"
        val signature = "signature"
        val ds = listOf("d1", "d2")
        val sdJwt = "$header.$payload.$signature${concatDisclosureValues(ds){it}}~"
        val clientId = "clientId"
        val claims = buildJsonObject {
            iat(iat.epochSecond)
            put("nonce", "nonce")
            put("aud", JsonArray(listOf(clientId, "foo").map { JsonPrimitive(it) }))
            putJsonObject("_js_sd_jwt") {
                put("payload", payload)
                putJsonArray("signatures") {
                    add(
                        buildJsonObject {
                            put("protected", header)
                            put("signature", signature)
                        },
                    )
                }
                put("disclosures", JsonArray(ds.map { JsonPrimitive(it) }))
            }
        }
        assertEquals(sdJwt, claims.envelopSdJwt(clock, iatOffset, clientId))
    }

    @Test
    fun `check iat issued within offset`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() - 10).epochSecond)
        assertEquals(iat, mapOf("iat" to JsonPrimitive(iat.epochSecond)).iat(clock, iatOffset))
    }

    @Test
    fun `check iat issued outside offset`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.minusSeconds(iatOffset.toSeconds() + 10).epochSecond)
        assertNull(mapOf("iat" to JsonPrimitive(iat.epochSecond)).iat(clock, iatOffset))
    }

    @Test
    fun `check iat after now`() = with(ClaimValidations) {
        val iat = Instant.ofEpochSecond(now.plusSeconds(10).epochSecond)
        assertNull(mapOf("iat" to JsonPrimitive(iat.epochSecond)).iat(clock, iatOffset))
    }

    @Test
    fun `check aud when single string`() = with(ClaimValidations) {
        val expectedAud = "some"
        assertEquals(listOf(expectedAud), mapOf("aud" to JsonPrimitive(expectedAud)).aud())
    }

    @Test
    fun `check Nonce`() = with(ClaimValidations) {
        assertNull(mapOf("nonce" to JsonNull).nonce())
        assertNull(mapOf("nonce" to JsonArray(emptyList())).nonce())
        assertEquals("nonce", mapOf("nonce" to JsonPrimitive("nonce")).nonce())
    }

    @Test
    fun `check primitiveClaims`() = with(ClaimValidations) {
        val claims = mapOf(
            "primitiveString" to JsonPrimitive("primitiveString"),
            "nullClaim" to JsonNull,
            "array" to JsonArray(listOf("e1", "e2").map { JsonPrimitive(it) }),
        )

        assertEquals(JsonNull, claims.primitiveClaim("nullClaim"))
        assertEquals(JsonPrimitive("primitiveString"), claims.primitiveClaim("primitiveString"))
        assertNull(claims.primitiveClaim("array"))
    }
}
