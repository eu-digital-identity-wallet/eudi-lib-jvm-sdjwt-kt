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

import eu.europa.ec.eudi.sdjwt.vc.DocumentIntegrities
import eu.europa.ec.eudi.sdjwt.vc.toDocumentIntegrity
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class DocumentIntegrityTest {

    @Test
    fun `ensure single valid DocumentIntegrities is being converted correctly to DocumentIntegrity lists`() {
        val singleValid = DocumentIntegrities("sha384-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7")
        val singleValidIntegrity = singleValid.toDocumentIntegrity()

        assert(singleValidIntegrity.size == 1)
    }

    @Test
    fun `ensure multiple valid DocumentIntegrities is being converted correctly to DocumentIntegrity lists`() {
        val multipleValid = DocumentIntegrities(
            "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
        )
        val multipleValidIntegrity = multipleValid.toDocumentIntegrity()

        assert(multipleValidIntegrity.size == 2)
    }

    @Test
    fun `ensure unknown algorithm in DocumentIntegrities throws IllegalArgumentException`() {
        val unknownAlgorithm = DocumentIntegrities("sha484-Li9vy3DqF8tnTXuiaAJuML3ky+er10rcgNR/VqsVpcw+ThHmYcwiB1pbOxEbzJr7")

        assertThrows<IllegalArgumentException> { unknownAlgorithm.toDocumentIntegrity() }
    }

    @Test
    fun `ensure when a known and one unknown algorithm exists, keep only the known one`() {
        val knownAndUnknownAlgorithm = DocumentIntegrities(
            "sha484-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO " +
                "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
        )
        val knownAndUnknownAlgorithmIntegrity = knownAndUnknownAlgorithm.toDocumentIntegrity()

        assert(knownAndUnknownAlgorithmIntegrity.size == 1)
    }

    @Test
    fun `ensure that when options are present, they are removed and ignored`() {
        val multipleValidWithOptions =
            DocumentIntegrities(
                "sha384-H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO?asdasdsadsadsad " +
                    "sha512-Q2bFTOhEALkN8hOms2FKTDLy7eugP2zFZ1T8LCvX42Fp3WoNr3bjZSAHeOsHrbV1Fu9/A0EzCinRE7Af1ofPrw==",
            )
        val multipleValidWithOptionsIntegrity = multipleValidWithOptions.toDocumentIntegrity()

        assert(
            multipleValidWithOptionsIntegrity.size == 2 &&
                multipleValidWithOptionsIntegrity[0].value == "H8BRh8j48O9oYatfu5AZzq6A9RINhZO5H16dQZngK7T62em8MUt1FLm52t+eX6xO",
        )
    }
}
