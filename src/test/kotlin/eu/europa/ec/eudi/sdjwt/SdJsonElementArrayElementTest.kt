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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class SdJsonElementArrayElementTest {
    @Test
    fun simple() {
        val sdJwtElements = sdJwt {
            claim("sub", "user_42")
            claim("iss", "https://example.com/issuer")
            claim("iat", 1683000000)
            claim("exp", 1883000000)
            sdClaim("given_name", "John")
            sdClaim("family_name", "Doe")
            sdClaim("email", "johndoe@example.com")
            sdClaim("phone_number", "+1-202-555-0101")
            sdClaim("phone_number_verified", true)
            sdObjClaim("address") {
                claim("street_address", "123 Main St")
                claim("locality", "Anytown")
                claim("region", "Anystate")
                claim("country", "US")
            }
            sdClaim("birthdate", "1940-01-01")
            sdClaim("updated_at", 1570000000)
            arrClaim("nationalities") {
                claim("US")
                sdClaim("DE")
            }
        }

        val sdJwt = SdJwtFactory().createSdJwt(sdJwtElements).getOrThrow().also {
            println(json.encodeToString(it.jwt))
        }

        with(SdJwtRecreateClaimsOps { claims: JsonObject -> claims }) {
            sdJwt.recreateClaims(visitor = null)
        }
    }
}
