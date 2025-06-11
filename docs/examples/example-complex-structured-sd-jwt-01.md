<!--- TEST_NAME ExampleComplexStructured01Test --> 

# Appendix 2 - Example 3: Complex Structured SD-JWT

Description of the example in
the [specification Appendix 2 - Example 3: Complex Structured SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-3-complex-structure)

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
-->

```kotlin
val complexStructuredSdJwt =
    sdJwt {
        claim("iss", "https://issuer.example.com")
        claim("iat", 1683000000)
        claim("exp", 1883000000)
        objClaim("verified_claims") {
            objClaim("verification") {
                sdClaim("time", "2012-04-23T18:25Z")
                sdClaim("verification_process", "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7")
                claim("trust_framework", "de_aml")
                arrClaim("evidence") {
                    sdObjClaim {
                        sdClaim("type", "document")
                        sdClaim("method", "pipp")
                        sdClaim("time", "2012-04-22T11:30Z")
                        sdObjClaim("document") {
                            claim("type", "idcard")
                            objClaim("issuer") {
                                claim("name", "Stadt Augsburg")
                                claim("country", "DE")
                            }
                            claim("number", "53554554")
                            claim("date_of_issuance", "2010-03-23")
                            claim("date_of_expiry", "2020-03-22")
                        }
                    }
                }
            }
            objClaim("claims") {
                sdClaim("given_name", "Max")
                sdClaim("family_name", "Müller")
                sdArrClaim("nationalities") {
                    claim("DE")
                }
                sdClaim("birthdate", "1956-01-28")
                sdObjClaim("place_of_birth") {
                    claim("country", "IS")
                    claim("locality", "Þykkvabæjarklaustur")
                }
                sdObjClaim("address") {
                    claim("locality", "Maxstadt")
                    claim("postal_code", "12344")
                    claim("country", "DE")
                    claim("street_address", "Weidenstraße 22")
                }
            }
            sdClaim("birth_middle_name", "Timotheus")
            sdClaim("salutation", "Dr.")
            sdClaim("msisdn", "49123456789")
        }
    }
```

Produces

```json
{
  "iss": "https://issuer.example.com",
  "iat": 1683000000,
  "exp": 1883000000,
  "verified_claims": {
    "verification": {
      "_sd": [
        "EWyCO8OLCdLjs5Ql_jzDe7qb4l8OhXPVAq_Izkuk3O0",
        "V5Gl6tnpcLCs6g1NUe1n4ge3qF5fNKlFeHcW5kFqZIM"
      ],
      "trust_framework": "de_aml",
      "evidence": [
        {
          "...": "YnL4kGryd2_kNdmiqCzy8S-DV4IeTDiIr6Bj0tPDU6c"
        }
      ]
    },
    "claims": {
      "_sd": [
        "igi72f_oFoMVxtaxzSvh-UIewL7b9qI32-Ra3xqUJy4",
        "2waOsSXu1OVuYUnaPmFJqCzzYgio_AIvSimdAt-GPgU",
        "vGxaWbmmhr9oR4ZG2u0LUWJBWwbwJVluMhUvvqnLnDU",
        "y19XS7INnwI8VF_zzqiQLwUu_6IfiOAkQwAUa8_lpqo",
        "afN_nLp0mAxoTsAxr0j574fd7BFLU2ughsJEoRVyraw",
        "aXQJHGy38vCJ-Is5K33cIJtn5uqaYGLuI2zjQoQB9Hs"
      ]
    },
    "_sd": [
      "DnE3UiysFTj-MFqQHIbPj9VNbtgCT5YFr0qAydLO9lg",
      "5mV0W_152wSqBrrShVbH1I4UqUAvejfzBg4gyP1tusc",
      "yr_roOLAd6UeL6BfoR8pmI816LulpQ1FICKFAQDA4fs"
    ]
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...","time","2012-04-23T18:25Z"],
  ["...salt...","verification_process","f24c6f-6d3f-4ec5-973e-b0d8506f3bc7"],
  ["...salt...","type","document"],
  ["...salt...","method","pipp"],
  ["...salt...","time","2012-04-22T11:30Z"],
  ["...salt...","document",{"type":"idcard","issuer":{"name":"Stadt Augsburg","country":"DE"},"number":"53554554","date_of_issuance":"2010-03-23","date_of_expiry":"2020-03-22"}],
  ["...salt...",{"_sd":["OBaGga10nf1m2DW0vWdwD1lBXjivCtSHzGcPnKtyY7s","FGwmPe9a1aWRd6o1NLhgol78e0_2NGbg4jZZAdNRPs0","W2baZYXUByZvX_Ssr-vPxzIANolgTgt7hvxLzS-fh9Y","4teoClbh7XwddDqV1_Rz8DO0vGJOrbhbd6KfWXeMnos"]}],
  ["...salt...","given_name","Max"],
  ["...salt...","family_name","Müller"],
  ["...salt...","nationalities",["DE"]],
  ["...salt...","birthdate","1956-01-28"],
  ["...salt...","place_of_birth",{"country":"IS","locality":"Þykkvabæjarklaustur"}],
  ["...salt...","address",{"locality":"Maxstadt","postal_code":"12344","country":"DE","street_address":"Weidenstraße 22"}],
  ["...salt...","birth_middle_name","Timotheus"],
  ["...salt...","salutation","Dr."],
  ["...salt...","msisdn","49123456789"]
]
```

> You can get the full code [here](../../src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleComplexStructured01.kt).

<!--- TEST complexStructuredSdJwt.assertThat("Appendix 2 - Example 3: Complex Structured SD-JWT", 16) -->
