<!--- TEST_NAME ExampleRecursiveSdJwt01Test -->

# Example 3: SD-JWT with Recursive Disclosures

Check [specification Example 3: SD-JWT with Recursive Disclosures](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-sd-jwt-with-recursi)

The Issuer may also decide to make the address claim contents selectively disclosable recursively, i.e., 
the `address` claim is made selectively disclosable as well as its sub-claims:

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
val recursiveSdJwt =
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
  ["...salt...","street_address","Schulstr. 12"],
  ["...salt...","locality","Schulpforta"],
  ["...salt...","region","Sachsen-Anhalt"],
  ["...salt...","country","DE"],
  ["...salt...","address",{"_sd":["gQondEr5yGFkFRGgcrz79J6oCUg2MRl_Zk-rRNuDbw0","j2yFIn_eXU4ppH5WqgRS3SAwwL50USZHOfI3JgXe7E4","mJXm4JuM-fRcog21XYUbPEY7L8O7WLbAmbVWxYpjx54","eJ9fi4cAqh1SJ5hpYkerDgIY_SNuHEQ9_QUqR4f4duw"]}]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleRecursiveSdJwt01.kt).

<!--- TEST recursiveSdJwt.assertThat("Example 3: Recursive SD-JWT", 5) -->
