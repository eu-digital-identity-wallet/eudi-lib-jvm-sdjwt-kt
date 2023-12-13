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

val complexStructuredSdJwt =
    sdJwt {
        iss("https://issuer.example.com")
        iat(1683000000)
        exp(1883000000)

        structured("verified_claims") {
            structured("verification") {
                sd {
                    put("time", "2012-04-23T18:25Z")
                    put("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                }
                plain {
                    put("trust_framework", "de_aml")
                }
                sdArray("evidence") {
                    buildSdObject {
                        sd {
                            put("type", "document")
                            put("method", "pipp")
                            put("time", "2012-04-22T11:30Z")
                            putJsonObject("document") {
                                put("type", "idcard")
                                putJsonObject("issuer") {
                                    put("name", "Stadt Augsburg")
                                    put("country", "DE")
                                }
                                put("number", "53554554")
                                put("date_of_issuance", "2010-03-23")
                                put("date_of_expiry", "2020-03-22")
                            }
                        }
                    }
                }
            }
            structured("claims") {
                sd {
                    put("given_name", "Max")
                    put("family_name", "Müller")
                    putJsonArray("nationalities") {
                        add("DE")
                    }
                    put("birthdate", "1956-01-28")
                    putJsonObject("place_of_birth") {
                        put("country", "IS")
                        put("locality", "Þykkvabæjarklaustur")
                    }
                    putJsonObject("address") {
                        put("locality", "Maxstadt")
                        put("postal_code", "12344")
                        put("country", "DE")
                        put("street_address", "Weidenstraße 22")
                    }
                }
            }

            sd {
                put("birth_middle_name", "Timotheus")
                put("salutation", "Dr.")
                put("msisdn", "49123456789")
            }
        }
    }
