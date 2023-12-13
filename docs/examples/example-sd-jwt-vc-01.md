<!--- TEST_NAME ExampleSdJwtVerifiableCredentials01Test --> 

# Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)

Description of the example in the [specification Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-4a-sd-jwt-based-ver)

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
val sdJwtVc =
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
```

Produces

```json
{
  "iss": "https://pid-provider.memberstate.example.eu",
  "iat": 1541493724,
  "exp": 1883000000,
  "type": "PersonIdentificationData",
  "_sd": [
    "NJvTfT23JLkZhIltHY0AO7rXOVDkkaRS6fQdRcm6VXM",
    "-OKrobtfIMN-laBbAdgtbw-fC-F0LZJmA2L0EBm9sGY",
    "xRVBj5H2WasSEZg9EyayY78uAwEV_002tuMK89z7E84",
    "QtO_azYf72Nc57WtikqTP8i2wM3dUa7Z2-LAKV89xBA",
    "jXEVBYdGHK-agzRSEDsntdqXO7BBPsxAYfLtqIw864U",
    "jzkHE6kjv3E29J4Oz-x1mej1MNQK1KJSRmyJgKYo5VI",
    "KRXWVqAE0VOiwagxYaanaDfNkehjvgkbP-q6DOydvKg",
    "7Ie5wOPnnvvFfNJmO8So8hoBuk1S2EABcqTWcLZe4Ig",
    "krfazQ7Bmisasqth6EQUCMoTvFHTD4-34WXKp6dmjjM"
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

<!--- TEST sdJwtVc.assertThat("Appendix 3 - Example 4a: SD-JWT VC", 10) -->
