# Example: Flat SD-JWT

Check [Flat SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-flat-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` is selectively disclosed as a whole.
Also, standard JWT claims have been added plain (`iss`, `iat`, `exp`)

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*

sdJwt {
    sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

    sd {
        putJsonObject("address") {
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
    "_sd": [
        "bPmTLglJnCrWVA_ozJqr45buaH1327SMxsTFw4sgOl4"
    ],
    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
    "iss": "https://example.com/issuer",
    "iat": 1516239022,
    "exp": 1735689661,
    "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json
{
  "address" : {
    "street_address":"Schulstr. 12",
    "locality":"Schulpforta",
    "region":"Sachsen-Anhalt",
    "country":"DE"
  }
}
```