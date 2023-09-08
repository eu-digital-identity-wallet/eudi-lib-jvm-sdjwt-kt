# Example 3: Complex Structured SD-JWT

Description of the example in
the [specification Example 3: Complex Structured SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-05.html#name-example-3-complex-structure)

```json
{
  "verified_claims": {
    "verification": {
      "trust_framework": "de_aml",
      "time": "2012-04-23T18:25Z",
      "verification_process": "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7",
      "evidence": [
        {
          "type": "document",
          "method": "pipp",
          "time": "2012-04-22T11:30Z",
          "document": {
            "type": "idcard",
            "issuer": {
              "name": "Stadt Augsburg",
              "country": "DE"
            },
            "number": "53554554",
            "date_of_issuance": "2010-03-23",
            "date_of_expiry": "2020-03-22"
          }
        }
      ]
    },
    "claims": {
      "given_name": "Max",
      "family_name": "Müller",
      "nationalities": [
        "DE"
      ],
      "birthdate": "1956-01-28",
      "place_of_birth": {
        "country": "IS",
        "locality": "Þykkvabæjarklaustur"
      },
      "address": {
        "locality": "Maxstadt",
        "postal_code": "12344",
        "country": "DE",
        "street_address": "Weidenstraße 22"
      }
    }
  },
  "birth_middle_name": "Timotheus",
  "salutation": "Dr.",
  "msisdn": "49123456789"
}
```

```kotlin
sdJwt {
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

    sd {
        put("birth_middle_name", "Timotheus")
        put("salutation", "Dr.")
        put("msisdn", "49123456789")
    }
    structured("verified_claims") {
        structured("verification") {
            plain {
                put("trust_framework", "de_aml")
            }
            sd {
                put("time", "2012-04-23T18:25Z")
                put("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
            }
            sdArray("evidence") {
                buildSdObject {
                    sd {
                        put("type", "document")
                        put("method", "pipp")
                        put("time", "2012-04-22T11:30Z")
                        putJsonObject("document") {
                            put("type", "idcard")
                            putJsonObject("issuer") {
                                put("name", "Stadt Augsburg")
                                put("country", "DE")
                            }
                            put("number", "53554554")
                            put("date_of_issuance", "2010-03-23")
                            put("date_of_expiry", "2020-03-22")
                        }
                    }
                }
            }
        }
        structured("claim") {
            sd {
                put("given_name", "Max")
                put("family_name", "Müller")
                putJsonArray("nationalities") {
                    add("DE")
                }
                put("birthdate", "1956-01-28")
                putJsonObject("place_of_birth") {
                    put("country", "IS")
                    put("locality", "Þykkvabæjarklaustur")
                }
                putJsonObject("address") {
                    put("locality", "Maxstadt")
                    put("postal_code", "12344")
                    put("country", "DE")
                    put("street_address", "Weidenstraße 22")
                }
            }
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
    "e721Y5f8a7UafHeYHjofP0PksjAjGly427g4K9Y2IDY",
    "RS9M66sj3vjsrIx-zjxt2GYN-IXh1mp7gZM8IcKz1Hw",
    "e3kvApJpIzydJta22dnac57t6cuAw3sA3rOAoK3Nx0c"
  ],
  "verified_claims": {
    "verification": {
      "trust_framework": "de_aml",
      "_sd": [
        "Ol6N4PybIYvifJQFDbEOCYg2jE83G0RM13QAvsbwQwQ",
        "vWV9mMyl4WEIHDbZ1orOwySRYSKP5PCz6Pj9judzPzI"
      ],
      "evidence": [
        {
          "...": "dReKrsxrGJNG_tTkBOfH0OFamvToQKdZQZ08fYInwa4"
        }
      ]
    },
    "claim": {
      "_sd": [
        "rbeL22IRL38aijG-nHAoRtkBZnsvhVkHncnfwt-59dQ",
        "cHcYBiORFHT9I3P4YzQ7969KetRcMDJSDgnClHWZeqQ",
        "TKqhzCiFSMv9M6yE_VMH5MbVuu9CMYaNA8R-rBDxetM",
        "kQJrRi98XuDCVeIQXzTIaX4GnOmU9Uy9oaSM1Fus-2w",
        "ckaGOiFPV28CJagP5yd95ZpyG0FlbdLNl1PsvYtrrlU",
        "TFWqSmViXraZT6OsasGMpxgcmmh-SSrM6eV8jRdO6_I"
      ]
    }
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
{
  "time": "2012-04-23T18:25Z"
}
```

```json 
{
  "verification_process": "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7"
}
```

```json 
{
  "type": "document"
}
```

```json 
{
  "method": "pipp"
}
```

```json 
{
  "time": "2012-04-22T11:30Z"
}
```

```json 
{
  "document": {
    "type": "idcard",
    "issuer": {
      "name": "Stadt Augsburg",
      "country": "DE"
    },
    "number": "53554554",
    "date_of_issuance": "2010-03-23",
    "date_of_expiry": "2020-03-22"
  }
}
```

Array Entry:

```json 
{
  "_sd": [
    "_wMV8jj_6UgLLAgpmak6fkN8u6bWh3w_cntpWYTdTTo",
    "Dva3soYW0kuETAM1MmhGBc5ZqFZK6HWkALGnOQQNa0s",
    "UOqJdECC1vax_Jo6D6nIlP0PW18cmL2zc64lWNQ0eIk",
    "QYpAb_BMD968wU6OpK_5XE92RVCnKIAdKz-lWaj3eHs"
  ]
}
```

```json 
{
  "given_name": "Max"
}
```

```json 
{
  "family_name": "Müller"
}
```

```json 
{
  "nationalities": [
    "DE"
  ]
}
```

```json 
{
  "birthdate": "1956-01-28"
}
```

```json 
{
  "place_of_birth": {
    "country": "IS",
    "locality": "Þykkvabæjarklaustur"
  }
}
```

```json 
{
  "address": {
    "locality": "Maxstadt",
    "postal_code": "12344",
    "country": "DE",
    "street_address": "Weidenstra\u00dfe 22"
  }
}
```

```json 
{
  "birth_middle_name": "Timotheus"
}
```

```json 
{
  "salutation": "Dr."
}
```

```json 
{
  "msisdn": "49123456789"
}
```

