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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObject
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertTrue

class MinimumDigestsTest {

    private fun JsonObject.digests(): List<String> =
        this[RFC9901.CLAIM_SD]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun JsonArray.arrayDigests(): List<String> =
        this.filter { it is JsonObject && it.containsKey(RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST) }
            .map { it.jsonObject[RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST]!!.jsonPrimitive.content }

    private fun SdJwt<SignedJWT>.topLevelDigests(): List<String> =
        jwt.jwtClaimsSet.jsonObject().digests()

    private fun SdJwt<SignedJWT>.digestsOf(name: String): List<String> =
        disclosures.firstNotNullOf {
            val (claimName, value) = it.claim()
            if (claimName == name) {
                when (value) {
                    is JsonObject -> value.digests()
                    is JsonArray -> value.arrayDigests()
                    else -> null
                }
            } else null
        }

    private val key = ECKeyGenerator(Curve.P_521).generate()

    fun issuerWithFallbackMinimumDigests(minimumDigests: Int?): SdJwtIssuer<SignedJWT> =
        NimbusSdJwtOps.issuer(
            signer = ECDSASigner(key),
            signAlgorithm = JWSAlgorithm.ES512,
            sdJwtFactory = SdJwtFactory(fallbackMinimumDigests = minimumDigests?.let { MinimumDigests(it) }),
        )

    private fun assertMinimumDigests(
        minimumDigests: Int,
        digestsNo: Int,
    ) {
        assertTrue("Expecting at least $minimumDigests found $digestsNo") { minimumDigests <= digestsNo }
    }

    private suspend fun testMinimumDigestsAtTopLevel(
        issuer: SdJwtIssuer<SignedJWT>,
        minimumDigests: Int,
        spec: SdJwtObject,
    ): SdJwt<SignedJWT> {
        val sdJwt = issuer.issue(spec).getOrThrow()
        sdJwt.prettyPrint { it.jwtClaimsSet.jsonObject() }
        val digests = sdJwt.topLevelDigests()
        assertMinimumDigests(minimumDigests, digests.size)
        return sdJwt
    }

    private suspend fun checkDigestsForNestedElement(
        spec: SdJwtObject,
        elementName: String,
        fallbackMinimumDigests: Int?,
        nestedElementMinimumDigests: Int?,
    ) {
        val issuer = issuerWithFallbackMinimumDigests(fallbackMinimumDigests)
        val expectedTopLevelDigests = fallbackMinimumDigests ?: 0
        val sdJwt = testMinimumDigestsAtTopLevel(issuer, expectedTopLevelDigests, spec)
        checkDigestsForNestedElement(sdJwt, elementName, expectedTopLevelDigests, nestedElementMinimumDigests)
    }

    private fun checkDigestsForNestedElement(
        sdJwt: SdJwt<SignedJWT>,
        elementName: String,
        expectedTopLevelDigests: Int,
        nestedElementMinimumDigests: Int?,
    ) {
        val digests = sdJwt.digestsOf(elementName)
        val expectedNestedDigests = nestedElementMinimumDigests ?: expectedTopLevelDigests
        assertMinimumDigests(expectedNestedDigests, digests.size)
    }

    @Test
    fun checkFallbackMinimumDigestsAtNestedSdObj() = runTest {
        // This spec contains a nested object with no explicitly defined minimum digests
        // fallback defined minimum digest should appear
        val spec = sdJwt {
            sdObjClaim("someObj") {
                sdClaim("foo", "bar")
                sdClaim("baz", "qux")
                sdClaim("quux", "corge")
            }
        }
        checkDigestsForNestedElement(
            spec = spec,
            elementName = "someObj",
            fallbackMinimumDigests = 6,
            nestedElementMinimumDigests = null,
        )
    }

    @Test
    fun checkExplicitMinimumDigestsAtNestedSdObj() = runTest {
        // This spec contains a nested object claim which explicitly defines minimum digests
        // fallback minimum digests should be ignored, for the object
        val nestedElementMinimumDigests = 6
        val spec = sdJwt {
            sdObjClaim("someObj", nestedElementMinimumDigests) {
                sdClaim("foo", "bar")
                sdClaim("baz", "qux")
                sdClaim("quux", "corge")
            }
        }
        checkDigestsForNestedElement(
            spec = spec,
            elementName = "someObj",
            fallbackMinimumDigests = nestedElementMinimumDigests + 1,
            nestedElementMinimumDigests = nestedElementMinimumDigests,
        )
    }

    @Test
    fun checkExplicitMinimumDigestsAtNestedSdArray() = runTest {
        // This spec contains a nested array claim which explicitly defines minimum digests
        // fallback minimum digests should be ignored, for the array
        val nestedElementMinimumDigests = 6
        val spec = sdJwt {
            sdArrClaim("someArr", nestedElementMinimumDigests) {
                sdClaim("foo")
                claim(1213)
                sdClaim("bar")
                sdClaim("baz")
            }
        }
        checkDigestsForNestedElement(
            spec = spec,
            elementName = "someArr",
            fallbackMinimumDigests = nestedElementMinimumDigests + 2,
            nestedElementMinimumDigests = nestedElementMinimumDigests,
        )
    }

    @Test
    fun checkFallbackMinimumDigestsAtNestedSdArray() = runTest {
        // This spec contains a nested array claim which doesn't explicitly define minimum digests
        // it should inherit the implicit minimum digests from the parent SD-JWT
        val spec = sdJwt {
            sdArrClaim("someArr") {
                sdClaim("foo")
                claim(1213)
                sdClaim("bar")
                sdClaim("baz")
            }
        }
        checkDigestsForNestedElement(
            spec = spec,
            elementName = "someArr",
            fallbackMinimumDigests = 10,
            nestedElementMinimumDigests = null,
        )
    }

    @Test
    fun moreComplexExample() = runTest {
        val topLevelMinimumDigests = 10
        val arrayMinDigests = 8
        val objMinDigests = 6
        val spec = sdJwt(topLevelMinimumDigests) {
            sdArrClaim("someArr", arrayMinDigests) {
                sdClaim("foo")
                sdObjClaim(objMinDigests) {
                    claim(
                        "marker",
                        buildJsonObject {
                            put("a", "value")
                        },
                    )
                    sdClaim("foo", "bar")
                    sdClaim("baz", "qux")
                    sdClaim("quux", "corge")
                }
                sdClaim("bar")
                sdClaim("baz")
            }
        }
        val sdJwt = run {
            val issuer = issuerWithFallbackMinimumDigests(null)
            testMinimumDigestsAtTopLevel(issuer, topLevelMinimumDigests, spec)
        }
        checkDigestsForNestedElement(sdJwt, "someArr", topLevelMinimumDigests, arrayMinDigests)
        val obj = sdJwt.disclosures.firstNotNullOf {
            if (it is Disclosure.ArrayElement) {
                val (_, v) = it.claim()
                if (v is JsonObject && "marker" in v.keys) v else null
            } else null
        }
        assertMinimumDigests(objMinDigests, obj.digests().size)
    }
}
