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

import eu.europa.ec.eudi.sdjwt.vc.DocumentIntegrity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentIntegrityTest {

    @Test
    fun `ensure single valid DocumentIntegrities is being converted correctly to DocumentIntegrity lists`() {
        val singleValid = DocumentIntegrity("sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7")
        val singleValidIntegrity = singleValid.hashes

        assertEquals(1, singleValidIntegrity.size)
    }

    @Test
    fun `ensure multiple valid DocumentIntegrities is being converted correctly to DocumentIntegrity lists`() {
        val multipleValid = DocumentIntegrity(
            "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
        )

        assertEquals(2, multipleValid.hashes.size)
    }

    @Test
    fun `ensure unknown algorithm in DocumentIntegrities throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentIntegrity(
                "sha484-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7",
            )
        }
    }

    @Test
    fun `ensure when a known and one unknown algorithm exists, unknown does not pass validation`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentIntegrity(
                "sha484-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO " +
                    "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
            )
        }
    }

    @Test
    fun `ensure that when options are present, they are detected correctly`() {
        val multipleValidWithOptions =
            DocumentIntegrity(
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?extraOptionsReserved " +
                    "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
            )

        assertEquals(2, multipleValidWithOptions.hashes.size)
        assertEquals("H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO", multipleValidWithOptions.hashes[0].encodedHash)
        assertEquals("extraOptionsReserved", multipleValidWithOptions.hashes[0].options)
    }
}
