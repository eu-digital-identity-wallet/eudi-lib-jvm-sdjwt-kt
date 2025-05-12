<!--- TEST_NAME ExampleReadMeTest01 -->

# EUDI SD-JWT

:heavy_exclamation_mark: **Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Table of contents

* [Overview](#overview)
* [Installation](#installation)
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

## Installation
```
// Include library in dependencies in build.gradle.kts
dependencies {
    implementation("eu.europa.ec.euidw:eudi-lib-jvm-sdjwt-kt:$version")
}
```

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
import kotlinx.coroutines.runBlocking
-->

```kotlin
val issuedSdJwt: String = runBlocking {
    val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
    val sdJwtSpec = sdJwt {
        claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        claim("iss", "https://example.com/issuer")
        claim("iat", 1516239022)
        claim("exp", 1735689661)
        objClaim("address") {
            sdClaim("street_address", "Schulstr. 12")
            sdClaim("locality", "Schulpforta")
            sdClaim("region", "Sachsen-Anhalt")
            sdClaim("country", "DE")
        }
    }
    with(NimbusSdJwtOps) {
        val issuer = issuer(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
        issuer.issue(sdJwtSpec).getOrThrow().serialize()
    }
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleIssueSdJw01.kt).

<!--- TEST println(issuedSdJwt) -->

> [!TIP]
> Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
> issuance scenario, including adding to the SD-JWT, holder public key, to leverage key binding.

## Holder Verification

`Holder` must know:

- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

<!--- INCLUDE
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.runBlocking
-->

```kotlin
val verifiedIssuanceSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
        val jwtSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()
        val unverifiedIssuanceSdJwt = loadSdJwt("/exampleIssuanceSdJwt.txt")
        verify(jwtSignatureVerifier, unverifiedIssuanceSdJwt).getOrThrow()
    }
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExampleIssuanceSdJwtVerification01.kt).

<!--- TEST verifiedIssuanceSdJwt.prettyPrint() -->

## Holder Presentation

