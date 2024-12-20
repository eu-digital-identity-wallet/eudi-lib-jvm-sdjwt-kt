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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StandardSerializationTest {

    private val issuer by lazy {
        val issuerKey = ECKeyGenerator(Curve.P_256).generate()
        NimbusSdJwtOps.issuer(signer = ECDSASigner(issuerKey), signAlgorithm = JWSAlgorithm.ES256)
    }

    private val keyBindingSigner: BuildKbJwt by lazy {
        val holderKey = ECKeyGenerator(Curve.P_256).generate()
        NimbusSdJwtOps.kbJwtIssuer(
            signer = ECDSASigner(holderKey),
            signAlgorithm = JWSAlgorithm.ES256,
            publicKey = holderKey.toPublicJWK(),
        )
    }

    @Test
    fun `An SD-JWT without disclosures or KBJWT should end in a single ~`() = runTest {
        val sdJwtSpec = sdJwt {
            claim("foo", "bar")
        }
        val sdJwt = issuer.issue(sdJwtSpec).getOrThrow()
        val expected =
            buildString {
                append(sdJwt.jwt.serialize())
                append("~")
            }
        val actual = with(NimbusSdJwtOps) { sdJwt.serialize() }
        assertEquals(expected, actual)
    }

    @Test
    fun `An SD-JWT with disclosures and without KBJWT should end in a single ~`() = runTest {
        val sdJwtSpec = sdJwt {
            sdClaim("foo", "bar")
        }
        val sdJwt = issuer.issue(sdJwtSpec).getOrThrow()
        val expected =
            buildString {
                append(sdJwt.jwt.serialize())
                append("~")
                for (d in sdJwt.disclosures) {
                    append(d.value)
                    append("~")
                }
            }
        val actual = with(NimbusSdJwtOps) { sdJwt.serialize() }
        assertEquals(expected, actual)
    }

    @Test
    fun `An SD-JWT without disclosures with KBJWT should not end in ~`() = runTest {
        with(NimbusSdJwtOps) {
            val sdJwtSpec = sdJwt {
                claim("foo", "bar")
            }
            val issuedSdJwt = issuer.issue(sdJwtSpec).getOrThrow()
            val sdJwt = issuedSdJwt.present(emptySet())
            assertNotNull(sdJwt)

            val actual = sdJwt.serializeWithKeyBinding(keyBindingSigner).getOrThrow()
            assertTrue { actual.count { it == '~' } == 1 }
            val (_, disclosures, kbJwt1) = StandardSerialization.parse(actual)
            assertTrue { disclosures.isEmpty() }
            assertNotNull(kbJwt1)
        }
    }

    @Test
    fun `An SD-JWT with disclosures and KBJWT should not end in ~`() = runTest {
        with(NimbusSdJwtOps) {
            val sdJwtSpec = sdJwt {
                sdClaim("foo", "bar")
            }
            val issuedSdJwt = issuer.issue(sdJwtSpec).getOrThrow()
            val sdJwt = issuedSdJwt.present(emptySet())
            assertNotNull(sdJwt)

            val actual = sdJwt.serializeWithKeyBinding(keyBindingSigner).getOrThrow()
            assertTrue { actual.count { it == '~' } == 2 }
            val (_, disclosures, kbJwt1) = StandardSerialization.parse(actual)
            assertEquals(1, disclosures.size)
            assertNotNull(kbJwt1)
        }
    }
}
