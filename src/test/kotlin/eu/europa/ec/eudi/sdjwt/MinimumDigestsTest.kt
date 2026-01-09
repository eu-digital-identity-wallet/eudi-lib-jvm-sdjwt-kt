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
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObject
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class MinimumDigestsTest {

    private fun SdJwt<SignedJWT>.topLevelDigests(): List<String> =
        jwt.jwtClaimsSet.jsonObject()[RFC9901.CLAIM_SD]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private val key = ECKeyGenerator(Curve.P_521).generate()
    private val issuer: SdJwtIssuer<SignedJWT> =
        NimbusSdJwtOps.issuer(
            signer = ECDSASigner(key),
            signAlgorithm = JWSAlgorithm.ES512,
            sdJwtFactory = SdJwtFactory(fallbackMinimumDigests = MinimumDigests(4)),
        )

    private fun testMinimumDigestsAtTopLevel(
        issuer: SdJwtIssuer<SignedJWT>,
        minimumDigests: Int,
    ): suspend (SdJwtObject) -> Unit = { spec ->
        val sdJwt = issuer.issue(spec).getOrThrow()
        sdJwt.prettyPrint { it.jwtClaimsSet.jsonObject() }
        val digests = sdJwt.topLevelDigests()
        assertTrue { minimumDigests <= digests.size }
    }

    @Test
    fun checkExplicitMinimumDigestsAtTopLevel() = runTest {
        val minimumDigests = 3
        val test = testMinimumDigestsAtTopLevel(issuer, minimumDigests)
        listOf(
            sdJwt(minimumDigests) {
                sdClaim("foo", "bar")
            },

            sdJwt(minimumDigests) {
                for (i in 0..<minimumDigests + 1)
                    sdClaim("$i", "value of $i")
            },
            sdJwt(minimumDigests) {
                sdArrClaim("baz") {
                    sdClaim("qux")
                }
            },
        ).forEach { test(it) }
    }

    @Test
    fun checkImplicitMinimumDigestsAtTopLevel() = runTest {
        val minimumDigests = 3
        val issuer =
            NimbusSdJwtOps.issuer(
                signer = ECDSASigner(key),
                signAlgorithm = JWSAlgorithm.ES512,
                sdJwtFactory = SdJwtFactory(fallbackMinimumDigests = MinimumDigests(minimumDigests)),
            )
        val test = testMinimumDigestsAtTopLevel(issuer, minimumDigests)

        listOf(
            sdJwt {
                sdClaim("foo", "bar")
            },

            sdJwt {
                for (i in 0..<minimumDigests + 1)
                    sdClaim("$i", "value of $i")
            },
            sdJwt {
                sdArrClaim("baz") {
                    sdClaim("qux")
                }
            },
        ).forEach { test(it) }
    }
}
