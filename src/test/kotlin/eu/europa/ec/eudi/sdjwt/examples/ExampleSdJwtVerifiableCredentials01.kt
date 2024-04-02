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
        iss("https://issuer.example.com")
        iat(1683000000)
        exp(1883000000)

        plain {
            put("vct", "https://bmi.bund.example/credential/pid/1.0")
            putJsonObject("cnf") {
                putJsonObject("jwk") {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                    put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
                }
            }
        }

        sd {
            put("given_name", "Erika")
            put("family_name", "Mustermann")
            put("birthdate", "1963-08-12")
            put("source_document_type", "id_card")
            putJsonArray("nationalities") {
                add("DE")
            }
            put("gender", "female")
            put("birth_family_name", "Gabler")
            put("also_known_as", "Schwester")
        }

        recursive("address") {
            sd {
                put("street_address", "Heidestraße 17")
                put("locality", "Köln")
                put("postal_code", "51147")
                put("country", "DE")
            }
        }

        recursive("place_of_birth") {
            plain {
                put("country", "DE")
            }
            sd {
                put("locality", "Berlin")
            }
        }

        structured("age_equal_or_over") {
            sd {
                put("12", true)
                put("14", true)
                put("16", true)
                put("18", true)
                put("21", true)
                put("65", false)
            }
        }
    }
