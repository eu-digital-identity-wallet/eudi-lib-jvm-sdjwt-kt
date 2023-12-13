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
import kotlinx.serialization.json.*

val flatSdJwt =
    sdJwt {
        iss("https://issuer.example.com")
        iat(1683000000)
        exp(1883000000)
        sub("user_42")

        sd {
            put("given_name", "John")
            put("family_name", "Doe")
            put("email", "johndoe@example.com")
            put("phone_number", "+1-202-555-0101")
            put("phone_number_verified", true)
            putJsonObject("address") {
                put("street_address", "123 Main St")
                put("locality", "Anytown")
                put("region", "Anystate")
                put("country", "US")
            }
            put("birthdate", "1940-01-01")
            put("updated_at", 1570000000)
        }

        sdArray("nationalities") {
            sd("US")
            sd("DE")
        }

        plain {
            putJsonObject("cnf") {
                putJsonObject("jwk") {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                    put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
                }
            }
        }
    }
