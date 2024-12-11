<!--- TEST_NAME ExampleSdJwtVcDataV201Test --> 

# Appendix 4 - Example 4b: W3C Verifiable Credentials Data Model v2.0

Description of the example in the [specification Appendix 4 - Example 4b: W3C Verifiable Credentials Data Model v2.0](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-4b-w3c-verifiable-c)

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
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

        plain("credentialSubject") {
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
            plain("vaccine") {
                plain {
                    put("type", "Vaccine")
                }
                sd {
                    put("atcCode", "J07BX03")
                    put("medicinalProductName", "COVID-19 Vaccine Moderna")
                    put("marketingAuthorizationHolder", "Moderna Biotech")
                }
            }
            plain("recipient") {
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
```

Produces

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/vaccination/v1"
  ],
  "type": [
    "VerifiableCredential",
    "VaccinationCertificate"
  ],
  "issuer": "https://example.com/issuer",
  "issuanceDate": "2023-02-09T11:01:59Z",
  "expirationDate": "2028-02-08T11:01:59Z",
  "name": "COVID-19 Vaccination Certificate",
  "description": "COVID-19 Vaccination Certificate",
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
      "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    }
  },
  "credentialSubject": {
    "type": "VaccinationEvent",
    "_sd": [
      "AN8fGQH71sxVicG6kpzMeuC6DUjWYweTbzo2YENbPIw",
      "W7PJCiGTkD6698JubYrfE9Nw0s9s2Qufo_w-rbGglso",
      "3NQAz_q5LUUdLssevda4iAVyHFQXrIr8azIxD1pzVF4",
      "hwSSXnJOxODS4g0272WSRvyfBUaI7gEFb-bgiQE4Pv8",
      "ew2bZj2drxmdWsDLgAgORgAflpO_MjwJ6BqRyyMfAiA",
      "MXrkHIROu1-CUMqgTmpJApw1QKiG-Ev4E3BlMI8dLOs",
      "kmq2TibfEvdQ59sv6rzINltNqvpJfOG1VmLl3gMZseg"
    ],
    "vaccine": {
      "type": "Vaccine",
      "_sd": [
        "jc7VUrPqLJ1uCF3jo0U5eMsMl2sdtjEf2k2J2GML3lo",
        "NnweRuqvhENsc-yYOVa7tBSXnmwn8v4Y3d00cC1M6hI",
        "-aWn6C2F8nsfTQEpMotE0D0YsLN6XRCYwlKazyBGxgk"
      ]
    },
    "recipient": {
      "type": "VaccineRecipient",
      "_sd": [
        "Ck24z45Hi1vUfNt44qKLmS1gnbRqPvmSmyMaW9otpbU",
        "A7jZlA13I-9fbqydsUE1zprrw9GVW17q9JgYgru4VcQ",
        "pTPGEA7YCTuIGF2uEiykOSLE7e9QHtk1-dw6PKd1A_w",
        "YXlycX6aWB5LGdyhdaH-rq_ze34ALbh4FyVwkpuf4n0"
      ]
    }
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...","nextVaccinationDate","2021-08-16T13:40:12Z"],
  ["...salt...","countryOfVaccination","GE"],
  ["...salt...","dateOfVaccination","2021-06-23T13:40:12Z"],
  ["...salt...","order","3/3"],
  ["...salt...","administeringCentre","Praxis Sommergarten"],
  ["...salt...","batchNumber","1626382736"],
  ["...salt...","healthProfessional","883110000015376"],
  ["...salt...","atcCode","J07BX03"],
  ["...salt...","medicinalProductName","COVID-19 Vaccine Moderna"],
  ["...salt...","marketingAuthorizationHolder","Moderna Biotech"],
  ["...salt...","gender","Female"],
  ["...salt...","birthDate","1961-08-17"],
  ["...salt...","givenName","Marion"],
  ["...salt...","familyName","Mustermann"]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleSdJwtVcDataV01.kt).

<!--- TEST sdJwtVcDataV2.assertThat("Appendix 4 - Example 4b: W3C Verifiable Credentials Data Model v2.0", 14) -->
