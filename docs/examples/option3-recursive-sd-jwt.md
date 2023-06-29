# Option 3: SD-JWT with Recursive Disclosures

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