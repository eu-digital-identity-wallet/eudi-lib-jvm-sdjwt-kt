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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrObj private constructor(
    val jwt: SignedJWT,
    val disclosures: List<Disclosure>,
    val kbJwt: SignedJWT?,
) {

    companion object {
        fun parse(s: String): TrObj {
            @Serializable
            data class UnprotectedHeader(
                val disclosures: List<String>,
                @SerialName("kb_jwt") val kbJwt: Jwt? = null,
            )

            @Serializable
            data class Signature(
                val protected: String,
                val signature: String,
                @SerialName("header") val unprotectedHeader: UnprotectedHeader? = null,
            )

            @Serializable
            data class General(
                val payload: String,
                val signatures: List<Signature> = emptyList(),
            ) {
                init {
                    require(signatures.size == 1) { "There should be 1 signature. Found ${signatures.size}" }
                    requireNotNull(signatures.first().unprotectedHeader)
                }
            }

            @Serializable
            data class Flattened(
                val payload: String,
                @SerialName("header") val unprotectedHeader: UnprotectedHeader,
                val protected: String,
                val signature: String,
            )

            return try {
                Json.decodeFromString<General>(s).run {
                    val (signature) = signatures
                    val unprotectedHeader = requireNotNull(signatures.first().unprotectedHeader)
                    TrObj(
                        SignedJWT(Base64URL(signature.protected), Base64URL(payload), Base64URL(signature.signature)),
                        disclosures = unprotectedHeader.disclosures.map { Disclosure.wrap(it).getOrThrow() },
                        kbJwt = unprotectedHeader.kbJwt?.let { SignedJWT.parse(it) },
                    )
                }
            } catch (t: Throwable) {
                Json.decodeFromString<Flattened>(s).run {
                    TrObj(
                        SignedJWT(Base64URL(protected), Base64URL(payload), Base64URL(signature)),
                        disclosures = unprotectedHeader.disclosures.map { Disclosure.wrap(it).getOrThrow() },
                        kbJwt = unprotectedHeader.kbJwt?.let { SignedJWT.parse(it) },
                    )
                }
            }
        }
    }
}

class JwsJsonSerializationTest {

    @Test
    fun ex1() {
        val trObj = TrObj.parse(ex1)
        val sdJwt: SdJwt<SignedJWT> = SdJwt(trObj.jwt, trObj.disclosures)
        val actual =
            with(NimbusSdJwtOps) {
                sdJwt.asJwsJsonObject(option = JwsSerializationOption.Flattened)
                    .also { println(json.encodeToString(it)) }
            }
        assertEquals(json.parseToJsonElement(ex1), actual)
    }

    @Test
    fun `get a JwsJSON for an Issued SDJWT`() = runTest {
        val issuer = run {
            val issuerKey = ECKeyGenerator(Curve.P_256).generate()
            NimbusSdJwtOps.issuer(signer = ECDSASigner(issuerKey), signAlgorithm = JWSAlgorithm.ES256)
        }

        val sdJwtSpec = sdJwt {
            sdClaim("age_over_18", true)
        }

        val sdJwt = assertDoesNotThrow { issuer.issue(sdJwtSpec).getOrThrow() }

        with(NimbusSdJwtOps) {
            sdJwt.asJwsJsonObject(option = JwsSerializationOption.Flattened)
                .also { println(json.encodeToString(it)) }
        }
    }

    @Test
    fun `parseJWSJson should extract parts jwt, disclosures and kbJwt`() {
        val unverifiedSdJwt = Json.parseToJsonElement(ex2).jsonObject
        val (jwt, ds, kbJwt) = JwsJsonSupport.parseJWSJson(unverifiedSdJwt)
        assertEquals(2, ds.size)
        assertNotNull(kbJwt)
        assertDoesNotThrow { SignedJWT.parse(jwt) }
        assertDoesNotThrow { SignedJWT.parse(kbJwt) }
    }

    @Test
    fun `parseJWSJson should extract parts jwt, disclosures when presentation doesn't have kb-jwt`() {
        val unverifiedSdJwt = Json.parseToJsonElement(ex3).jsonObject
        val (jwt, ds, kbJwt) = JwsJsonSupport.parseJWSJson(unverifiedSdJwt)
        assertEquals(2, ds.size)
        assertNull(kbJwt)
        assertDoesNotThrow { SignedJWT.parse(jwt) }
    }

    @Test
    fun `verify with kbJwt`() = runTest {
        val unverifiedSdJwt = Json.parseToJsonElement(ex2).jsonObject
        val sdJwt2 = assertDoesNotThrow {
            JwsJsonSupport.parseIntoStandardForm(unverifiedSdJwt)
        }
        assertEquals(ex2SimpleFormat, sdJwt2)
    }
}

