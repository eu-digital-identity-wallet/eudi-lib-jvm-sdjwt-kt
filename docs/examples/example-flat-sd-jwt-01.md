<!--- TEST_NAME ExampleFlatSdJwt01Test --> 

# Example 1: Flat SD-JWT

Check [specification Example 1: Flat SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-1-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT.
The Issuer in this case made the following decisions:
* The `nationalities` array is always visible, but its contents are selectively disclosable.
* The `sub` element and essential verification data (`iss`, `iat`, `cnf`, etc.) are always visible.
* All other End-User claims are selectively disclosable.
* For `address`, the Issuer is using a flat structure, i.e., all of the claims in the address claim can only 
* be disclosed in full.

```kotlin
object ExampleFlatSdJwt01 {
    val sdObject =
        sdJwt {
            iss("https://issuer.example.com")
            iat(1683000000)
            exp(1883000000)
            sub("user_42")

            sd {
                put("given_name", "John")
                put("family_name", "Doe")
                put("email", "johndoe@example.com")
                put("phone_number", "+1-202-555-0101")
                put("phone_number_verified", true)
                putJsonObject("address") {
                    put("street_address", "123 Main St")
                    put("locality", "Anytown")
                    put("region", "Anystate")
                    put("country", "US")
                }
                put("birthdate", "1940-01-01")
                put("updated_at", 1570000000)
            }

            sdArray("nationalities") {
                sd("US")
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
    "iss": "https://issuer.example.com",
    "iat": 1683000000,
    "exp": 1883000000,
    "sub": "user_42",
    "_sd": [
        "nIUE2QQQZYz5RyYyrlCD9OWVIYNTIWZZ005S3-puGWs",
        "KiIzuEFZAyccFCXcA8o8he_yyQkVIekdU9VFjt5EvEM",
        "YTxdlYhDuPbwjLqvu1Y8UOB6ITFIie6B4gV7F4wWp1c",
        "u7_httnFqe6S43vAXtTq-S6sCmLn6d0uF8J08w7gOhk",
        "URmqrWqoqWcTvC_3aA4LnE_3KomJvotlZstXEqCW3rk",
        "UC1A3ZMFrkThFsaKe1KGsfZYxXRedmUn0nW51-d3XTU",
        "7UdwhaOM2LHGmsI2o8pJ6xbYXdxvpSMvGe10pWMJHxg",
        "PrGP25eeYRXnmlmDGFNCu_-kuHK6nyr24nmpDeO49Go"
    ],
    "nationalities": [
        {
            "...": "YTuDECi0WYiJyKdmqW1BRTdeFH6ESolopL57C494x90"
        },
        {
            "...": "a-sGGuZJ9iuUXzMiaQWRYl6VHhCzlwiCAtyBukmxi5Q"
        }
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
  ["...salt...", "given_name", "John"],
  ["...salt...", "family_name", "Doe"],
  ["...salt...", "email", "johndoe@example.com"],
  ["...salt...", "phone_number", "+1-202-555-0101"],
  ["...salt...", "phone_number_verified", true],
  ["...salt...", "address", {"street_address":"123 Main St","locality":"Anytown","region":"Anystate","country":"US"}],
  ["...salt...", "birthdate", "1940-01-01"],
  ["...salt...", "updated_at", 1570000000],
  ["...salt...", "US"],
  ["...salt...", "DE"]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleFlatSdJwt01.kt).

<!--- TEST ExampleFlatSdJwt01.sdObject.assertThat("Example 1: Flat SD-JWT", 0, 10) -->