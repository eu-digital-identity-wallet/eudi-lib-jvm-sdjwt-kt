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
    * [Holder Presentation](#holder-presentation)
    * [Presentation Verification](#presentation-verification)
    * [Recreate initial claims](#recreate-original-claims)
* [Decoy digests](#decoy-digests)
* [DSL Examples](#dsl-examples)
* [SD-JWT VC support](#sd-jwt-vc-support)
* [How to contribute](#how-to-contribute)
* [License](#license)

## Overview

This is a library offering a DSL (domain-specific language) for defining how a set of claims should be made selectively
disclosable.

Library implements [SD-JWT draft 12](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-12.html)
is implemented in Kotlin, targeting JVM.

Library's SD-JWT DSL leverages the DSL provided by
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements

## Use cases supported

- [Issuance](#issuance): As an Issuer use the library to issue a SD-JWT
- [Holder Verification](#holder-verification): As Holder verify a SD-JWT issued by an Issuer
- [Holder Presentation](#holder-presentation): As a Holder of a SD-JWT issued by an Issuer, create a presentation for a Verifier
- [Presentation Verification](#presentation-verification): As a Verifier verify SD-JWT
- [Recreate initial claims](#recreate-original-claims): Given a SD-JWT recreate the original claims

## Issuance

To issue a SD-JWT, an `Issuer` should have:

- Decided on how the issued claims will be selectively disclosed (check [DSL examples](#dsl-examples))
- Whether to use decoy digests or not
- An appropriate signing key pair
- optionally, decided if and how to include the holder's public key in the SD-JWT

In the example below, the Issuer decides to issue an SD-JWT as follows:

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

> [!TIP]
> Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
> issuance scenario, including adding to the SD-JWT, holder public key, to leverage key binding.

## Holder Verification

In this case, the SD-JWT is expected to be in serialized form.

`Holder` must know:

- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.*
-->

```kotlin
val verifiedIssuanceSdJwt: SdJwt.Issuance<JwtAndClaims> = runBlocking {
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

## Holder Presentation

In this case, a `Holder` of an SD-JWT issued by an `Issuer`, wants to create a presentation for a `Verifier`.
The `Holder` should know which of the selectively disclosed claims to include in the presentation.
The selectively disclosed claims to include in the presentation are expressed using JSON Pointers
as per [RFC6901](https://datatracker.ietf.org/doc/html/rfc6901).


<!--- INCLUDE
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jwt.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.*
-->

```kotlin
val presentationSdJwt: SdJwt.Presentation<SignedJWT> = run {
    val issuedSdJwt = run {
        val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
        val sdJwtSpec = sdJwt {
            plain {
                sub("6c5c0a49-b589-431d-bae7-219122a9ec2c")
                iss("https://example.com/issuer")
                iat(1516239022)
                exp(1735689661)
            }
            recursive("address") {
                sd {
                    put("street_address", "Schulstr. 12")
                    put("locality", "Schulpforta")
                    put("region", "Sachsen-Anhalt")
                    put("country", "DE")
                }
            }
        }
        val issuer = SdJwtIssuer.nimbus(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
        issuer.issue(sdJwtSpec).getOrThrow()
    }

    val claimsToInclude = listOf("/address/region", "/address/country")
        .mapNotNull { JsonPointer.parse(it) }
        .toSet()

    issuedSdJwt.present(claimsToInclude)!!
}
```
> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExamplePresentationSdJwt01.kt).

<!--- TEST assertEquals(3, presentationSdJwt.disclosures.size) -->

In the above example, the `Holder` has decided to disclose the claims `region` and `country` of the selectively 
disclosed claim `address`.

The resulting presentation will contain 3 disclosures:
* 1 disclosure for the selectively disclosed claim `address`
* 1 disclosure for the selectively disclosed claim `region`
* 1 disclosure for the selectively disclosed claim `country`

This is because to disclose either the claim `region` or the claim `country`, the claim `address` must be 
disclosed as well.

> [!NOTE]
> Please note that OpenId4VP uses Presentation Exchange, to allow an RP/Verifier to describe the presentation
> requirements, which depends on JSON Path expressions. On the other hand, the `present` function shown above expects
> either a set of JSON Pointers or a JSON Pointer predicate. We consider that bridging those two (JSON Path & Pointer)
> should be left outside the scope of this library.

## Presentation Verification

### In simple format

In this case, the SD-JWT is expected to be in Combined Presentation format.
Verifier should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT. Also, if verification includes Key Binding, the Verifier must also
know how the public key of the Holder was included in the SD-JWT and which algorithm
the Holder used to sign the `Key Binding JWT`

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.*
-->

```kotlin
val verifiedPresentationSdJwt: SdJwt.Presentation<JwtAndClaims> = runBlocking {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val jwtSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()

    val unverifiedPresentationSdJwt = loadSdJwt("/examplePresentationSdJwt.txt")
    val (sdJwt, _) = SdJwtVerifier.verifyPresentation(
        jwtSignatureVerifier = jwtSignatureVerifier,
        keyBindingVerifier = KeyBindingVerifier.MustNotBePresent,
        unverifiedSdJwt = unverifiedPresentationSdJwt,
    ).getOrThrow()
    sdJwt
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExamplePresentationSdJwtVerification01.kt).

Library provides various variants of the above method that:

- Preserve the KB-JWT, if present, to the successful outcome of a verification
- Accept the unverified SD-JWT serialized in JWS JSON  

<!--- TEST verifiedPresentationSdJwt.prettyPrint { it.second } -->

Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
presentation scenario which includes key binding

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
## Decoy digests

By default, the library doesn't add decoy digests to the issued SD-JWT.
If an issuer wants to use digests, it can do so using the DSL.

DSL functions that mark a container composed of potentially selectively disclosable   
elements, such as `sdJwt{}`, `structured{}` e.t,c, accept
an optional parameter named `minimumDigests: Int? = null`.

The issuer can use this parameter to set the minimum number of digests
for the immediate level of this container. Library will make sure that
the underlying digests array will have at minimum a length equal to `digestNumberHint`.

Initially, during issuance, the digests array will contain disclosure digests and if needed,
additional decoy digests to reach the hint provided. If the array
contains more disclosure digests than the hint, no decoys will be added.

<!--- INCLUDE
import eu.europa.ec.eudi.sdjwt.*
-->

```kotlin
val sdJwtWithMinimumDigests = sdJwt(minimumDigests = 5) {
    // This 5 guarantees that at least 5 digests will be found
    // to the digest array, regardless of the content of the SD-JWT
    structured("address", minimumDigests = 10) {
        // This affects the nested array of the digests that will
        // have at list 10 digests.
    }

    recursive("address1", minimumDigests = 8) {
        // This will affect the digests array that will be found
        // in the disclosure of this recursively disclosable item
        // the whole object will be embedded in its parent
        // as a single digest
    }

    sdArray("evidence", minimumDigests = 2) {
        // Array will have at least 2 digests
        // regardless of its elements
    }

    recursiveArray("evidence1", minimumDigests = 2) {
        // Array will have at least 2 digests
        // regardless of its elements
        // the whole array will be embedded in its parent
        // as a single digest
    }
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleSdJwtWithMinimumDigest01.kt).

<!--- TEST println(sdJwtWithMinimumDigests) -->

> [!TIP]
> In addition to the DSL defined hints, the issuer may set a global hint to the `SdJwtFactory`.
> This will be used as a fallback limit for every container of selectively disclosable elements
> that don't explicitly provide a limit.

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
- [Appendix 4 - Example 4b: W3C Verifiable Credentials Data Model v2.0](docs/examples/example-sd-jwt-vc-data-v02-01.md)

## SD-JWT VC support

The library support verifying 
[SD-JWT-based Verifiable Credentials](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-04.html).
More specifically, Issuer-signed JWT Verification Key Validation support is provided by
[SdJwtVcVerifier](src/main/kotlin/eu/europa/ec/eudi/sdjwt/vc/SdJwtVcVerifier.kt).  
Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for code examples of
verifying an Issuance SD-JWT VC and a Presentation SD-JWT VC (including verification of the Key Binding JWT).

Example:

<!--- INCLUDE
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifier
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
-->

```kotlin
val sdJwtVcVerification = runBlocking {
    val key = OctetKeyPairGenerator(Curve.Ed25519).generate()
    val didJwk = "did:jwk:${Base64.UrlSafe.encode(key.toPublicJWK().toJSONString().toByteArray())}"

    val sdJwt = run {
        val spec = sdJwt {
            iss(didJwk)
        }
        val signer = SdJwtIssuer.nimbus(signer = Ed25519Signer(key), signAlgorithm = JWSAlgorithm.EdDSA) {
            type(JOSEObjectType("vc+sd-jwt"))
        }
        signer.issue(spec).getOrThrow()
    }

    val verifier = SdJwtVcVerifier { did, _ ->
        assertEquals(didJwk, did)
        listOf(key.toPublicJWK())
    }
    verifier.verifyIssuance(sdJwt.serialize())
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleSdJwtVcVerification01.kt).

<!--- TEST sdJwtVcVerification.getOrThrow() -->

> [!NOTE]
> Support for OctetKeyPair required the optional dependency **com.google.crypto.tink:tink**.

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
