# Example: Structured SD-JWT

Check [Structured SD-JWT](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-06.html#name-example-structured-sd-jwt)

The example bellow demonstrates the usage of the library mixed with the Kotlinx Serialization DSL
to produce a SD-JWT which contains claim `sub` plain and `address` claim contents selectively disclosable individually

```kotlin
sdJwt {
    sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

    structured("address") {
        sd {
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