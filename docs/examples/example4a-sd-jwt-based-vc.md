# Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)

Description of the example in the [specification Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-05.html#name-example-4a-sd-jwt-based-ver)

```json
{
  "iss": "https://pid-provider.memberstate.example.eu",
  "iat": 1541493724,
  "type": "PersonIdentificationData",
  "first_name": "Erika",
  "family_name": "Mustermann",
  "nationalities": [
    "DE"
  ],
  "birth_family_name": "Schmidt",
  "birthdate": "1973-01-01",
  "address": {
    "postal_code": "12345",
    "locality": "Irgendwo",
    "street_address": "Sonnenstrasse 23",
    "country_code": "DE"
  },
  "is_over_18": true,
  "is_over_21": true,
  "is_over_65": false
}
```

```kotlin
sdJwt {
    iss("https://pid-provider.memberstate.example.eu")
    iat(1541493724)
    exp(1883000000)
    plain {
        put("type", "PersonIdentificationData")
    }

    sd {
        put("first_name", "Erika")
        put("family_name", "Mustermann")
        put("birth_family_name", "Schmidt")
        put("birthdate", "1973-01-01")
        put("is_over_18", true)
        put("is_over_21", true)
        put("is_over_65", false)
    }

    recursiveArr("nationalities") {
        sd("DE")
    }

    recursive("address") {
        plain {
            put("postal_code", "12345")
            put("locality", "Irgendwo")
            put("street_address", "Sonnenstrasse 23")
            put("country_code", "DE")
        }
    }
}
```
Produces

```json
{
  "_sd": [
    "09TbSuo12i2CqZbg31AFgbGy_UnMIXIHoMjsELpukqg",
    "0n9yzFSWvK_BUHiaMhm12ghrCtVahrGJ6_-kZP-ySq4",
    "4VoA3a1VmPxmdC8WIn3pOqQf3gfOVOvDYsN5E5R5Kd0",
    "5A88AmauAao-QANao95CYUkUPNTid_gAK8aYtZ9RZwc",
    "910byr3UVRqRzQoPzBsc20m-eMgpZAhLN6z8NoGF5mc",
    "Ch-DBcL3kb4VbHIwtknnZdNUHthEq9MZjoFdg6idiho",
    "I00fcFUoDXCucp5yy2ujqPssDVGaWNiUliNz_awD0gc",
    "X9MaPaFWmQYpfHEdytRdaclnYoEru8EztBEUQuWOe44",
    "Y1urWJV_-HBGnSf9tFOwvH4cICRBCiKwEHfkXFSfjpo",
    "rNhKoraaq--x7BWWIVhbGXu1XXXLM8ivZXD3m2FZMgs",
    "xpsq6cxQHDsOnZWhrqBckTkOM_efElUnDFXOFmowLSE",
    "zU452lkGbEKh8ZuH_8Kx3CUvn1F4y1gZLqlDTgX_8Pk"
  ],
  "iss": "https://pid-provider.memberstate.example.eu",
  "iat": 1541493724,
  "exp": 1883000000,
  "type": "PersonIdentificationData",
  "_sd_alg": "sha-256",
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
      "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    }
  }
}
```

and the following disclosures (salt omitted):

```json 
{
  "first_name": "Erika"
}
```

```json
{"family_name": "Mustermann"}
```

```json
{"6Ij7tM-a5iVPGboS5tmvVA": "DE"}
```

```json
{"nationalities": [{"...":
"JuL32QXDzizl-L6CLrfxfjpZsX3O6vsfpCVd1jkwJYg"}]}
```                        

```json
{"birth_family_name" : "Schmidt"}
```

```json
{"birthdate" : "1973-01-01"}
```

```json
{
  "address": {
    "postal_code": "12345", 
    "locality": "Irgendwo",
    "street_address": "Sonnenstrasse 23",
    "country_code": "DE"
  }
}
```

```json
{"is_over_18" : true}
```

```json
{"is_over_21" : true}
```

```json
{"is_over_65" : false}
```