In this case, a `Holder` of an SD-JWT issued by an `Issuer`, wants to create a presentation for a `Verifier`.
The `Holder` should know which of the selectively disclosed claims to include in the presentation.
The selectively disclosed claims to include in the presentation are expressed using Claim Paths as per 
[SD-JWT VC draft 6](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-06.html#name-claim-path).

<!--- INCLUDE
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import kotlinx.coroutines.runBlocking
-->

```kotlin
val presentationSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val issuedSdJwt = run {
            val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
            val sdJwtSpec = sdJwt {
                claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
                claim("iss", "https://example.com/issuer")
                claim("iat", 1516239022)
                claim("exp", 1735689661)
                sdObjClaim("address") {
                    sdClaim("street_address", "Schulstr. 12")
                    sdClaim("locality", "Schulpforta")
                    sdClaim("region", "Sachsen-Anhalt")
                    sdClaim("country", "DE")
                }
            }
            val issuer = issuer(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
            issuer.issue(sdJwtSpec).getOrThrow()
        }

        val addressPath = ClaimPath.claim("address")
        val claimsToInclude = setOf(addressPath.claim("region"), addressPath.claim("country"))
        issuedSdJwt.present(claimsToInclude)!!
    }
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

## Presentation Verification

### Using compact serialization

Verifier should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT. Also, if verification includes Key Binding, the Verifier must also
know how the public key of the Holder was included in the SD-JWT and which algorithm
the Holder used to sign the `Key Binding JWT`

<!--- INCLUDE
import com.nimbusds.jose.crypto.*
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.*
-->

```kotlin
val verifiedPresentationSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val issuerKeyPair = loadRsaKey("/examplesIssuerKey.json")
        val jwtSignatureVerifier = RSASSAVerifier(issuerKeyPair).asJwtVerifier()
        val unverifiedPresentationSdJwt = loadSdJwt("/examplePresentationSdJwt.txt")
        verify(
            jwtSignatureVerifier,
            unverifiedPresentationSdJwt,
        ).getOrThrow()
    }
}
```

> You can get the full code [here](src/test/kotlin/eu/europa/ec/eudi/sdjwt/examples/ExamplePresentationSdJwtVerification01.kt).

Library provides various variants of the above method that:

- Preserve the KB-JWT, if present, to the successful outcome of a verification
- Accept the unverified SD-JWT serialized in JWS JSON  

<!--- TEST verifiedPresentationSdJwt.prettyPrint() -->

Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
presentation scenario which includes key binding

## Recreate original claims

Given an `SdJwt`, either issuance or presentation, the original claims used to produce the SD-JWT can be
recreated. This includes the claims that are always disclosed (included in the JWT part of the SD-JWT) having
the digests replaced by selectively disclosable claims found in disclosures.

<!--- INCLUDE
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
-->

```kotlin
val claims: JsonObject = runBlocking {
    val issuerKeyPair: RSAKey = loadRsaKey("/examplesIssuerKey.json")
    val sdJwt: SdJwt<SignedJWT> = run {
        val spec = sdJwt {
            claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
            claim("iss", "https://example.com/issuer")
            claim("iat", 1516239022)
            claim("exp", 1735689661)
            objClaim("address") {
                sdClaim("street_address", "Schulstr. 12")
                sdClaim("locality", "Schulpforta")
                sdClaim("region", "Sachsen-Anhalt")
                sdClaim("country", "DE")
            }
        }
        val issuer = NimbusSdJwtOps.issuer(signer = RSASSASigner(issuerKeyPair), signAlgorithm = JWSAlgorithm.RS256)
        issuer.issue(spec).getOrThrow()
    }

    with(NimbusSdJwtOps) {
        sdJwt.recreateClaims(visitor = null)
    }
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
elements, such as `sdJwt{}`, `plain{}` e.t,c, accept
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
    objClaim("address", minimumDigests = 10) {
        // This affects the nested array of the digests that will
        // have at list 10 digests.
    }

    sdObjClaim("address1", minimumDigests = 8) {
        // This will affect the digests array that will be found
        // in the disclosure of this recursively disclosable item
        // the whole object will be embedded in its parent
        // as a single digest
    }

    arrClaim("evidence", minimumDigests = 2) {
        // Array will have at least 2 digests
        // regardless of its elements
    }

    sdArrClaim("evidence1", minimumDigests = 2) {
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
verifying an SD-JWT VC and an SD-JWT+KB VC (including verification of the Key Binding JWT).

Example:

<!--- INCLUDE
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.X509CertUtils
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.RFC7519
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.sdJwt
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.URL
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.time.Duration.Companion.days
-->

```kotlin
val sdJwtVcVerification = runBlocking {
    val issuer = URL("https://issuer.example.com")
    val key = ECKeyGenerator(Curve.P_521).generate()
    val certificate = run {
        val issuedAt = Clock.System.now()
        val expiresAt = issuedAt.plus(365.days)
        val subject = X500Principal("CN=${issuer.host}")
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(key.toECPrivateKey())
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date.from(issuedAt.toJavaInstant()),
            Date.from(expiresAt.toJavaInstant()),
            subject,
            key.toECPublicKey(),
        ).addExtension(
            Extension.subjectAlternativeName,
            true,
            GeneralNames.getInstance(DERSequence(GeneralName(GeneralName.dNSName, issuer.host))),
        ).build(signer)
        X509CertUtils.parse(holder.encoded)
    }

    with(NimbusSdJwtOps) {
        val sdJwt = run {
            val spec = sdJwt {
                claim(RFC7519.ISSUER, issuer.toExternalForm())
                claim(SdJwtVcSpec.VCT, "urn:credential:sample")
            }

            val signer = issuer(signer = ECDSASigner(key), signAlgorithm = JWSAlgorithm.ES512) {
                type(JOSEObjectType("vc+sd-jwt"))
                x509CertChain(listOf(Base64.encode(certificate.encoded)))
            }
            signer.issue(spec).getOrThrow().serialize()
        }

        val verifier = SdJwtVcVerifier.usingX5c { chain, _ -> chain.firstOrNull() == certificate }
        verifier.verify(sdJwt)
    }
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
