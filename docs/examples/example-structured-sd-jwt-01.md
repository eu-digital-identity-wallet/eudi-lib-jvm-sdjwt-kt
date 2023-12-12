<!--- TEST_NAME ExampleStructuredSdJwt01Test --> 

# Example 2: Structured SD-JWT

Check [specification Example 2: Structured SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-structured-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` claim contents selectively disclosable individually

```kotlin
object ExampleStructuredSdJwt01 {
    val sdObject =
        sdJwt {
            iss("https://issuer.example.com")
            iat(1683000000)
            exp(1883000000)
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")

            structured("address") {
                sd {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
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
  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
  "address": {
    "_sd": [
      "Ek2zRVvXFVw3-EHiBK3Lsssjv1qbotW_sp2ju03sq4s",
      "evzKfw0XVp9x1Br5IZrl1c-t0HlrGo6uvYY7qIUhqXA",
      "fd4ull64_NWEHwPFiE87blw2sjDjtyZW-Ho_DuZUpyE",
      "jBYSBZ7gJpwMja_eJcWuVHJFAmz1fF7xOc6Fb0dGGH8"
    ]
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...", "street_address", "Schulstr. 12"],
  ["...salt...", "locality", "Schulpforta"],
  ["...salt...", "region", "Sachsen-Anhalt"],
  ["...salt...", "country", "DE"]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleStructuredSdJwt01.kt).

<!--- TEST ExampleStructuredSdJwt01.sdObject.assertThat("Example 2: Structured SD-JWT", 4) -->
