<!--- TEST_NAME ExampleSdJwtVerifiableCredentials01Test --> 

# Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)

Description of the example in the [specification Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-4a-sd-jwt-based-ver)

```kotlin
object ExampleSdJwtVerifiableCredentials01 {
    val sdObject =
        sdJwt {
            iss("https://pid-provider.memberstate.example.eu")
            iat(1541493724)
            exp(1883000000)

            plain {
                put("type", "PersonIdentificationData")
            }

            sd {
                put("first_name", "Erika")
                put("family_name", "Mustermann")
                put("birth_family_name", "Schmidt")
                put("birthdate", "1973-01-01")

                putJsonObject("address") {
                    put("postal_code", "12345")
                    put("locality", "Irgendwo")
                    put("street_address", "Sonnenstrasse 23")
                    put("country_code", "DE")
                }

                put("is_over_18", true)
                put("is_over_21", true)
                put("is_over_65", false)
            }

            recursiveArray("nationalities") {
                sd("DE")
            }

            plain {
                putJsonObject("cnf") {
                    putJsonObject("jwk") {
                        put("kty", "EC")
                        put("crv", "P-256")
                        put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
                        put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
                    }
                }
            }
        }
}
```

Produces

```json
{
  "iss": "https://pid-provider.memberstate.example.eu",
  "iat": 1541493724,
  "exp": 1883000000,
  "type": "PersonIdentificationData",
  "_sd": [
    "4su09Z3bC7AKzfkYE032y6SdUXpooK8OqW89L-Bq4Vo",
    "OkiKykLLcItB6ZUWQf80kEo4Qy_90EfYdeESEZFzZbU",
    "wmqxpV_AwaCF2vnBVexz3ukr5Zu5G2f-q7leTHNMkZI",
    "arC9peX1alXgWmcI4oyDwSaG964Ff72L2_en_QhCtJA",
    "AYlLIAl8DEaLyGZlPk0IJ8AkV_kQnBceQjoKt6yqnOA",
    "0inWJsEAtEtr6zoNaLDe7iifLjqvc6zGGUYSoLmG7XA",
    "e9g2nG_hFk9rILOBi_i7laGkG1W7HJcvlRgntmy_kWM",
    "d1oq7KsXmTvCCniODl0LoR0wJyBUCShj5H__lf8Zo6s",
    "w0y_14m9NhdKdPSBzTTpFtBVkb_yK0pQ-Jb3Gg6moHQ"
  ],
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
      "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    }
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...","first_name","Erika"],
  ["...salt...","family_name","Mustermann"],
  ["...salt...","birth_family_name","Schmidt"],
  ["...salt...","birthdate","1973-01-01"],
  ["...salt...","address",{"postal_code":"12345","locality":"Irgendwo","street_address":"Sonnenstrasse 23","country_code":"DE"}],
  ["...salt...","is_over_18",true],
  ["...salt...","is_over_21",true],
  ["...salt...","is_over_65",false],
  ["...salt...","DE"],
  ["...salt...","nationalities",[{"...":"Mf7hxnDJRwKTuM2_XFZHGjdGMt6WHDG8dQi8i9w0vFM"}]]
]
```


> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleSdJwtVerifiableCredentials01.kt).

<!--- TEST ExampleSdJwtVerifiableCredentials01.sdObject.assertThat("Appendix 3 - Example 4a: SD-JWT VC", 10) -->
