# Module EUDI SD-JWT

`eudi-lib-jvm-sdjwt-kt` offers a DSL (domain-specific language) for defining how a set of claims should be made selectively
disclosable.

Library implements [SD-JWT draft 12](https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-12.html)
is implemented in Kotlin, targeting JVM.

Library's SD-JWT DSL leverages the DSL provided by
[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library for defining JSON elements

## eu.europa.ec.eudi.sdjwt

### Use cases supported

- [Issuance](#issuance): As an Issuer use the library to issue a SD-JWT
- [Holder Verification](#holder-verification): As Holder verify a SD-JWT issued by an Issuer
- [Holder Presentation](#holder-presentation): As a Holder of a SD-JWT issued by an Issuer, create a presentation for a Verifier
- [Presentation Verification](#presentation-verification): As a Verifier verify SD-JWT
- [Recreate initial claims](#recreate-original-claims): Given a SD-JWT recreate the original claims

#### Issuance

To issue a SD-JWT, an `Issuer` should have:

- Decided on how the issued claims will be selectively disclosed
- Whether to use decoy digests or not
- An appropriate signing key pair
- optionally, decided if and how to include the holder's public key in the SD-JWT

In the example below, the Issuer decides to issue an SD-JWT as follows:

- Includes in plain standard JWT claims (`sub`,`iss`, `iat`, `exp`)
- Makes selectively disclosable a claim named `address` using structured disclosure. This allows individually
  disclosing every subclaim of `address`
- Uses his RSA key pair to sign the SD-JWT

```kotlin
val issuedSdJwt: String = runBlocking {
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
        val issuer = issuer(signer = ECDSASigner(issuerEcKeyPair), signAlgorithm = JWSAlgorithm.ES256)
        issuer.issue(sdJwtSpec).getOrThrow().serialize()
    }
}
```

> [!TIP]
> Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
> issuance scenario, including adding to the SD-JWT, holder public key, to leverage key binding.

#### Holder Verification

`Holder` must know:

- the public key of the `Issuer` and the algorithm used by the Issuer to sign the SD-JWT

```kotlin
val verifiedIssuanceSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val jwtSignatureVerifier = RSASSAVerifier(issuerRsaKeyPair).asJwtVerifier()
        val unverifiedIssuanceSdJwt = loadSdJwt("/exampleIssuanceSdJwt.txt")
        verify(jwtSignatureVerifier, unverifiedIssuanceSdJwt).getOrThrow()
    }
}
```

#### Holder Presentation

In this case, a `Holder` of an SD-JWT issued by an `Issuer`, wants to create a presentation for a `Verifier`.
The `Holder` should know which of the selectively disclosed claims to include in the presentation.
The selectively disclosed claims to include in the presentation are expressed using Claim Paths as per
[SD-JWT VC draft 6](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-06.html#name-claim-path).

```kotlin
val presentationSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val issuedSdJwt = run {
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
            val issuer = issuer(signer = RSASSASigner(issuerRsaKeyPair), signAlgorithm = JWSAlgorithm.RS256)
            issuer.issue(sdJwtSpec).getOrThrow()
        }

        val addressPath = ClaimPath.claim("address")
        val claimsToInclude = setOf(addressPath.claim("region"), addressPath.claim("country"))
        issuedSdJwt.present(claimsToInclude)!!
    }
}
```

In the above example, the `Holder` has decided to disclose the claims `region` and `country` of the selectively
disclosed claim `address`.

The resulting presentation will contain 3 disclosures:
* 1 disclosure for the selectively disclosed claim `address`
* 1 disclosure for the selectively disclosed claim `region`
* 1 disclosure for the selectively disclosed claim `country`

This is because to disclose either the claim `region` or the claim `country`, the claim `address` must be
disclosed as well.

#### Presentation Verification

##### Using compact serialization

Verifier should know the public key of the Issuer and the algorithm used by the Issuer
to sign the SD-JWT. Also, if verification includes Key Binding, the Verifier must also
know how the public key of the Holder was included in the SD-JWT and which algorithm
the Holder used to sign the `Key Binding JWT`

```kotlin
val verifiedPresentationSdJwt: SdJwt<SignedJWT> = runBlocking {
    with(NimbusSdJwtOps) {
        val jwtSignatureVerifier = RSASSAVerifier(issuerRsaKeyPair).asJwtVerifier()
        val unverifiedPresentationSdJwt = loadSdJwt("/examplePresentationSdJwt.txt")
        verify(
            jwtSignatureVerifier,
            unverifiedPresentationSdJwt,
        ).getOrThrow()
    }
}
```

Library provides various variants of the above method that:

- Preserve the KB-JWT, if present, to the successful outcome of a verification
- Accept the unverified SD-JWT serialized in JWS JSON

Please check [KeyBindingTest](src/test/kotlin/eu/europa/ec/eudi/sdjwt/KeyBindingTest.kt) for a more advanced
presentation scenario which includes key binding

#### Recreate original claims

Given an `SdJwt`, either issuance or presentation, the original claims used to produce the SD-JWT can be
recreated. This includes the claims that are always disclosed (included in the JWT part of the SD-JWT) having
the digests replaced by selectively disclosable claims found in disclosures.

```kotlin
val claims: JsonObject = runBlocking {
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
        val issuer = NimbusSdJwtOps.issuer(signer = RSASSASigner(issuerRsaKeyPair), signAlgorithm = JWSAlgorithm.RS256)
        issuer.issue(spec).getOrThrow()
    }

    with(NimbusSdJwtOps) {
        sdJwt.recreateClaimsAndDisclosuresPerClaim().first
    }
}
```

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
