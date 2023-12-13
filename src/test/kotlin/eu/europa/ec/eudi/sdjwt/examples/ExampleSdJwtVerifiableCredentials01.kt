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

val sdJwtVc =
    sdJwt {
        iss("https://pid-provider.memberstate.example.eu")
        iat(1541493724)
        exp(1883000000)

        plain {
            put("type", "PersonIdentificationData")
        }

        sd {
            put("first_name", "Erika")
            put("family_name", "Mustermann")
            put("birth_family_name", "Schmidt")
            put("birthdate", "1973-01-01")

            putJsonObject("address") {
                put("postal_code", "12345")
                put("locality", "Irgendwo")
                put("street_address", "Sonnenstrasse 23")
                put("country_code", "DE")
            }

            put("is_over_18", true)
            put("is_over_21", true)
            put("is_over_65", false)
        }

        recursiveArray("nationalities") {
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
