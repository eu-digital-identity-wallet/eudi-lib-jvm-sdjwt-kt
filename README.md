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

## Use cases supported

- [Issuance](#issuance): As an Issuer use the library to issue a SD-JWT (in Combined Issuance Format)
- [Holder Verification](#holder-verification): As Holder verify a SD-JWT (in Combined Issuance Format) issued by an Issuer
- [Holder Presentation](#holder-presentation): As Holder create a SD-JWT for presentation (in Combined Presentation Format)
- [Presentation Verification](#presentation-verification): As a Verifier verify SD-JWT (in Combined Presentation Format) 

## Issuance

To issue a SD-JWT, an `Issuer` should have:

- Decided on how the issued claims will be selectively disclosed (check [DSL examples](#dsl-examples))
- Whether to use decoy digests or not
- An appropriate signing key pair
- optionally, decided if and how will include holder's public key to the SD-JWT

In the example bellow, Issuer decides to issue an SD-JWT as follows:

- Includes in plain standard JWT claims (`sub`,`iss`, `iat`, `exp`)
- Makes selectively disclosable a claim named `address` using structured disclosure. This allows to individually disclose every sub-claim of `address`
- Uses his RSA key pair to sign the SD-JWT

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

## Holder Verification

In this case the SD-JWT is expected to be in Combined Issuance format.

`Holder` must know: 
- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.crypto.ECDSAVerifier

val unverifiedSdJwt : String ="..."
val issuerPubKey: ECPublicKey
val jwtVerifier = ECDSAVerifier(issuerPubKey).asJwtVerifier() 

val holdersSdJwt : SdJwt.Issuance<Claims> = 
    SdJwtVerifier.verifyIssuance(jwtVerifier, unverifiedSdJwt).getOrThrow()
val (jwtClaims, disclosures) = holdersSdJwt
```

## Holder Presentation

To create a presentation SD-JWT, a `Holder` must:

- Have an issued SD-JWT
- Know whether verifier to whom the presentation is for, requires Holder Binding or not

In the following example, `Holder` presents only `street_address` and `country` without Holder Binding 

```kotlin

val holdersSdJwt : SdJwt.Issuance<SignedJWT> 

// Chooses which selectively disclosable claims to be presented
val claimsToDisclose = {claim -> claim.name in listOf("street_address", "country")}

val presentationSdJwt : SdJwt.Presentation<SignedJwt, Nothing> =  
        holdersSdJwt.present(null, claimsToDisclose)

val presentationSdJwtStr = presentationSdJwt.serialize()

```

## Presentation Verification

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








