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
package eu.europa.ec.eudi.sdjwt.dsl.def

import eu.europa.ec.eudi.sdjwt.SdJwtFactory
import eu.europa.ec.eudi.sdjwt.UnsignedSdJwt
import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.SdJwtObject
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.AttributeMetadata
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.sdJwt
import eu.europa.ec.eudi.sdjwt.vc.SdJwtDefinitionCredentialValidationError
import eu.europa.ec.eudi.sdjwt.vc.SdJwtDefinitionValidationResult
import eu.europa.ec.eudi.sdjwt.vc.validateCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SdJwtVcDefinitionValidatorTest {

    @Test
    fun test01() = PidDefinition.shouldConsiderValid {
        sdJwt {
            sdClaim("family_name", "Foo")
        }
    }

    @Test
    fun testCredentialWithNoClaims() = PidDefinition.shouldConsiderValid {
        sdJwt {
            // Here we are testing an empty SD-JWT.
            // It should pass the validation, because
            // PidDefinition doesn't contain an top-level
            // Never Selectively Disclosable attribute
            // This means that an empty payload is ok.

            // Of course, this is not according to SD-JWT-VC
            // that requires some claims such as vct to be never selectively disclosable
            // Yet current PidDefinition doesn't have this rule
        }
    }

    @Test
    fun test03() {
        // Here we are created a SD-JWT which contains `family_name` as never selectively disclosable
        // attribute.
        // Whereas PidDefinition requires this claim to be always selectively disclosable
        val sdJwt = sdJwt { claim("family_name", "Foo") }
        val errors = PidDefinition.shouldConsiderInvalid(sdJwt)
        val expectedError =
            SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(
                PidDefinition,
                "family_name",

            )
        assertEquals(expectedError, errors.first())
    }

    @Test
    fun test04() {
        // Here we are created a SD-JWT which contains an attribute which is not part
        // of the definition
        val sdJwt = sdJwt {
            claim("unknownNeverSelectivelyDisclosed", "bar")
            sdClaim("unknownAlwaysSelectivelyDisclosed", "y")
            arrClaim("unknownNeverSelectivelyDisclosedArr") {
                sdClaim("x")
            }
            sdArrClaim("unknownAlwaysSelectivelyDisclosedArr") {
                claim("foo")
                sdClaim("bar")
            }
            objClaim("unknownNeverSelectivelyDisclosedObj") {
                sdClaim("foo", 1)
            }
            sdObjClaim("unknownAlwaysSelectivelyDisclosedObj") {
                sdClaim("i_am_ok", true)
            }
        }
        val errors = PidDefinition.shouldConsiderInvalid(sdJwt)
        val expectedErrors = listOf(
            "unknownNeverSelectivelyDisclosed",
            "unknownAlwaysSelectivelyDisclosed",
            "unknownNeverSelectivelyDisclosedArr",
            "unknownAlwaysSelectivelyDisclosedArr",
            "unknownNeverSelectivelyDisclosedObj",
            "unknownAlwaysSelectivelyDisclosedObj",
        ).map { SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(PidDefinition, it) }

        assertEquals(expectedErrors.size, errors.size)
        expectedErrors.forEach { expectedError ->
            assertEquals(expectedError, errors.firstOrNull { it == expectedError })
        }
    }

    @Test
    fun test06() {
        // Here we are created a SD-JWT which contains
        // an object attribute `address`
        // which contains an unknown
        val sdJwt = sdJwt {
            sdObjClaim("address") {
                claim("foo", "bar")
            }
        }
        val errors = PidDefinition.shouldConsiderInvalid(sdJwt)
        val (_, addressDefinition) = run {
            val de = PidDefinition.content["address"]
            checkNotNull(de)
            val dv = de.value
            check(dv is DisclosableValue.Obj<String, AttributeMetadata>)
            val sd = de is Disclosable.AlwaysSelectively<*>
            sd to dv.value
        }

        val expectedError =
            SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(
                addressDefinition,
                "foo",

            )
        assertEquals(expectedError, errors.first())
    }

    private fun SdJwtDefinition.shouldConsiderInvalid(sdJwtObject: SdJwtObject): List<SdJwtDefinitionCredentialValidationError> {
        val result = createAndValidate(this, sdJwtObject)
        return assertIs<SdJwtDefinitionValidationResult.Invalid>(result).errors
    }

    private fun SdJwtDefinition.shouldConsiderValid(sdJwtObject: () -> SdJwtObject) {
        val result = createAndValidate(this, sdJwtObject())
        assertIs<SdJwtDefinitionValidationResult.Valid>(result)
    }

    private fun createAndValidate(
        sdJwtDefinition: SdJwtDefinition,
        sdJwtObject: SdJwtObject,
    ): SdJwtDefinitionValidationResult {
        val (payload, disclosures) = createSdJwt(sdJwtObject)
        return sdJwtDefinition.validateCredential(payload, disclosures)
    }

    private fun createSdJwt(sdJwtObject: SdJwtObject): UnsignedSdJwt {
        return SdJwtFactory.Default.createSdJwt(sdJwtObject).getOrThrow()
    }
}
