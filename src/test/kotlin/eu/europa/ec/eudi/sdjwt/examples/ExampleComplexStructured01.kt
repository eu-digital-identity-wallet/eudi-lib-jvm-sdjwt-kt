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

val complexStructuredSdJwt =
    sdJwt {
        iss("https://issuer.example.com")
        iat(1683000000)
        exp(1883000000)
        plain("verified_claims") {
            plain("verification") {
                sd("time", "2012-04-23T18:25Z")
                sd("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                plain("trust_framework", "de_aml")
                plainArray("evidence") {
                    plainObject {
                        sd("type", "document")
                        sd("method", "pipp")
                        sd("time", "2012-04-22T11:30Z")
                        sd("document") {
                            plain("type", "idcard")
                            plain("issuer") {
                                plain("name", "Stadt Augsburg")
                                plain("country", "DE")
                            }
                            plain("number", "53554554")
                            plain("date_of_issuance", "2010-03-23")
                            plain("date_of_expiry", "2020-03-22")
                        }
                    }
                }
            }
            plain("claims") {
                sd("given_name", "Max")
                sd("family_name", "Müller")
                sd_Array("nationalities") {
                    plain("DE")
                }
                sd("birthdate", "1956-01-28")
                sd("place_of_birth") {
                    plain("country", "IS")
                    plain("locality", "Þykkvabæjarklaustur")
                }
                sd("address") {
                    plain("locality", "Maxstadt")
                    plain("postal_code", "12344")
                    plain("country", "DE")
                    plain("street_address", "Weidenstraße 22")
                }

            }
            sd("birth_middle_name", "Timotheus")
            sd("salutation", "Dr.")
            sd("msisdn", "49123456789")
        }
    }
