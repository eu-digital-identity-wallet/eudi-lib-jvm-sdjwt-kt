<!--- TEST_NAME ExampleRecursiveSdJwt01Test -->

# Example 3: SD-JWT with Recursive Disclosures

Check [specification Example 3: SD-JWT with Recursive Disclosures](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-sd-jwt-with-recursi)

The Issuer may also decide to make the address claim contents selectively disclosable recursively, i.e., 
the `address` claim is made selectively disclosable as well as its sub-claims:

```kotlin
object ExampleRecursiveSdJwt01 {
    val sdObject =
        sdJwt {
            iss("https://issuer.example.com")
            iat(1683000000)
            exp(1883000000)
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")

            recursive("address") {
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
  "_sd": [
    "jUAs7yfMFRJWitbYdaLkjp0n53IjoquziD_0SK-5lsk"
  ],
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...", "street_address", "Schulstr. 12"],
  ["...salt...", "locality", "Schulpforta"],
  ["...salt...", "region", "Sachsen-Anhalt"],
  ["...salt...", "country", "DE"],
  ["...salt...", "address", {"_sd":["gFWXDV6mggkpsxqL-nHqqM6mIEy5WeBLV1jbz1O5pS0","LjQfgLDVbdIa57m98JIZX9I7_NeCgbmN3uHDg9q6JyY","YFYDdcnUYoWkye82Nk8zSUbG8UAXPYHNddEwI5TVrnk","ceHK1FqGvxZLm8ffvx5LI_us6mLtHMaVEkhlRe_1p-E"]}]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleRecursiveSdJwt01.kt).

<!--- TEST ExampleRecursiveSdJwt01.sdObject.assertThat("Example 3: Recursive SD-JWT", 0, 5) -->
