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

Library's SD-JWT DSL leverages the DSL provided by 
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements 

## Use cases supported

- [Issuance](#issuance): As an Issuer use the library to issue a SD-JWT (in Combined Issuance Format)
- [Holder Verification](#holder-verification): As Holder verify a SD-JWT (in Combined Issuance Format) issued by an Issuer
- [Holder Presentation](#holder-presentation): As Holder create a SD-JWT for presentation (in Combined Presentation Format)
- [Presentation Verification](#presentation-verification): As a Verifier verify SD-JWT (in Combined Presentation Format)
- [Recreate initial claims](#recreate-original-claims): Given a SD-JWT recreate the original claims

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
val sdJwt : SdJwt.Issuance<NimbusSignedJWT> = sdJwt(signer = RSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256) {
    
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

Please check [HolderBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/HolderBindingTest.kt) for more advanced
issuance scenario, including adding to the SD-JWT, holder public key, to leverage holder binding.

## Holder Verification

In this case the SD-JWT is expected to be in Combined Issuance format.

`Holder` must know: 
- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

```kotlin
import eu.europa.ec.eudi.sdjwt.*
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.crypto.ECDSAVerifier

val unverifiedSdJwt: String = "..."
val issuerPubKey: ECPublicKey
val jwtVerifier = ECDSAVerifier(issuerPubKey).asJwtVerifier()

val issued: SdJwt.Issuance<JwtAndClaims> = SdJwtVerifier.verifyIssuance(jwtVerifier, unverifiedSdJwt).getOrThrow()
val (jwtAndClaims, disclosures) = issued
```

## Holder Presentation

To create a presentation SD-JWT, a `Holder` must:

- Have an issued SD-JWT
- Know whether verifier to whom the presentation is for, requires Holder Binding or not

In the following example, `Holder` presents only `street_address` and `country` without Holder Binding 

```kotlin

//
//  The SD-JWT that holder has an issued SD-JWT
val issued: SdJwt.Issuance<JwtAndClaims> 
val whatToDisclose : (Claim)->Boolean = {claim -> claim.name() in listOf("street_address", "country")}


val presentation: SdJwt.Presentation<JwtAndClaims, Nothing> = issued.present(criteria = whatToDisclose)
val combinedPresentationFormat: String = presentation.toCombinedPresentationFormat({it.first}, {it})

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
val sdJwt : SdJwt.Presentation<JwtAndClaims, JwtAndClaims> =
    SdJwtVerifier.verifyPresentation(
        jwtVerifier = jwtVerifier,
        holderBindingVerifier = HolderBindingVerifier.MustNotBePresent,
        sdJwt = unverifiedSdJwt
    ).getOrThrow()

```
Please check [HolderBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/HolderBindingTest.kt) for more advanced
presentation scenario which includes holder binding


## Recreate original claims

Given an `SdJwt`, either issuance or presentation, the original claims used to produce the SD-JWT can be
recreated. This includes the claims that are always disclosed (included in the JWT part of the SD-JWT) having 
the digests replaced by selectively disclosable claims found in disclosures.

```kotlin
val iss = "Issuer"
val issuerKeyPair: RSAKey
val sdJwt : SdJwt.Issuance<NimbusSignedJWT> = sdJwt(signer = RSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256) {

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
}
val claims: Claims = sdJwt.recreateClaims{ jwt -> jwt.asClaims() }
```

The claims contents would be

```json
{
   "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
   "iss": "https://example.com/issuer",
   "iat": 1516239022,
   "exp": 1735689661,
   "address" : {
        "street_address": "Schulstr. 12",
        "locality": "Schulpforta",
        "region": "Sachsen-Anhalt",
        "country": "DE"
   }
}
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