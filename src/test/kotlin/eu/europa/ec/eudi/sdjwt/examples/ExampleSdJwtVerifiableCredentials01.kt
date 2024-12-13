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
        claim("iss", "https://issuer.example.com")
        claim("iat", 1683000000)
        claim("exp", 1883000000)

        claim("vct", "https://bmi.bund.example/credential/pid/1.0")

        objClaim("cnf") {
            objClaim("jwk") {
                claim("kty", "EC")
                claim("crv", "P-256")
                claim("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                claim("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
            }
        }

        sdClaim("given_name", "Erika")
        sdClaim("family_name", "Mustermann")
        sdClaim("birthdate", "1963-08-12")
        sdClaim("source_document_type", "id_card")
        sdArrClaim("nationalities") { claim("DE") }
        sdClaim("gender", "female")
        sdClaim("birth_family_name", "Gabler")
        sdClaim("also_known_as", "Schwester")

        sdObjClaim("address") {
            sdClaim("street_address", "Heidestraße 17")
            sdClaim("locality", "Köln")
            sdClaim("postal_code", "51147")
            sdClaim("country", "DE")
        }

        sdObjClaim("place_of_birth") {
            claim("country", "DE")
            sdClaim("locality", "Berlin")
        }

        objClaim("age_equal_or_over") {
            sdClaim("12", true)
            sdClaim("14", true)
            sdClaim("16", true)
            sdClaim("18", true)
            sdClaim("21", true)
            sdClaim("65", false)
        }
    }
