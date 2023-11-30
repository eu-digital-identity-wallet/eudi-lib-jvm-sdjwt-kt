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

import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TrObj private constructor(
    val jwt: SignedJWT,
    val disclosures: Set<Disclosure>,
) {

    companion object {
        fun parse(s: String): TrObj {
            @Serializable
            data class Signature(
                val protected: String,
                val signature: String,
            )

            @Serializable
            data class General(
                val payload: String,
                val disclosures: Set<String> = emptySet(),
                val signatures: List<Signature> = emptyList(),
            ) {
                init {
                    require(signatures.size == 1) { "There should be 1 signature. Found ${signatures.size}" }
                }
            }

            @Serializable
            data class Flattened(
                val payload: String,
                val disclosures: Set<String> = emptySet(),
                val protected: String,
                val signature: String,
            )

            return try {
                Json.decodeFromString<General>(s).run {
                    val (signature) = signatures

                    TrObj(
                        SignedJWT(Base64URL(signature.protected), Base64URL(payload), Base64URL(signature.signature)),
                        disclosures = disclosures.map { Disclosure.wrap(it).getOrThrow() }.toSet(),
                    )
                }
            } catch (t: Throwable) {
                Json.decodeFromString<Flattened>(s).run {
                    TrObj(
                        SignedJWT(Base64URL(protected), Base64URL(payload), Base64URL(signature)),
                        disclosures = disclosures.map { Disclosure.wrap(it).getOrThrow() }.toSet(),
                    )
                }
            }
        }
    }
}

class JwsJsonSerialization {

    @Test
    fun ex1() {
        val trObj = TrObj.parse(ex1)
        val sdJwt = SdJwt.Issuance(trObj.jwt, trObj.disclosures)
        val actual =
            sdJwt.asJwsJsonObject(option = JwsSerializationOption.Flattened).also { println(json.encodeToString(it)) }
        assertEquals(json.parseToJsonElement(ex1), actual)
    }
}

private val ex1 = """
    {
      "payload": "eyJfc2QiOiBbIjRIQm42YUlZM1d0dUdHV1R4LXFVajZjZGs2V0JwWnlnbHRkRmF2UGE3TFkiLCAiOHNtMVFDZjAyMXBObkhBQ0k1c1A0bTRLWmd5Tk9PQVljVGo5SE5hQzF3WSIsICJTRE43OU5McEFuSFBta3JkZVlkRWE4OVhaZHNrME04REtZU1FPVTJaeFFjIiwgIlh6RnJ6d3NjTTZHbjZDSkRjNnZWSzhCa01uZkc4dk9TS2ZwUElaZEFmZEUiLCAiZ2JPc0k0RWRxMngyS3ctdzV3UEV6YWtvYjloVjFjUkQwQVROM29RTDlKTSIsICJqTUNYVnotLTliOHgzN1ljb0RmWFFpbnp3MXdaY2NjZkZSQkNGR3FkRzJvIiwgIm9LSTFHZDJmd041V3d2amxGa29oaWRHdmltLTMxT3VsUjNxMGhyRE8wNzgiXSwgImlzcyI6ICJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgIl9zZF9hbGciOiAic2hhLTI1NiJ9",
      "protected": "eyJhbGciOiAiRVMyNTYifQ",
      "signature": "9tz3nIr4COwA4VjSkRwk6v1Dt62Q4-zwdidjlCHogtdAYLdtMtbewe6b009hobPl3DeG4n-ZNESaS-WMiFWGgA",
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
""".trimIndent().removeNewLine()
