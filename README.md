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

- [Option 1: Flat SD-JWT](docs/examples/option1-flat-sd-jwt.md)
- [Option 2: Structured SD-JWT](docs/examples/option2-structured-sd-jwt.md)
- [Option 3: SD-JWT with Recursive Disclosures](docs/examples/option3-recursive-sd-jwt.md)
- [Example 2a: Handling Structured Claims](docs/examples/example2a-handling-structure-claims.md)












