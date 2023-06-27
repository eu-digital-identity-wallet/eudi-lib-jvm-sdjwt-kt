# eudi-lib-jvm-sdjwt-kt

This is library offering a DSL (domain specific language)
for defining how a set of claims should be made selectively
disclosable.

Library implements [SD-JWT draft4](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-04.html)
is implemented in Kotlin, targeting JVM.

Library's SD-JWT DSL leverages the the DSL provided by 
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements 

## DSL Examples

All examples assume that we have the following claim set

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

- [Option 1: Flat SD-JWT](#option-1-flat-sd-jwt)
- [Option 2: Structured SD-JWT](#option-2-structured-sd-jwt)
- [Option 3: SD-JWT with Recursive Disclosures](#option-3-sd-jwt-with-recursive-disclosures)
- [Example 2a: Handling Structured Claims](#example-2a-handling-structured-claims)


### Option 1: Flat SD-JWT

Check [specification Option 1: Flat SD-JWT](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-1-flat-sd-jwt)

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

### Option 2: Structured SD-JWT

Check [specification Option 2: Structured SD-JWT](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-2-structured-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` claim contents selectively disclosable individually

```kotlin
sdJwt {
    sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

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
and the following disclosures (salt omitted):

```json 
{ 
  "street_address " : "Schulstr. 12"
}
```

```json 
{
  "locality" : "Schulpforta"
}
```
```json 
{
  "region" : "Sachsen-Anhalt"
}
```
```json 
{
  "country" : "DE"
}
```
### Option 3: SD-JWT with Recursive Disclosures

Check [specification Option 3: SD-JWT with Recursive Disclosures](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-option-3-sd-jwt-with-recurs)

```kotlin
sdJwt {
    sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

    recursively("address") {
        put("street_address", "Schulstr. 12")
        put("locality", "Schulpforta")
        put("region", "Sachsen-Anhalt")
        put("country", "DE")
    }
}
```

Produces

```json
{
    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
    "iss": "https://example.com/issuer",
    "iat": 1516239022,
    "exp": 1735689661,
    "_sd": [
        "hV3ODWPnOhf2NoKDu7P95l0_hYHtEgiyNFDfnp7keK4"
    ],
    "_sd_alg": "sha-256"
}
```
and the following disclosures (salt omitted):

```json 
{ 
  "street_address " : "Schulstr. 12"
}
```

```json 
{
  "locality" : "Schulpforta"
}
```
```json 
{
  "region" : "Sachsen-Anhalt"
}
```
```json 
{
  "country" : "DE"
}
```
```json 
{
  "address": {
    "_sd": [
      "0VIpwaAlklovZ9ZqoVNaqwsVJ0F1yq0dUackLiRHI34",
      "LFYT0w3_i6EcSoxKW1rS8FwZ__yl98LH3txq47iRGPc",
      "iARv_ADQxrdgM4rsQ7DClrEXyTReBw_DU3rRLohb6iA",
      "z8xX_wFl-4gDAwHX-yGAEPu0OgPUE1LT5TJwSPMJZh4"
    ]
  }
}
```

### Example 2a: Handling Structured Claims

Description of the example in the [specification Example 2a: Handling Structured Claims](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#name-example-2a-handling-structu)

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

    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

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
Produces

```json
{
    "iss": "https://example.com/issuer",
    "iat": 1516239022,
    "exp": 1735689661,
    "_sd": [
        "1nqCcdyEBs7CeDF9DAmlv6vH_Bxg2oijdUXrEc1GXsk",
        "Pc-TFtBmjxfSYFE8KrjFL2qdRi1TYVF43Phw-SkxQTI",
        "U279L5sFAupFzXkf0z1xqFUVF9R-vEFB2XeS_Nr56G0",
        "aDFSxGelDNsGKK111qXIF6TmYnWYCT8jk48yfNFocQA",
        "itcuLF2Ijg4jhWQL39oByyrkk2rihcXz0QVSj8rAPzU",
        "kGPUPFd7Dg1mitLVVKLfD0miziropwSs575LBkRdz8o",
        "yXLGrdEpVc6H7FtLfuQ_Ra82XKqMFpD61ZK9-97Hfd4"
    ],
    "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
{
  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c"
}
```

```json
{"given_name" : "太郎"}
```

```json
{"family_name" : "山田"}
```

```json
{"email" : "\"unusual email address\"@example.jp"}
```                        

```json
{"phone_number" : "+81-80-1234-5678"}
```

```json
{
  "address": {
    "street_address": "東京都港区芝公園４丁目２−８",
    "locality": "東京都",
    "region": "港区",
    "country": "JP"
  }
}
```

```json
  {"birthdate" : "1940-01-01"}

```




