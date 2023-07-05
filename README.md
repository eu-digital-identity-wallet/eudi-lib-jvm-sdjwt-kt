# EUDI SD-JWT

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Table of contents

* [Overview](#overview)
* [DSL Examples](#dsl-examples)
* [How to contribute](#how-to-contribute)
* [License](#license)

## Overview

This is library offering a DSL (domain specific language) for defining how a set of claims should be made selectively
disclosable.

Library implements [SD-JWT draft4](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-04.html)
is implemented in Kotlin, targeting JVM.

Library's SD-JWT DSL leverages the the DSL provided by 
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements 

## Issuance

```kotlin

import com.nimbusds.jose.crypto.RSASigner
import com.nimbusds.jose.jwk.RSAKey
import eu.europa.ec.eudi.sdjwt.*

val iss = "Issuer"
val issuerKeyPair: RSAKey
val sdJwt = sdJwt(signer = RSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256) {
    
    sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
    iss("https://example.com/issuer")
    iat(1516239022)
    exp(1735689661)

    structured("address") {
        flat {
            put("street_address", "Schulstr. 12")
            put("locality", "Schulpforta")
            put("region", "Sachsen-Anhalt")
            put("country", "DE")
        }
    }
}.serialize()


```

## Verification

There are two cases of SD-JWT verification:
- in case of `combined issuance format`. This is applicable to the holder
- in case of  `combined presentation format`. This is applicable to the verifier 

### Holder Verification

In this case the SD-JWT is expected to be in Combined Issuance format.
Holder should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.crypto.ECDSAVerifier

val unverifiedSdJwt : String ="..."
val issuerPubKey: ECPublicKey
val jwtVerifier = ECDSAVerifier(issuerPubKey).asJwtVerifier() 

val sdJwt : SdJwt.Issuance<Claims> = 
    SdJwtVerifier.verifyIssuance(jwtVerifier, unverifiedSdJwt).getOrThrow()
val (jwtClaims, disclosures) = sdJwt
```

### Verifier Verification

In this case the SD-JWT is expected to be in Combined Presentation format.
Verifier should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT. Also, if verification includes Holder Binding, the Verifier must also
know a how the public key of the Holder was included into the SD-JWT and which algorithm
the Holder used to sign the `Holder Binding JWT`

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.crypto.ECDSAVerifier

val unverifiedSdJwt : String ="..."
val issuerPubKey: ECPublicKey
val jwtVerifier = ECDSAVerifier(issuerPubKey).asJwtVerifier()

//
// The following demonstrates verification of presentation
// without Holder Binding JWT
//
val sdJwt : SdJwt.Presentation<Claims, String> =
    SdJwtVerifier.verifyPresentation(
        jwtVerifier = jwtVerifier,
        holderBindingVerifier = HolderBindingVerifier.ShouldNotBePresent,
        sdJwt = unverifiedSdJwt
    ).getOrThrow()

```

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



## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

### License details

Copyright (c) 2023 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.








