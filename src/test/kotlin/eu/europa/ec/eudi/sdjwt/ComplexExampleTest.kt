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
            notSd("plainClaim", "value")

            // sd claim in object
            sd("sdClaim", "value")

            // plain array in object
            notSdArray("plainArray") {
                // plain value in plain array
                notSd("plain")

                // sd value in plain array
                sd("sd")

                // plain object in plain array, can also contain other arrays or objects
                notSdObject {
                    notSd("plainClaim", "value")
                    sd("sdClaim", "value")
                }

                // sd object in plain array, can also contain other arrays or objects
                sdObject {
                    notSd("plainClaim", "value")
                    sd("sdClaim", "value")
                }

                // plain array in plain array, can also contain other arrays or objects
                notSdArray {
                    notSd("plain")
                    sd("sd")
                }

                // sd array in plain array, can also contain other arrays or objects
                sdArray {
                    notSd("plain")
                    sd("sd")
                }
            }

            // sd array in object
            sdArray("sdArray") {
                // plain value in sd array
                notSd("plain")

                // sd value in sd array
                sd("sd")

                // plain object in sd array, can also contain other arrays or objects
                notSdObject {
                    notSd("plainClaim", "value")
                    sd("sdClaim", "value")
                }

                // sd object in sd array, can also contain other arrays or objects
                sdObject {
                    notSd("plainClaim", "value")
                    sd("sdClaim", "value")
                }

                // plain array in sd array, can also contain other arrays or objects
                notSdArray {
                    notSd("plain")
                    sd("sd")
                }

                // sd array in sd array, can also contain other arrays or objects
                sdArray {
                    notSd("plain")
                    sd("sd")
                }
            }

            // plain object in object
            notSdObject("plainObject") {
                // plain value in plain object
                notSd("plainClaim", "value")

                // sd value in plain object
                sd("sdClaim", "value")

                // plain array in plain object, can also contain other arrays or objects
                notSdArray("plainArray") {
                    notSd("plain")
                    sd("sd")
                }

                // sd value in plain object, can also contain other arrays or objects
                sdArray("sdArray") {
                    notSd("plain")
                    sd("sd")
                }
            }

            // sd object in object
            sdObject("sdObject") {
                // plain value in sd object
                notSd("plainClaim", "value")

                // sd value in sd object
                sd("sdClaim", "value")

                // plain array in sd object, can also contain other arrays or objects
                notSdArray("plainArray") {
                    notSd("plain")
                    sd("sd")
                }

                // sd array in sd object, can also contain other arrays or objects
                sdArray("sdArray") {
                    notSd("plain")
                    sd("sd")
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
