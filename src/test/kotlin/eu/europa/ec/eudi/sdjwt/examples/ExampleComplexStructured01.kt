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
        claim("iss", "https://issuer.example.com")
        claim("iat", 1683000000)
        claim("exp", 1883000000)
        objClaim("verified_claims") {
            objClaim("verification") {
                sdClaim("time", "2012-04-23T18:25Z")
                sdClaim("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                claim("trust_framework", "de_aml")
                arrClaim("evidence") {
                    sdObjClaim {
                        sdClaim("type", "document")
                        sdClaim("method", "pipp")
                        sdClaim("time", "2012-04-22T11:30Z")
                        sdObjClaim("document") {
                            claim("type", "idcard")
                            objClaim("issuer") {
                                claim("name", "Stadt Augsburg")
                                claim("country", "DE")
                            }
                            claim("number", "53554554")
                            claim("date_of_issuance", "2010-03-23")
                            claim("date_of_expiry", "2020-03-22")
                        }
                    }
                }
            }
            objClaim("claims") {
                sdClaim("given_name", "Max")
                sdClaim("family_name", "Müller")
                sdArrClaim("nationalities") {
                    claim("DE")
                }
                sdClaim("birthdate", "1956-01-28")
                sdObjClaim("place_of_birth") {
                    claim("country", "IS")
                    claim("locality", "Þykkvabæjarklaustur")
                }
                sdObjClaim("address") {
                    claim("locality", "Maxstadt")
                    claim("postal_code", "12344")
                    claim("country", "DE")
                    claim("street_address", "Weidenstraße 22")
                }
            }
            sdClaim("birth_middle_name", "Timotheus")
            sdClaim("salutation", "Dr.")
            sdClaim("msisdn", "49123456789")
        }
    }
