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
            sub("user_42")
            iss("https://example.com/issuer")
            iat(1683000000)
            exp(1883000000)
            sd("given_name", "John")
            sd("family_name", "Doe")
            sd("email", "johndoe@example.com")
            sd("phone_number", "+1-202-555-0101")
            sd("phone_number_verified", true)
            sdObject("address") {
                notSd("street_address", "123 Main St")
                notSd("locality", "Anytown")
                notSd("region", "Anystate")
                notSd("country", "US")
            }
            sd("birthdate", "1940-01-01")
            sd("updated_at", 1570000000)
            notSdArray("nationalities") {
                notSd("US")
                sd("DE")
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
