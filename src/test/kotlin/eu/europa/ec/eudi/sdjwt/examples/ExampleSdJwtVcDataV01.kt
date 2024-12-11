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

val sdJwtVcDataV2 =
    sdJwt {
        plain {
            putJsonArray("@context") {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://w3id.org/vaccination/v1")
            }
            putJsonArray("type") {
                add("VerifiableCredential")
                add("VaccinationCertificate")
            }
            put("issuer", "https://example.com/issuer")
            put("issuanceDate", "2023-02-09T11:01:59Z")
            put("expirationDate", "2028-02-08T11:01:59Z")
            put("name", "COVID-19 Vaccination Certificate")
            put("description", "COVID-19 Vaccination Certificate")
            putJsonObject("cnf") {
                putJsonObject("jwk") {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                    put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
                }
            }
        }

        sd("credentialSubject") {
            plain {
                put("type", "VaccinationEvent")
            }
            sd {
                put("nextVaccinationDate", "2021-08-16T13:40:12Z")
                put("countryOfVaccination", "GE")
                put("dateOfVaccination", "2021-06-23T13:40:12Z")
                put("order", "3/3")
                put("administeringCentre", "Praxis Sommergarten")
                put("batchNumber", "1626382736")
                put("healthProfessional", "883110000015376")
            }
            sd("vaccine") {
                plain {
                    put("type", "Vaccine")
                }
                sd {
                    put("atcCode", "J07BX03")
                    put("medicinalProductName", "COVID-19 Vaccine Moderna")
                    put("marketingAuthorizationHolder", "Moderna Biotech")
                }
            }
            sd("recipient") {
                plain {
                    put("type", "VaccineRecipient")
                }
                sd {
                    put("gender", "Female")
                    put("birthDate", "1961-08-17")
                    put("givenName", "Marion")
                    put("familyName", "Mustermann")
                }
            }
        }
    }
