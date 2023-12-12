# Example 2a: Handling Structured Claims

Description of the example in the [Example 2a: Handling Structured Claims](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-2-handling-structur)

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
        put("birthdate", "1940-01-01")
    }
    structured("address") {
        sd {
            put("street_address", "東京都港区芝公園４丁目２−８")
            put("locality", "東京都")
            put("region", "港区")
            put("country", "JP")
        }
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
    "FnOYwFlQsWlhkMp7NCvRZwDTQl953A90tbRXXudxlB4",
    "XOgzXSbiM9HrqUQhrA9T0mJXYGcwkVopAzTCc-9_EdM",
    "XoqRBRWE_kK8cKB3tlkmGgI-lbBv5hd12RbLnNJ2hH0",
    "EiZvIeplqObTavuPPQHRuTWT8hv5IqreMZbg0-qxl5Y",
    "ck-1SrTpkVP0BoMZXADqxn7ZTqUX_fd5cVnQsqB_RV0",
    "pXlMcfISGAlQV7Gashkz349N77fOhGkEaFC9a3b4BVE"
  ],
  "address": {
    "_sd": [
      "m60ZZsNmPOvp2tkDgAatj97C37MuQwyeer_2GKliQXo",
      "SKPxKDtk4jaSjH3VjLiLQ6_e7mveKXqJ4u3XoSHBaLI",
      "fetPHIZDUxo5PxZ--xh1rYi8JlqrE4taXQobq-GZCW8",
      "LKHhDX8HyloRo73wHUuksowsc9K5m6KOXtB-k23pptw"
    ]
  },
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