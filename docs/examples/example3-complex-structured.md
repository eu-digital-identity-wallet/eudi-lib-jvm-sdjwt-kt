# Example 3: Complex Structured SD-JWT

Description of the example in the [specification Example 3: Complex Structured SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-05.html#name-example-3-complex-structure)

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
  "_sd": [
    "-aSznId9mWM8ocuQolCllsxVggq1-vHW4OtnhUtVmWw",
    "IKbrYNn3vA7WEFrysvbdBJjDDU_EvQIr0W18vTRpUSg",
    "otkxuT14nBiwzNJ3MPaOitOl9pVnXOaEHal_xkyNfKI"
  ],
  "iss": "https://example.com/issuer",
  "iat": 1683000000,
  "exp": 1883000000,
  "verified_claims": {
    "verification": {
      "_sd": [
        "7h4UE9qScvDKodXVCuoKfKBJpVBfXMF_TmAGVaZe3Sc",
        "vTwe3raHIFYgFA3xaUD2aMxFz5oDo8iBu05qKlOg9Lw"
      ],
      "trust_framework": "de_aml",
      "evidence": [
        {
          "...": "tYJ0TDucyZZCRMbROG4qRO5vkPSFRxFhUELc18CSl3k"
        }
      ]
    },
    "claims": {
      "_sd": [
        "RiOiCn6_w5ZHaadkQMrcQJf0Jte5RwurRs54231DTlo",
        "S_498bbpKzB6Eanftss0xc7cOaoneRr3pKr7NdRmsMo",
        "WNA-UNK7F_zhsAb9syWO6IIQ1uHlTmOU8r8CvJ0cIMk",
        "Wxh_sV3iRH9bgrTBJi-aYHNCLt-vjhX1sd-igOf_9lk",
        "_O-wJiH3enSB4ROHntToQT8JmLtz-mhO2f1c89XoerQ",
        "hvDXhwmGcJQsBCA2OtjuLAcwAMpDsaU0nkovcKOqWNE"
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
  "document": {"type": "idcard",
    "issuer": {"name": "Stadt Augsburg", "country": "DE"},
    "number": "53554554", "date_of_issuance": "2010-03-23",
    "date_of_expiry": "2020-03-22"}
}
```

Array Entry:
```json 
{
  "Pc33JM2LchcU_lHggv_ufQ": {"_sd":
  ["9wpjVPWuD7PK0nsQDL8B06lmdgV3LVybhHydQpTNyLI",
    "G5EnhOAOoU9X_6QMNvzFXjpEA_Rc-AEtm1bG_wcaKIk",
    "IhwFrWUB63RcZq9yvgZ0XPc7Gowh3O2kqXeBIswg1B4",
    "WpxQ4HSoEtcTmCCKOeDslB_emucYLz2oO8oHNr1bEVQ"]}
}
```


```json 
{
  "given_name": "Max"
}
```

```json 
{
  "family_name": "M\u00fcller"
}
```

```json 
{
  "nationalities": ["DE"]
}
```

```json 
{
  "birthdate": "1956-01-28"
}
```

```json 
{
  "place_of_birth":  {"country": "IS", 
    "locality": "\u00deykkvab\u00e6jarklaustur"}
}
```

```json 
{
  "address": {"locality":
  "Maxstadt", "postal_code": "12344", "country": "DE",
    "street_address": "Weidenstra\u00dfe 22"}
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

