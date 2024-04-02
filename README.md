<!--- TEST_NAME ExampleReadMeTest01 -->

# EUDI SD-JWT

:heavy_exclamation_mark: **Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Table of contents

* [Overview](#overview)
* [Use cases supported](#use-cases-supported)
  * [Issuance](#issuance)
  * [Holder Verification](#holder-verification)
  * [Presentation Verification](#presentation-verification)
  * [Recreate initial claims](#recreate-original-claims)
* [DSL Examples](#dsl-examples)
* [How to contribute](#how-to-contribute)
* [License](#license)

## Overview

This is a library offering a DSL (domain-specific language) for defining how a set of claims should be made selectively
disclosable.

Library implements [SD-JWT draft 8](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html)
is implemented in Kotlin, targeting JVM.

Library's SD-JWT DSL leverages the DSL provided by
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements

## Use cases supported

- [Issuance](#issuance): As an Issuer use the library to issue a SD-JWT
- [Holder Verification](#holder-verification): As Holder verify a SD-JWT issued by an Issuer
- [Presentation Verification](#presentation-verification): As a Verifier verify SD-JWT in simple or in Enveloped Format
- [Recreate initial claims](#recreate-original-claims): Given a SD-JWT recreate the original claims

## Issuance

To issue a SD-JWT, an `Issuer` should have:

- Decided on how the issued claims will be selectively disclosed (check [DSL examples](#dsl-examples))
- Whether to use decoy digests or not
- An appropriate signing key pair
- optionally, decided if and how will include holder's public key to the SD-JWT

In the example bellow, Issuer decides to issue an SD-JWT as follows:

- Includes in plain standard JWT claims (`sub`,`iss`, `iat`, `exp`)
- Makes selectively disclosable a claim named `address` using structured disclosure. This allows individually
  disclosing every subclaim of `address`
- Uses his RSA key pair to sign the SD-JWT

<!--- INCLUDE
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
val issuedSdJwt: String = run {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val sdJwtSpec = sdJwt {
        plain {
            sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
            iss("https://example.com/issuer")
            iat(1516239022)
            exp(1735689661)
        }
        structured("address") {
            sd {
                put("street_address", "Schulstr. 12")
                put("locality", "Schulpforta")
                put("region", "Sachsen-Anhalt")
                put("country", "DE")
            }
        }
    }
    val issuer = SdJwtIssuer.nimbus(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
    issuer.issue(sdJwtSpec).getOrThrow().serialize()
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleIssueSdJw01.kt).

<!--- TEST println(issuedSdJwt) -->

Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
issuance scenario, including adding to the SD-JWT, holder public key, to leverage key binding.

## Holder Verification

In this case, the SD-JWT is expected to be in serialized form.

`Holder` must know:

- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
-->

```kotlin
val verifiedIssuanceSdJwt: SdJwt.Issuance<JwtAndClaims> = run {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val jwtSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()

    val unverifiedIssuanceSdJwt = loadSdJwt("/exampleIssuanceSdJwt.txt")
    SdJwtVerifier.verifyIssuance(
        jwtSignatureVerifier = jwtSignatureVerifier,
        unverifiedSdJwt = unverifiedIssuanceSdJwt,
    ).getOrThrow()
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleIssuanceSdJwtVerification01.kt).

<!--- TEST verifiedIssuanceSdJwt.prettyPrint { it.second } -->

## Presentation Verification

### In simple (not enveloped) format

In this case, the SD-JWT is expected to be in Combined Presentation format.
Verifier should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT. Also, if verification includes Key Binding, the Verifier must also
know a how the public key of the Holder was included in the SD-JWT and which algorithm
the Holder used to sign the `Key Binding JWT`

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
-->

```kotlin
val verifiedPresentationSdJwt: SdJwt.Presentation<JwtAndClaims> = run {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val jwtSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()

    val unverifiedPresentationSdJwt = loadSdJwt("/examplePresentationSdJwt.txt")
    SdJwtVerifier.verifyPresentation(
        jwtSignatureVerifier = jwtSignatureVerifier,
        keyBindingVerifier = KeyBindingVerifier.MustNotBePresent,
        unverifiedSdJwt = unverifiedPresentationSdJwt,
    ).getOrThrow()
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExamplePresentationSdJwtVerification01.kt).

<!--- TEST verifiedPresentationSdJwt.prettyPrint { it.second } -->

Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
presentation scenario which includes key binding

### In enveloped format

In this case, the SD-JWT is expected to be in envelope format.
Verifier should know
- the public key of the Issuer and the algorithm used by the Issuer to sign the SD-JWT.
- the public key and the signing algorithm used by the Holder to sign the envelope JWT, since the envelope acts
  like a proof of possession (replacing the key binding JWT)

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
import java.time.*
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
-->

```kotlin
val verifiedEnvelopedSdJwt: SdJwt.Presentation<JwtAndClaims> = run {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val issuerSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()

    val holderKeyPair = loadRsaKey("/exampleHolderKey.json")
    val holderSignatureVerifier = RSASSAVerifier(holderKeyPair).asJwtVerifier()
        .and { claims ->
            claims["nonce"] == JsonPrimitive("nonce")
        }

    val unverifiedEnvelopedSdJwt = loadJwt("/exampleEnvelopedSdJwt.txt")

    SdJwtVerifier.verifyEnvelopedPresentation(
        sdJwtSignatureVerifier = issuerSignatureVerifier,
        envelopeJwtVerifier = holderSignatureVerifier,
        clock = Clock.systemDefaultZone(),
        iatOffset = 3650.days.toJavaDuration(),
        expectedAudience = "verifier",
        unverifiedEnvelopeJwt = unverifiedEnvelopedSdJwt,
    ).getOrThrow()
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleEnvelopedPresentationSdJwtVerification01.kt).

<!--- TEST verifiedEnvelopedSdJwt.prettyPrint { it.second } -->

## Recreate original claims

Given an `SdJwt`, either issuance or presentation, the original claims used to produce the SD-JWT can be
recreated. This includes the claims that are always disclosed (included in the JWT part of the SD-JWT) having
the digests replaced by selectively disclosable claims found in disclosures.

<!--- INCLUDE
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jose.jwk.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
-->

```kotlin
val claims: Claims = run {
    val issuerKeyPair: RSAKey = loadRsaKey("/examplesIssuerKey.json")
    val sdJwt: SdJwt.Issuance<NimbusSignedJWT> =
        signedSdJwt(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256) {
            plain {
                sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
                iss("https://example.com/issuer")
                iat(1516239022)
                exp(1735689661)
            }
            structured("address") {
                sd {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }
    sdJwt.recreateClaims { jwt -> jwt.jwtClaimsSet.asClaims() }
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleRecreateClaims01.kt).

<!--- TEST println(claims) -->

The claims contents would be

```json
{
  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
  "address": {
    "street_address": "Schulstr. 12",
    "locality": "Schulpforta",
    "region": "Sachsen-Anhalt",
    "country": "DE"
  },
  "iss": "https://example.com/issuer",
  "exp": 1735689661,
  "iat": 1516239022
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

- [Example 1: Flat SD-JWT](docs/examples/example-flat-sd-jwt-01.md)
- [Example 2: Structured SD-JWT](docs/examples/example-structured-sd-jwt-01.md)
- [Example 3: SD-JWT with Recursive Disclosures](docs/examples/example-recursive-sd-jwt-01.md)
- [Appendix 1 - Example 2: Handling Structured Claims](docs/examples/example-handling-structure-claims-01.md)
- [Appendix 2 - Example 3: Complex Structured SD-JWT](docs/examples/example-complex-structured-sd-jwt-01.md)
- [Appendix 3 - Example 4a: SD-JWT-based Verifiable Credentials (SD-JWT VC)](docs/examples/example-sd-jwt-vc-01.md)

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