private val ex1 = """
    {
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJTRE43OU5McEFuSFBta3JkZVlkRWE4OVhaZHNrME04REtZU1FPVTJaeFFjIiwgIlh6RnJ6d3NjTTZHbjZDSkRjNnZWSzhCa01uZkc4dk9TS2ZwUElaZEFmZEUiLCAiZ2JPc0k0RWRxMngyS3ctdzV3UEV6YWtvYjloVjFjUkQwQVROM29RTDlKTSIsICJqTUNYVnotLTliOHgzN1ljb0RmWFFpbnp3MXdaY2NjZkZSQkNGR3FkRzJvIiwgIm9LSTFHZDJmd041V3d2amxGa29oaWRHdmltLTMxT3VsUjNxMGhyRE8wNzgiXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiJ9",
      "protected": "eyJhbGciOiAiRVMyNTYifQ",
      "signature": "9tz3nIr4COwA4VjSkRwk6v1Dt62Q4-zwdidjlCHogtdAYLdtMtbewe6b009hobPl3DeG4n-ZNESaS-WMiFWGgA",
      "header" : {
          "disclosures": [
            "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgInN1YiIsICJqb2huX2RvZV80MiJd",
            "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd",
            "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
            "WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ",
            "WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ",
            "WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0",
            "WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0"
          ]
      }
    }
""".trimIndent()

private val ex2 = """
    {
      "header": {
        "disclosures": [
          "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
          "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        ],
        "kb_jwt": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZCI6ICJodHRwczovL3ZlcmlmaWVyLmV4YW1wbGUub3JnIiwgImlhdCI6IDE3MjUzNzQ0MTMsICJzZF9oYXNoIjogImQ5T3pJclJQY2dVanNKb3NzeVJ3SjZNOXo5TGpneGQtWmk3VmJfNGxveXMifQ.KEni_tu4WRFeH7croigMQu2u0Xy3dsUf7bmmDT8Q5yTg_xFh7kMxbWemFglmFUVrwqxdLHvXNuiKguF3TztL9Q"
      },
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJjZ0ZkaHFQbzgzeFlObEpmYWNhQ2FhN3VQOVJDUjUwVkU1UjRMQVE5aXFVIiwgImpNQ1hWei0tOWI4eDM3WWNvRGZYUWluencxd1pjY2NmRlJCQ0ZHcWRHMm8iXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ",
      "protected": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0",
      "signature": "QqT_REPTOaBX4EzA9rQqad_iOL6pMl9_onmFH_q-Npyqal5TsxcUc5FIKjQL9BFO8QvA0BFbVbzaO-NLonN3Mw"
    }
""".trimIndent()

private val ex2SimpleFormat =
    """eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJjZ0ZkaHFQbzgzeFlObEpmYWNhQ2FhN3VQOVJDUjUwVkU1UjRMQVE5aXFVIiwgImpNQ1hWei0tOWI4eDM3WWNvRGZYUWluencxd1pjY2NmRlJCQ0ZHcWRHMm8iXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ.QqT_REPTOaBX4EzA9rQqad_iOL6pMl9_onmFH_q-Npyqal5TsxcUc5FIKjQL9BFO8QvA0BFbVbzaO-NLonN3Mw~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd~eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZCI6ICJodHRwczovL3ZlcmlmaWVyLmV4YW1wbGUub3JnIiwgImlhdCI6IDE3MjUzNzQ0MTMsICJzZF9oYXNoIjogImQ5T3pJclJQY2dVanNKb3NzeVJ3SjZNOXo5TGpneGQtWmk3VmJfNGxveXMifQ.KEni_tu4WRFeH7croigMQu2u0Xy3dsUf7bmmDT8Q5yTg_xFh7kMxbWemFglmFUVrwqxdLHvXNuiKguF3TztL9Q
    """.trimIndent()

private val ex3 = """
    {
      "header": {
        "disclosures": [
          "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
          "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        ]
      },
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJjZ0ZkaHFQbzgzeFlObEpmYWNhQ2FhN3VQOVJDUjUwVkU1UjRMQVE5aXFVIiwgImpNQ1hWei0tOWI4eDM3WWNvRGZYUWluencxd1pjY2NmRlJCQ0ZHcWRHMm8iXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ",
      "protected": "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0",
      "signature": "QqT_REPTOaBX4EzA9rQqad_iOL6pMl9_onmFH_q-Npyqal5TsxcUc5FIKjQL9BFO8QvA0BFbVbzaO-NLonN3Mw"
    }
""".trimIndent()
