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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ComplexExampleTest {
    private val key = ECKeyGenerator(Curve.P_521).generate()
    private val issuer = NimbusSdJwtOps.issuer(signer = ECDSASigner(key), signAlgorithm = JWSAlgorithm.ES512)

    @Test
    internal fun `verify disclosures using the new dsl`() = runTest {
        val spec = sdJwt {
            // plain claim in object
            claim("plainClaim", "value")

            // sd claim in object
            sdClaim("sdClaim", "value")

            // plain array in object
            arrClaim("plainArray") {
                // plain value in plain array
                claim("plain")

                // sd value in plain array
                sdClaim("sd")

                // plain object in plain array, can also contain other arrays or objects
                objClaim {
                    claim("plainClaim", "value")
                    sdClaim("sdClaim", "value")
                }

                // sd object in plain array, can also contain other arrays or objects
                sdObjClaim {
                    claim("plainClaim", "value")
                    sdClaim("sdClaim", "value")
                }

                // plain array in plain array, can also contain other arrays or objects
                arrClaim {
                    claim("plain")
                    sdClaim("sd")
                }

                // sd array in plain array, can also contain other arrays or objects
                sdArrClaim {
                    claim("plain")
                    sdClaim("sd")
                }
            }

            // sd array in object
            sdArrClaim("sdArray") {
                // plain value in sd array
                claim("plain")

                // sd value in sd array
                sdClaim("sd")

                // plain object in sd array, can also contain other arrays or objects
                objClaim {
                    claim("plainClaim", "value")
                    sdClaim("sdClaim", "value")
                }

                // sd object in sd array, can also contain other arrays or objects
                sdObjClaim {
                    claim("plainClaim", "value")
                    sdClaim("sdClaim", "value")
                }

                // plain array in sd array, can also contain other arrays or objects
                arrClaim {
                    claim("plain")
                    sdClaim("sd")
                }

                // sd array in sd array, can also contain other arrays or objects
                sdArrClaim {
                    claim("plain")
                    sdClaim("sd")
                }
            }

            // plain object in object
            objClaim("plainObject") {
                // plain value in plain object
                claim("plainClaim", "value")

                // sd value in plain object
                sdClaim("sdClaim", "value")

                // plain array in plain object, can also contain other arrays or objects
                arrClaim("plainArray") {
                    claim("plain")
                    sdClaim("sd")
                }

                // sd value in plain object, can also contain other arrays or objects
                sdArrClaim("sdArray") {
                    claim("plain")
                    sdClaim("sd")
                }
            }

            // sd object in object
            sdObjClaim("sdObject") {
                // plain value in sd object
                claim("plainClaim", "value")

                // sd value in sd object
                sdClaim("sdClaim", "value")

                // plain array in sd object, can also contain other arrays or objects
                arrClaim("plainArray") {
                    claim("plain")
                    sdClaim("sd")
                }

                // sd array in sd object, can also contain other arrays or objects
                sdArrClaim("sdArray") {
                    claim("plain")
                    sdClaim("sd")
                }
            }
        }

        val sdJwt = issuer.issue(sdJwtSpec = spec).getOrThrow()
        assertEquals(25, sdJwt.disclosures.size)

        sdJwt.prettyPrint { it.jwtClaimsSet.jsonObject() }
        val recreated = with(NimbusSdJwtOps) {
            sdJwt.recreateClaimsAndDisclosuresPerClaim().first
        }
        println(Json.encodeToString(recreated))
    }
}
