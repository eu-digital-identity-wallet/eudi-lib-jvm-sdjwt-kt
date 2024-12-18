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
package eu.europa.ec.eudi.sdjwt.examples

import eu.europa.ec.eudi.sdjwt.*
import kotlin.test.*

class ExampleReadMeTest01 {

    @Test
    fun testExampleIssueSdJw01() {
        println(issuedSdJwt)
    }

    @Test
    fun testExampleIssuanceSdJwtVerification01() {
        verifiedIssuanceSdJwt.prettyPrint()
    }

    @Test
    fun testExamplePresentationSdJwt01() {
        assertEquals(3, presentationSdJwt.disclosures.size)
    }

    @Test
    fun testExamplePresentationSdJwtVerification01() {
        verifiedPresentationSdJwt.prettyPrint()
    }

    @Test
    fun testExampleRecreateClaims01() {
        println(claims)
    }

    @Test
    fun testExampleSdJwtWithMinimumDigest01() {
        println(sdJwtWithMinimumDigests)
    }

    @Test
    fun testExampleSdJwtVcVerification01() {
        sdJwtVcVerification.getOrThrow()
    }
}
