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
        arrClaim("@context") {
            claim("https://www.w3.org/2018/credentials/v1")
            claim("https://w3id.org/vaccination/v1")
        }
        arrClaim("type") {
            claim("VerifiableCredential")
            claim("VaccinationCertificate")
        }
        claim("issuer", "https://example.com/issuer")
        claim("issuanceDate", "2023-02-09T11:01:59Z")
        claim("expirationDate", "2028-02-08T11:01:59Z")
        claim("name", "COVID-19 Vaccination Certificate")
        claim("description", "COVID-19 Vaccination Certificate")
        objClaim("cnf") {
            objClaim("jwk") {
                claim("kty", "EC")
                claim("crv", "P-256")
                claim("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                claim("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
            }
        }

        objClaim("credentialSubject") {
            claim("type", "VaccinationEvent")
            sdClaim("nextVaccinationDate", "2021-08-16T13:40:12Z")
            sdClaim("countryOfVaccination", "GE")
            sdClaim("dateOfVaccination", "2021-06-23T13:40:12Z")
            sdClaim("order", "3/3")
            sdClaim("administeringCentre", "Praxis Sommergarten")
            sdClaim("batchNumber", "1626382736")
            sdClaim("healthProfessional", "883110000015376")
            objClaim("vaccine") {
                claim("type", "Vaccine")
                sdClaim("atcCode", "J07BX03")
                sdClaim("medicinalProductName", "COVID-19 Vaccine Moderna")
                sdClaim("marketingAuthorizationHolder", "Moderna Biotech")
            }
            objClaim("recipient") {
                claim("type", "VaccineRecipient")
                sdClaim("gender", "Female")
                sdClaim("birthDate", "1961-08-17")
                sdClaim("givenName", "Marion")
                sdClaim("familyName", "Mustermann")
            }
        }
    }
