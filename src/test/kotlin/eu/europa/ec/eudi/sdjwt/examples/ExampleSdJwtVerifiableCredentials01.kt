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

val sdJwtVc =
    sdJwt {
        notSd("iss", "https://issuer.example.com")
        notSd("iat", 1683000000)
        notSd("exp", 1883000000)

        notSd("vct", "https://bmi.bund.example/credential/pid/1.0")

        notSdObject("cnf") {
            notSdObject("jwk") {
                notSd("kty", "EC")
                notSd("crv", "P-256")
                notSd("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                notSd("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
            }
        }

        sd("given_name", "Erika")
        sd("family_name", "Mustermann")
        sd("birthdate", "1963-08-12")
        sd("source_document_type", "id_card")
        sdArray("nationalities") { notSd("DE") }
        sd("gender", "female")
        sd("birth_family_name", "Gabler")
        sd("also_known_as", "Schwester")

        sdObject("address") {
            sd("street_address", "Heidestraße 17")
            sd("locality", "Köln")
            sd("postal_code", "51147")
            sd("country", "DE")
        }

        sdObject("place_of_birth") {
            notSd("country", "DE")
            sd("locality", "Berlin")
        }

        notSdObject("age_equal_or_over") {
            sd("12", true)
            sd("14", true)
            sd("16", true)
            sd("18", true)
            sd("21", true)
            sd("65", false)
        }
    }
