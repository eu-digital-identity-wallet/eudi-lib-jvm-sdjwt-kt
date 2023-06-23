# eudi-lib-jvm-sdjwt-kt




## DSL Examples

All examples assume that we have claim set

```json
{
  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
  "address": {
    "street_address": "Schulstr. 12",
    "locality": "Schulpforta",
    "region": "Sachsen-Anhalt",
    "country": "DE"
  }
}
```
### Flat SD-JWT mixing SD-JWT DSL and Kotlinx Serialization DSL

Check [Option 1: Flat SD-JWT](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-1-flat-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` is selectively disclosed as a whole.
Also, standard JWT claims have been added plain (`iss`, `iat`, `exp`)

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*

sdJwt {
    plain { 
        put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        put("iss", "https://example.com/issuer")
        put("iat", 1516239022)
        put("exp", 1735689661)
    }
    flat {
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

### Structured SD-JWT mixing SD-JWT DSL and Kotlinx Serialization DSL

Check [Option 2: Structured SD-JWT](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-2-structured-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` claim contents selectively disclosable individually

```kotlin
sdJwt {
    plain {
        put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        put("iss", "https://example.com/issuer")
        put("iat", 1516239022)
        put("exp", 1735689661)
    }
    structured("address") {
        flat {
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
    "address": {
        "_sd": [
            "31WAhqkcrD8fuOFjZqHHbC-eHyKLw56l7D7lR_Rk6TU",
            "BTNyzBxNNFHy6P__qY1cyAOJJfX-FrdHxHgTSbFH0IA",
            "LUv679S3AKLZObi6Q6R_fd7naFo1_BISDbJvxtiw6eA",
            "sbnpjZHQm2YzxuA-0Woy9BclTPY9vQTTZCpE5LqekRQ"
        ]
    },
    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
    "iss": "https://example.com/issuer",
    "iat": 1516239022,
    "exp": 1735689661,
    "_sd_alg": "sha-256"
}
```

### Example 2a: Handling Structured Claims

Description of the example in the [Example 2a: Handling Structured Claims](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-example-2a-handling-structu)

```json
{
  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
  "given_name": "太郎",
  "family_name": "山田",
  "email": "\"unusual email address\"@example.jp",
  "phone_number": "+81-80-1234-5678",
  "address": {
    "street_address": "東京都港区芝公園４丁目２−８",
    "locality": "東京都",
    "region": "港区",
    "country": "JP"
  },
  "birthdate": "1940-01-01"
}
```
```kotlin
sdJwt {
    plain {
        put("iss", "https://example.com/issuer")
        put("iat", 1516239022)
        put("exp", 1735689661)
    }
    flat {
        put("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        put("given_name", "太郎")
        put("family_name", "山田")
        put("email", "\"unusual email address\"@example.jp")
        put("phone_number", "+81-80-1234-5678")
        putJsonObject("address") {
            put("street_address", "東京都港区芝公園４丁目２−８")
            put("locality", "東京都")
            put("region", "港区")
            put("country", "JP")
        }
        put("birthdate", "1940-01-01")
    }
}
```


### Recursive SD-JWT mixing SD-JWT DSL and Kotlinx Serialization DSL

Check [Option 3: SD-JWT with Recursive Disclosures](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-3-sd-jwt-with-recurs)
TBD

