# EUDI SD-JWT DSL Documentation

This document provides a comprehensive guide to the Domain-Specific Language (DSL) for working with Selective Disclosure JWT (SD-JWT) in the EUDI Wallet Reference Implementation.

## Table of Contents

- [Overview](#overview)
- [Core Concepts](#core-concepts)
- [Basic Usage](#basic-usage)
- [Advanced Features](#advanced-features)
- [Working with Metadata](#working-with-metadata)
- [Examples](#examples)

## Overview

The `eu.europa.ec.eudi.sdjwt.dsl.values` package and its sub-packages provide a type-safe, Kotlin-based DSL for creating and manipulating Selective Disclosure JWTs (SD-JWTs). The DSL allows developers to define complex structures with precise control over which parts are selectively disclosable, making it suitable for privacy-preserving credential systems like the European Digital Identity Wallet.

The DSL approach makes the code more readable and maintainable compared to directly manipulating JSON or other data structures, while still providing the flexibility needed for complex credential scenarios.

## Core Concepts

### Disclosable Elements

The foundation of the DSL is the concept of a `Disclosable<A>` type, which embellishes values with selective disclosure information:

- `NeverSelectively`: Values that are always disclosed (included directly in the JWT)
- `AlwaysSelectively`: Values that are selectively disclosed (included as disclosure digests)

These are implemented as value classes in `Disclosable.kt` and can be created using the unary operators `!` (never selectively disclosed) and `+` (always selectively disclosed).

### Disclosable Structures

The DSL supports three types of disclosable structures:

1. **Primitive Values**: Simple values like strings, numbers, or booleans
2. **Objects**: Map-like structures with named properties
3. **Arrays**: List-like structures of elements

Each structure can contain a mix of always-disclosed and selectively-disclosed elements, and can be nested to any depth.

### Builders

The DSL provides builder classes for constructing disclosable objects and arrays:

- `DisclosableObjectSpecBuilder`: For building objects with named properties
- `DisclosableArraySpecBuilder`: For building arrays of elements

These builders offer methods like `claim()`, `sdClaim()`, `objClaim()`, `sdObjClaim()`, etc., to add different types of claims.

## Basic Usage

### Creating a Simple SD-JWT

The most common way to use the DSL is through the `sdJwt` function, which creates a JSON-based SD-JWT structure:

```kotlin
val sdJwtSpec = sdJwt {
    // Always disclosed claims (included directly in the JWT)
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
    claim("iss", "https://example.com/issuer")
    claim("iat", 1516239022)
    claim("exp", 1735689661)

    // Selectively disclosed claims (included as disclosure digests)
    sdClaim("given_name", "John")
    sdClaim("family_name", "Doe")
}
```

### Working with Nested Objects

You can create nested objects with mixed disclosure properties:

```kotlin
val sdJwtSpec = sdJwt {
    // Regular claims
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")

    // Object with selectively disclosed properties
    objClaim("address") {
        sdClaim("street_address", "Schulstr. 12")
        sdClaim("locality", "Schulpforta")
        sdClaim("region", "Sachsen-Anhalt")
        sdClaim("country", "DE")
    }

    // Selectively disclosed object (the entire object is disclosed as one unit)
    sdObjClaim("credentials") {
        claim("degree", "Bachelor of Science")
        claim("field", "Computer Science")
    }
}
```

### Working with Arrays

Arrays can also be included with mixed disclosure properties:

```kotlin
val sdJwtSpec = sdJwt {
    // Regular array with mixed disclosure elements
    arrClaim("nationalities") {
        claim("US")
        sdClaim("DE")
    }

    // Selectively disclosed array (the entire array is disclosed as one unit)
    sdArrClaim("languages") {
        claim("en")
        claim("de")
    }
}
```

## Advanced Features

### Minimum Digests and Decoys

To enhance privacy by preventing correlation through the number of disclosures, you can specify a minimum number of digests:

```kotlin
val sdJwtSpec = sdJwt(minimumDigests = 5) {
    // This ensures at least 5 digests in the top-level object

    objClaim("address", minimumDigests = 3) {
        // This ensures at least 3 digests in the address object
        sdClaim("street_address", "Schulstr. 12")
        sdClaim("country", "DE")
    }
}
```

If the actual number of selectively disclosed elements is less than the specified minimum, the library will add decoy digests to reach the minimum.

### Mapping Disclosable Structures

The DSL provides a `map` function to transform disclosable structures:

```kotlin
// Transform a disclosable object by applying functions to keys and values
val transformedObject = originalObject.map(
    fK = { key -> key.uppercase() },  // Transform keys to uppercase
    fA = { value -> value.toString() }  // Transform all values to strings
)
```

### Folding Disclosable Structures

The `fold` operation allows you to traverse and accumulate values from disclosable structures:

```kotlin
// Collect all selectively disclosed claim names
val disclosedClaimNames = sdJwtSpec.fold(
    objectHandlers = customObjectHandlers,
    arrayHandlers = customArrayHandlers,
    initial = emptySet<String>(),
    combine = { acc, current -> acc + current }
)
```

## Working with Metadata

The `eu.europa.ec.eudi.sdjwt.dsl.def` package provides support for working with metadata for SD-JWT Verifiable Credentials:

### Creating Object Definitions from Metadata

You can create an SD-JWT object definition from SD-JWT VC metadata:

```kotlin
// Create an SD-JWT object definition from SD-JWT VC metadata
val sdJwtDefinition = SdJwtDefinition.fromSdJwtVcMetadataStrict(
    sdJwtVcMetadata = resolvedTypeMetadata,
    selectivelyDiscloseWhenAllowed = true
)
```

This converts the flat metadata structure into a hierarchical disclosable structure that accurately represents the disclosure and display properties of the credential.

### SD-JWT-VC Templating: Transforming Raw Data with Definitions

The library provides a powerful **templating capability** that generates an **`SdJwtObject` directly from raw JSON data** using an **`SdJwtDefinition` as a template**. This feature automates the process of applying selective disclosure rules to your credential data according to a predefined schema.

Key benefits of this approach:

1. **Simplified Code**: No need to manually call `sdClaim`, `objClaim`, or `arrClaim` for each field
2. **Automatic Processing**: Supply raw credential data as a standard `JsonObject`, and the template handles the rest
3. **Consistent Rules Application**: The `SdJwtDefinition` determines:
   - Which claims to include (only processes claims present in both data and definition)
   - Whether claims should be selectively disclosable (based on the definition)
   - How to handle nested structures (objects and arrays)
   - Automatic inclusion of the required `vct` claim from the definition's metadata

The `DefinitionBasedSdJwtObjectBuilder` class powers this functionality, with the convenient `sdJwtVc` helper function providing a simple interface. You can enable strict validation to ensure your data matches the definition exactly.

Consider the following comparison to illustrate the efficiency of this templating approach:

```kotlin
// Option 1: Utilizing the SdJwtDefinition as a template with raw JSON data
val sdJwtObjectFromTemplate = sdJwtVc(PidDefinition) { // PidDefinition serves as the template
    put("given_name", "Foo")
    put("family_name", "Bar")
    putJsonArray("nationalities") {
        add("GR")
    }
    putJsonObject("age_equal_or_over") { put("18", true) }
    putJsonObject("address") {
        put("country", "GR")
        put("street_address", "12345 Main Street")
    }
}.getOrThrow() // Ensure proper Result handling

// Option 2: Manually constructing the SdJwtObject (equivalent to Option 1 if PidDefinition implies these disclosures)
val manuallyBuiltSdJwtObject = sdJwt {
    claim(SdJwtVcSpec.VCT, PidDefinition.metadata.vct.value) // Manually add vct
    sdClaim("given_name", "Foo") // Manually mark as SD
    sdClaim("family_name", "Bar") // Manually mark as SD
    sdArrClaim("nationalities") { // Manually mark array as SD, then its elements
        sdClaim("GR")
    }
    sdObjClaim("age_equal_or_over") { // Manually mark object as SD
        sdClaim("18", true) // Manually mark inner claim as SD
    }
    sdObjClaim("address") { // Manually mark object as SD
        sdClaim("country", "GR") // Manually mark inner claim as SD
        sdClaim("street_address", "12345 Main Street") // Manually mark inner claim as SD
    }
}

// Both sdJwtObjectFromTemplate and manuallyBuiltSdJwtObject will yield the same underlying SdJwtObject structure,
// assuming PidDefinition accurately specifies the selective disclosure for each claim.
```
As demonstrated, the templating approach substantially reduces boilerplate and potential for errors 
by allowing the `SdJwtDefinition` to govern the transformation, resulting in cleaner and more robust code.

### Validating SD-JWTs Against Definitions

The `DefinitionBasedSdJwtVcValidator` provides a mechanism to validate an SD-JWT (its payload and provided disclosures) against its `SdJwtDefinition`. This ensures that presented credentials conform to the expected structure and disclosure rules.

#### Validation Process

When validating an SD-JWT against a definition, the validator checks:
1. That all required claims are present
2. That claims are disclosed according to the definition (selectively or not)
3. That the structure of claims matches the definition
4. That the credential type (`vct`) matches the one in the definition

#### Possible Validation Errors

The validation process can identify the following types of errors, reported as `DefinitionViolation` instances:

- `DisclosureInconsistencies`: Issues with the disclosures themselves, such as non-unique disclosures or disclosures without matching digests, preventing the successful reconstruction of claims.
- `UnknownClaim`: A claim in the SD-JWT payload or disclosures is not present in the `SdJwtDefinition`.
- `WrongClaimType`: A claim's type (e.g., object, array, or primitive) in the presented SD-JWT does not match its type as defined in the SdJwtDefinition.
- `IncorrectlyDisclosedClaim`: A claim's selective disclosure status (always disclosed vs. selectively disclosed) in the SD-JWT contradicts its definition. For instance, a claim defined as "always selectively disclosable" is found directly in the payload, or vice-versa.
- `MissingRequiredClaim`: According to SD-JWT-VC, `iss` and `vct` claims are required and must be never selectively disclosable.
- `InvalidVct`: The credential has a `vct` claim that is not equal to the one found in the type metadata.

```kotlin
// Assuming you have your sdJwtDefinition, jwtPayload, and disclosures
val sdJwtDefinition = TODO()
val validationResult = with(DefinitionBasedSdJwtVcValidator){
    sdJwtDefinition.validateSdJwtVc(
        jwtPayload = yourJwtPayload,
        disclosures = yourDisclosures,
    )
}

when (validationResult) {
    DefinitionBasedValidationResult.Valid -> println("SD-JWT is valid against the definition.")
    is DefinitionBasedValidationResult.Invalid -> {
        println("SD-JWT validation failed:")
        validationResult.errors.forEach { error ->
            println("- $error")
        }
    }
}
```
### Working with Display Information

The metadata includes display information for claims, which can be used for UI rendering:

```kotlin
// Access display information for a claim
val displayInfo = attributeMetadata.display
if (displayInfo != null) {
    for (display in displayInfo) {
        println("Language: ${display.lang}")
        println("Name: ${display.name}")
        println("Description: ${display.description}")
    }
}
```

### Future Uses of the DSL with Metadata

The DSL's metadata support opens up several future possibilities:

1. **Selective Disclosure Policies**: Define policies for what should be selectively disclosed based on metadata attributes.

```kotlin
// Define a policy that makes all PII selectively disclosable
val piiPolicy = SelectiveDisclosurePolicy.Builder()
    .makeSelectivelyDisclosable { metadata -> metadata.isPII }
    .build()

// Apply the policy to create an SD-JWT
val sdJwtSpec = createSdJwtWithPolicy(credentialData, piiPolicy)
```

2.**Credential Transformation**: Transform credentials between different formats while preserving disclosure properties.

```kotlin
// Transform an SD-JWT to a different format (e.g., mDL)
val mdlCredential = sdJwtCredential.transformWithMetadata(
    targetFormat = CredentialFormat.MDL,
    metadataMapper = { metadata -> mapSdJwtMetadataToMdl(metadata) }
)
```

3.**UI Generation**: Automatically generate UI components based on metadata.

```kotlin
// Generate UI components for displaying a credential
val uiComponents = generateCredentialUI(credential, sdJwtVcMetadata)
```

## Examples

### Example 1: Basic Credential

```kotlin
val sdJwtSpec = sdJwt {
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
    claim("iss", "https://example.com/issuer")
    claim("iat", 1516239022)
    claim("exp", 1735689661)
    sdClaim("given_name", "John")
    sdClaim("family_name", "Doe")
    sdClaim("email", "john.doe@example.com")
}
```

### Example 2: Address Credential with Structured Disclosure

```kotlin
val sdJwtSpec = sdJwt {
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
    claim("iss", "https://example.com/issuer")
    sdObjClaim("address") {
        sdClaim("street_address", "Schulstr. 12")
        sdClaim("locality", "Schulpforta")
        sdClaim("region", "Sachsen-Anhalt")
        sdClaim("country", "DE")
    }
}
```

### Example 3: Complex Credential with Arrays and Nested Objects

```kotlin
val sdJwtSpec = sdJwt {
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
    claim("iss", "https://example.com/issuer")

    sdObjClaim("personal_info") {
        sdClaim("given_name", "John")
        sdClaim("family_name", "Doe")
        sdArrClaim("nationalities") {
            claim("US")
            claim("DE")
        }
    }

    sdObjClaim("education") {
        sdArrClaim("degrees") {
            objClaim {
                claim("degree", "Bachelor")
                claim("field", "Computer Science")
                claim("year", 2015)
            }
            objClaim {
                claim("degree", "Master")
                claim("field", "Data Science")
                claim("year", 2017)
            }
        }
    }
}
```

For more detailed examples, refer to the example files in the [examples](examples) directory.
