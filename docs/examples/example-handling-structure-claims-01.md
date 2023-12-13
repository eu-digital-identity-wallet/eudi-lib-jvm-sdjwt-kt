<!--- TEST_NAME ExampleHandlingStructuredClaims01Test --> 

# Appendix A-1 - Example 2: Handling Structured Claims

Description of the example in the [specification Appending A-1 - Example 2: Handling Structured Claims](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-2-handling-structur)

In this example, the Issuer decided to create a structured object for the `address` claim, allowing to separately 
disclose individual members of the claim.

```kotlin
object ExampleHandlingStructuredClaims01 {
    val sdObject =
        sdJwt {
            iss("https://issuer.example.com")
            iat(1683000000)
            exp(1883000000)

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
}
```
Produces

```json
{
  "iss": "https://issuer.example.com",
  "iat": 1683000000,
  "exp": 1883000000,
  "_sd": [
    "qbQAj0dE3igrWE794PRfoHOMMEt2GotDJ0iAOh4NJJ8",
    "LmwjkSlgL4JyzfoQl1X8ed7z_bpFIlMD9PhM9AA74g4",
    "q0l6vjlj3nju8I96-ILrysjeI2lzdcpK6ETa5kz1pr8",
    "qdsD0MHfo1qP-uJxzInStmlkM4gp-z-HynCwfKNHcx4",
    "k9vpV5Z4XJJBZ0QX5pPi4dutJ6V0_rYzrv9T0I3-eJw",
    "7OB1VFEGEnduH465YOhv7SbxkQfW8Upv1XfKmwygYtQ"
  ],
  "address": {
    "_sd": [
      "OdrekYoJNMDnIaqTRvtF8eMFiYsl8JCtuoirdbVzZVU",
      "OSuvmHrFXftSn8sDKiAzcVpO1mls_b70rxNz92_D9VA",
      "Znq6-AXnKk6RZKtJoq81fuTafyIjnC8EUD7X5fcApq4",
      "5NpHJeMaix3pTF9FgdrTqR5PMx6Ek5MS8R-3htJFE9Q"
    ]
  },
  "_sd_alg": "sha-256"
}
```

and the following disclosures (salt omitted):

```json 
[
  ["...salt...","sub","6c5c0a49-b589-431d-bae7-219122a9ec2c"],
  ["...salt...","given_name","太郎"],
  ["...salt...","family_name","山田"],
  ["...salt...","email","\"unusual email address\"@example.jp"],
  ["...salt...","phone_number","+81-80-1234-5678"],
  ["...salt...","birthdate","1940-01-01"],
  ["...salt...","street_address","東京都港区芝公園４丁目２−８"],
  ["...salt...","locality","東京都"],
  ["...salt...","region","港区"],
  ["...salt...","country","JP"]
]
```

<!--- KNIT ExampleHandlingStructuredClaims01.kt -->
<!--- TEST ExampleHandlingStructuredClaims01.sdObject.assertThat("Appendix 1 - Example 2: Handling Structured Claims", 10) -->
