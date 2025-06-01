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

import eu.europa.ec.eudi.sdjwt.Disclosure
import eu.europa.ec.eudi.sdjwt.SdJwtFactory
import eu.europa.ec.eudi.sdjwt.UnsignedSdJwt
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.values.SdJwtObject
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.values.sdJwt
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import eu.europa.ec.eudi.sdjwt.vc.DefinitionBasedSdJwtVcValidator
import eu.europa.ec.eudi.sdjwt.vc.DefinitionBasedValidationResult
import eu.europa.ec.eudi.sdjwt.vc.DefinitionViolation
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefinitionBasedSdJwtVcValidatorTest {

    @Test
    fun testCredentialWithNoClaims() = PidDefinition.shouldConsiderValid(
        sdJwtObject = sdJwt {
            // Here we are testing an empty SD-JWT.
            // It should pass the validation, because
            // PidDefinition doesn't contain a top-level
            // Never Selectively Disclosable attribute
            // This means that an empty payload is ok.

            // Of course, this is not according to SD-JWT-VC
            // that requires some claims such as vct to be never selectively disclosable
            // Yet current PidDefinition doesn't have this rule
        },
    )

    @Test
    fun happyPath() = PidDefinition.shouldConsiderValid(
        sdJwtObject =
            sdJwt {
                sdClaim("family_name", "Foo")
            },
    )

    @Test
    fun happyPathNoDisclosure() = PidDefinition.shouldConsiderValid(
        sdJwtObject = sdJwt {
            sdClaim("family_name", "Foo")
        },
        disclosureFilter = { _ -> false },
    )

    @Test
    fun shouldConsiderNullValuesValid() = PidDefinition.shouldConsiderValid(
        sdJwtObject = sdJwt {
            sdClaim("family_name", JsonNull)
            sdClaim("address", JsonNull)
            sdObjClaim("place_of_birth") {
                sdClaim("country", JsonNull)
            }
        },
    )

    @Test
    fun detectIncorrectlyDisclosedAttributes() {
        // [family] is wrongly declared as never selectively disclosed
        // [address] is correctly declared
        // [address][country] is wrongly declared as never selectively disclosed
        val sdJwt = sdJwt {
            claim("family_name", "Foo")
            sdObjClaim("address") {
                claim("country", "Foo")
            }
        }
        val expectedErrors =
            listOf(
                ClaimPath.claim("family_name"),
                ClaimPath.claim("address").claim("country"),
            ).map { DefinitionViolation.IncorrectlyDisclosedClaim(it) }

        val errors = PidDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        assertEquals(expectedErrors, errors)
    }

    @Test
    fun detectIncorrectlyDisclosedAttributesNoDisclosures() {
        // [family] is wrongly declared as never selectively disclosed
        // [address] is correctly declared
        // [address][country] is wrongly declared as never selectively disclosed
        val sdJwt = sdJwt {
            claim("family_name", "Foo")
            sdObjClaim("address") {
                claim("country", "Foo")
            }
        }

        // Since we removed all disclosures
        // when can detect only errors related to Never Selectively Disclosable attributes
        val expectedErrors =
            listOf(
                ClaimPath.claim("family_name"),
            ).map { DefinitionViolation.IncorrectlyDisclosedClaim(it) }

        val errors = PidDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt, disclosureFilter = { _ -> false })
        assertEquals(expectedErrors, errors)
    }

    @Test
    fun detectUnknownObjectAttributes() {
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
        val errors = PidDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        val expectedErrors = listOf(
            "unknownNeverSelectivelyDisclosed",
            "unknownAlwaysSelectivelyDisclosed",
            "unknownNeverSelectivelyDisclosedArr",
            "unknownAlwaysSelectivelyDisclosedArr",
            "unknownNeverSelectivelyDisclosedObj",
            "unknownAlwaysSelectivelyDisclosedObj",
        ).map { DefinitionViolation.UnknownClaim(ClaimPath.claim(it)) }

        assertEquals(expectedErrors.size, errors.size)
        expectedErrors.forEach { expectedError ->
            assertEquals(expectedError, errors.firstOrNull { it == expectedError })
        }
    }

    @Test
    fun detectUnknownObjectAttributeNested() {
        // Here we are created a SD-JWT which contains
        // an object attribute `address`
        // which contains an unknown claim
        val sdJwt = sdJwt {
            sdObjClaim("address") {
                claim("foo", "bar")
            }
        }
        val errors = PidDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        val expectedError = DefinitionViolation.UnknownClaim(
            ClaimPath.claim("address").claim("foo"),
        )
        assertEquals(expectedError, errors.first())
    }

    @Test
    fun detectWrongAttributeType() {
        val sdJwt = sdJwt {
            sdClaim("nationalities", "GR") // nationalities  is defined as array
            sdClaim("place_of_birth", "foo") // place_of_birth is defined as an obj
        }

        val expectedErrors = listOf(
            ClaimPath.claim("nationalities"),
            ClaimPath.claim("place_of_birth"),
        ).map { DefinitionViolation.WrongClaimType(it) }

        val errors = PidDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        errors.forEach { expectedError -> println(expectedError) }

        assertEquals(expectedErrors.size, errors.size)
        expectedErrors.forEach { expectedError ->
            assertEquals(expectedError, errors.firstOrNull { it == expectedError })
        }
    }

    /**
     * Although the SD-JWT contains some obvious errors, [SdJwtDefinition] cannot detect them.
     * That's actually a limitation of SD-JWT-VC type metadata, which uses JsonSchema
     * for such validations
     */
    @Test
    fun limitationsOfTypeMetadata() = PidDefinition.shouldConsiderValid(
        sdJwtObject =
            sdJwt {
                sdArrClaim("family_name") { sdClaim("foo") } // family_name is defined a claim not an array
                sdObjClaim("address") {
                    sdArrClaim("locality") {} // not an array
                    sdObjClaim("country") {} // not an object
                }
            },
    )

    @Test
    fun arrayTestHappyPath() = AddressDefinition.shouldConsiderValid(
        sdJwtObject = sdJwt {
            sdArrClaim("addresses") {
                sdObjClaim {
                    sdClaim("country", "country0")
                }
                sdObjClaim {
                    sdClaim("country", "country1")
                }
                sdObjClaim {
                    sdClaim("country", "country2")
                }
            }
        },
    )

    @Test
    fun arrayShouldConsiderNullValuesValid() = AddressDefinition.shouldConsiderValid(
        sdJwtObject = sdJwt {
            sdArrClaim("addresses") {
                sdClaim(JsonNull)
                sdClaim(JsonNull)
                sdClaim(JsonNull)
            }
        },
    )

    @Test
    fun detectIncorrectlyDisclosedClaimInsideArray() {
        val sdJwt = sdJwt {
            sdArrClaim("addresses") {
                objClaim {
                    sdClaim("country", "country0")
                }
                sdObjClaim {
                    sdClaim("country", "country1")
                }
                objClaim {
                    sdClaim("country", "country2")
                }
            }
        }

        val errors = AddressDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        val expectedErrors =
            listOf(
                ClaimPath.claim("addresses").arrayElement(0),
                ClaimPath.claim("addresses").arrayElement(2),
            ).map { DefinitionViolation.IncorrectlyDisclosedClaim(it) }
        assertContentEquals(expectedErrors, errors)
    }

    @Test
    fun detectUnknownClaimsInArray() {
        val sdJwt = sdJwt {
            sdArrClaim("addresses") {
                sdObjClaim {
                    sdClaim("county", "county1")
                }
                sdObjClaim {
                    sdClaim("locale", "locale1")
                }
            }
        }

        val errors = AddressDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        val expectedErrors =
            listOf(
                ClaimPath.claim("addresses").arrayElement(0).claim("county"),
                ClaimPath.claim("addresses").arrayElement(1).claim("locale"),
            ).map { DefinitionViolation.UnknownClaim(it) }
        assertContentEquals(expectedErrors, errors)
    }

    @Test
    fun detectIncorrectTypeInsideArray() {
        val sdJwt = sdJwt {
            sdArrClaim("addresses") {
                sdArrClaim {
                    sdClaim("country0")
                }
                sdObjClaim {
                    sdClaim("country", "country1")
                }
                sdObjClaim {
                    // dsl limitation. won't be detected because country is Id
                    sdObjClaim("country") {
                        sdClaim("country", "country2")
                    }
                }
                sdClaim("country3")
            }
        }

        val errors = AddressDefinition.shouldConsiderInvalid(sdJwtObject = sdJwt)
        val expectedErrors =
            listOf(
                ClaimPath.claim("addresses").arrayElement(0),
                ClaimPath.claim("addresses").arrayElement(3),
            ).map { DefinitionViolation.WrongClaimType(it) }
        assertContentEquals(expectedErrors, errors)
    }

    private fun SdJwtDefinition.shouldConsiderInvalid(
        sdJwtObject: SdJwtObject,
        disclosureFilter: (Disclosure) -> Boolean = { true },
    ): List<DefinitionViolation> {
        val result = createAndValidate(this, sdJwtObject, disclosureFilter)
        return assertIs<DefinitionBasedValidationResult.Invalid>(result).errors
    }

    private fun SdJwtDefinition.shouldConsiderValid(
        sdJwtObject: SdJwtObject,
        disclosureFilter: (Disclosure) -> Boolean = { true },
    ) {
        val result = createAndValidate(this, sdJwtObject, disclosureFilter)
        assertIs<DefinitionBasedValidationResult.Valid>(result)
    }

    private fun createAndValidate(

        sdJwtDefinition: SdJwtDefinition,
        sdJwtObject: SdJwtObject,
        disclosureFilter: (Disclosure) -> Boolean,
    ): DefinitionBasedValidationResult {
        val (payload, disclosures) = createSdJwt(sdJwtObject)
        return with(DefinitionBasedSdJwtVcValidator) {
            sdJwtDefinition.validate(payload, disclosures.filter(disclosureFilter))
        }
    }

    private fun createSdJwt(sdJwtObject: SdJwtObject): UnsignedSdJwt {
        return SdJwtFactory.Default.createSdJwt(sdJwtObject).getOrThrow()
    }
}
