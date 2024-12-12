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

val sdJwtVcDataV2 =
    sdJwt {
        notSdArray("@context") {
            notSd("https://www.w3.org/2018/credentials/v1")
            notSd("https://w3id.org/vaccination/v1")
        }
        notSdArray("type") {
            notSd("VerifiableCredential")
            notSd("VaccinationCertificate")
        }
        notSd("issuer", "https://example.com/issuer")
        notSd("issuanceDate", "2023-02-09T11:01:59Z")
        notSd("expirationDate", "2028-02-08T11:01:59Z")
        notSd("name", "COVID-19 Vaccination Certificate")
        notSd("description", "COVID-19 Vaccination Certificate")
        notSdObject("cnf") {
            notSdObject("jwk") {
                notSd("kty", "EC")
                notSd("crv", "P-256")
                notSd("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                notSd("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
            }
        }

        notSdObject("credentialSubject") {
            notSd("type", "VaccinationEvent")
            sd("nextVaccinationDate", "2021-08-16T13:40:12Z")
            sd("countryOfVaccination", "GE")
            sd("dateOfVaccination", "2021-06-23T13:40:12Z")
            sd("order", "3/3")
            sd("administeringCentre", "Praxis Sommergarten")
            sd("batchNumber", "1626382736")
            sd("healthProfessional", "883110000015376")
            notSdObject("vaccine") {
                notSd("type", "Vaccine")
                sd("atcCode", "J07BX03")
                sd("medicinalProductName", "COVID-19 Vaccine Moderna")
                sd("marketingAuthorizationHolder", "Moderna Biotech")
            }
            notSdObject("recipient") {
                notSd("type", "VaccineRecipient")
                sd("gender", "Female")
                sd("birthDate", "1961-08-17")
                sd("givenName", "Marion")
                sd("familyName", "Mustermann")
            }
        }
    }
