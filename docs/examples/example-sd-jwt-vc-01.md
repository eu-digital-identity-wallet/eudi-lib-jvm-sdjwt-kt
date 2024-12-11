<!--- TEST_NAME ExampleSdJwtVerifiableCredentials01Test --> 

# Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)

Description of the example in the [specification Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-4a-sd-jwt-based-ver)

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
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

        sd("age_equal_or_over") {
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
```

Produces

```json
{
  "iss": "https://issuer.example.com",
  "iat": 1683000000,
  "exp": 1883000000,
  "vct": "https://bmi.bund.example/credential/pid/1.0",
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
      "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    }
  },
  "_sd": [
    "eO_HA4ixR8-HtnCIexytyrg9YoIv1_f2JBBvoeURT4U",
    "UhB7COWXQpF6BQXOF5T2dSHAZEn4eQbL6ljmte0T3ig",
    "Cz4sgRSQNxQPMFOTQX-udUqRXXwUC39PzFyCfXxmjos",
    "jtA1gYLu-j9BUCTBZOz2scCYtcWTM48ZbYupoB8WK-g",
    "JcDLTp8dgP_DxWDjdHiqZ3PZy62Ue9A58Y6mx-L8wMQ",
    "X1Oj6ZxK44W1sKTRXEltMeAvF0zzkHkqpGgR620mKNY",
    "eK0doYXEPQ2HuGOkfQybV4hsa85yXRvysNq5j5BQ-_Y",
    "oM31EdZdrBS6FwbgqhgyPb0ZEkySUhtiAlBCSUPWc1w",
    "-AolJEEv-Q1oWNPJ3PUpC9vo8wuAohxO2jEb7PwLRyA",
    "PU-PF8t1qdcyyE71KYaOo_CXN67kZoZMnVpImadVqRI"
  ],
  "age_equal_or_over": {
    "_sd": [
      "hCETIFvt0REVh9eR9eTSggLxGS-x9Xk5RolLINs9B6k",
      "HnkQfo5UceT71ygJ5VFZBWBCKSYUm2zzNVrTQ6F6Trk",
      "gPSU6dRniTHF3ERLx8-BdZVUAMJE49WpZ4wovKZNZpI",
      "e8jBCC_A0FLS9wsXPqaXpi2MWQ0iHsbsSAP5S99zFqc",
      "hSO06ko_FY1zKcxhkzk1B5GW15sJWF2HCeGj2OSICPk",
      "Hxi2pax1gFZY0CU2VPMMJFsMG32YNbVEA9s_wNcQJJ8"
    ]
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...","given_name","Erika"],
  ["...salt...","family_name","Mustermann"],
  ["...salt...","birthdate","1963-08-12"],
  ["...salt...","source_document_type","id_card"],
  ["...salt...","nationalities",["DE"]],
  ["...salt...","gender","female"],
  ["...salt...","birth_family_name","Gabler"],
  ["...salt...","also_known_as","Schwester"],
  ["...salt...","street_address","Heidestraße 17"],
  ["...salt...","locality","Köln"],
  ["...salt...","postal_code","51147"],
  ["...salt...","country","DE"],
  ["...salt...","address",{"_sd":["0SqBd-P2pU7bEcaEF7JHSrM_uZUXr4ZDO2p0lEFpB30","PXDyvcQ3-3eeJLfYKWIbeO2Pm4dUjTVW9w0jC7zFyUw","6YonldXmAaSSIV7HpttlHqAtG71DN-dzLr7thT3xNr4","XWilRh55_L3EzKY0VeXa0FFJb5nuzknE2iBV_Zdhh4w"]}],
  ["...salt...","locality","Berlin"],
  ["...salt...","place_of_birth",{"country":"DE","_sd":["242Ck5tIKgGYuKojAtIt9sLnqrWsNr3Gnj1g2RPc3Vw"]}],
  ["...salt...","12",true],
  ["...salt...","14",true],
  ["...salt...","16",true],
  ["...salt...","18",true],
  ["...salt...","21",true],
  ["...salt...","65",false]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleSdJwtVerifiableCredentials01.kt).

<!--- TEST sdJwtVc.assertThat("Appendix 3 - Example 4a: SD-JWT VC", 21) -->
