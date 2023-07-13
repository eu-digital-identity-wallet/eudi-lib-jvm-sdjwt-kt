# Example 2a: Handling Structured Claims

Description of the example in the [specification Example 2a: Handling Structured Claims](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-05.html#name-example-2-handling-structur)

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

    sd {
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