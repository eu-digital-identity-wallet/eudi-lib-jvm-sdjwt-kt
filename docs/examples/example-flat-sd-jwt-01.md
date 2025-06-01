<!--- TEST_NAME ExampleFlatSdJwt01Test --> 

# Example 1: Flat SD-JWT

Check [specification Example 1: Flat SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-flat-sd-jwt)

The example below demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT.
The Issuer in this case made the following decisions:
* The `sub` element and essential verification data (`iss`, `iat`, etc.) are always visible.
* For `address`, the Issuer is using a flat structure, i.e., all of the claims in the address claim can only be disclosed in full.

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.values.sdJwt
-->

```kotlin
val flatSdJwt =
    sdJwt {
        claim("iss", "https://issuer.example.com")
        claim("iat", 1683000000)
        claim("exp", 1883000000)
        claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")

        sdObjClaim("address") {
            claim("street_address", "Schulstr. 12")
            claim("locality", "Schulpforta")
            claim("region", "Sachsen-Anhalt")
            claim("country", "DE")
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
    "jm7N0xvEn35gBZW2RYUJw8siDCFrkv81-w-nb81j7Is"
  ],
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json
[
  ["...salt...","address",{"street_address":"Schulstr. 12","locality":"Schulpforta","region":"Sachsen-Anhalt","country":"DE"}]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleFlatSdJwt01.kt).

<!--- TEST flatSdJwt.assertThat("Example 1: Flat SD-JWT", 1) -->
