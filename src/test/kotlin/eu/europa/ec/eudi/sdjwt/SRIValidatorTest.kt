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

import eu.europa.ec.eudi.sdjwt.vc.DocumentIntegrity
import eu.europa.ec.eudi.sdjwt.vc.SRIValidator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SRIValidatorTest {

    @Test
    fun `ensure that hash of content to validate exists in strongest provided hashes`() {
        val singleValid = DocumentIntegrity(
            "sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7 " +
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?asdasdsadsadsad " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw== " +
                "sha512-tLL38NKkjSrUzPZcxdw2Cje4pvsXFicllTGy7hgenGSdRfaU7jSVqscGaV9OjUq6UmeHJXyoPYrCiwQcR3r5uw==",

        )
        val validationResult = SRIValidator().isValid(singleValid, "asdasdas".toByteArray(Charsets.UTF_8))

        assertTrue(validationResult)
    }

    @Test
    fun `hash of content to validate does not exists in strongest provided hashes`() {
        val singleValid = DocumentIntegrity(
            "sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7 " +
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?asdasdsadsadsad " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw== " +
                "sha384-tLL38NKkjSrUzPZcxdw2Cje4pvsXFicllTGy7hgenGSdRfaU7jSVqscGaV9OjUq6UmeHJXyoPYrCiwQcR3r5uw==",

        )
        val validationResult = SRIValidator().isValid(singleValid, "asdasdas".toByteArray(Charsets.UTF_8))

        assertFalse(validationResult)
    }

    @Test
    fun `hash of content to validate does not exists in provided hashes`() {
        val singleValid = DocumentIntegrity(
            "sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7 " +
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?asdasdsadsadsad " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw== ",
        )
        val validationResult = SRIValidator().isValid(singleValid, "asdasdas".toByteArray(Charsets.UTF_8))

        assertFalse(validationResult)
    }

    @Test
    fun `hash of content is hash SHA-512 but exists as SHA-386 in provided hashes`() {
        val singleValid = DocumentIntegrity(
            "sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7 " +
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?asdasdsadsadsad " +
                "sha384-tLL38NKkjSrUzPZcxdw2Cje4pvsXFicllTGy7hgenGSdRfaU7jSVqscGaV9OjUq6UmeHJXyoPYrCiwQcR3r5uw==",

        )
        val validationResult = SRIValidator().isValid(singleValid, "asdasdas".toByteArray(Charsets.UTF_8))

        assertFalse(validationResult)
    }
}
