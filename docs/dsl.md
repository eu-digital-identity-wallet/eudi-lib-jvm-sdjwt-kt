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

The `eu.europa.ec.eudi.sdjwt.dsl` package and its sub-packages provide a type-safe, Kotlin-based DSL for creating and manipulating Selective Disclosure JWTs (SD-JWTs). The DSL allows developers to define complex structures with precise control over which parts are selectively disclosable, making it suitable for privacy-preserving credential systems like the European Digital Identity Wallet.

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

The `eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def` package provides support for working with metadata for SD-JWT Verifiable Credentials:

### Creating Object Definitions from Metadata

You can create an SD-JWT object definition from SD-JWT VC metadata:

```kotlin
// Create an SD-JWT object definition from SD-JWT VC metadata
val sdJwtDefinition = SdJwtDefinition.fromSdJwtVcMetadata(
    sdJwtVcMetadata = resolvedTypeMetadata,
    selectivelyDiscloseWhenAllowed = true
)
```

This converts the flat metadata structure into a hierarchical disclosable structure that accurately represents the disclosure and display properties of the credential.

### Extracting Claim Paths

You can extract claim paths from an object definition:

```kotlin
// Extract claim paths from the definition
val claimPaths = sdJwtDefinition.claimPaths()
```

These claim paths can be used for presentation requests or for navigating the credential structure.

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

1. **Dynamic Credential Templates**: Create credential templates based on metadata that can be dynamically adjusted based on issuer requirements.

```kotlin
// Create a credential template from metadata
val credentialTemplate = createTemplateFromMetadata(sdJwtVcMetadata)

// Use the template to create a credential with specific values
val credential = credentialTemplate.createCredential(userValues)
```

2. **Selective Disclosure Policies**: Define policies for what should be selectively disclosed based on metadata attributes.

```kotlin
// Define a policy that makes all PII selectively disclosable
val piiPolicy = SelectiveDisclosurePolicy.Builder()
    .makeSelectivelyDisclosable { metadata -> metadata.isPII }
    .build()

// Apply the policy to create an SD-JWT
val sdJwtSpec = createSdJwtWithPolicy(credentialData, piiPolicy)
```

3. **Credential Transformation**: Transform credentials between different formats while preserving disclosure properties.

```kotlin
// Transform an SD-JWT to a different format (e.g., mDL)
val mdlCredential = sdJwtCredential.transformWithMetadata(
    targetFormat = CredentialFormat.MDL,
    metadataMapper = { metadata -> mapSdJwtMetadataToMdl(metadata) }
)
```

4. **Validation Rules from Metadata**: Generate validation rules based on metadata.

```kotlin
// Generate validation rules from metadata
val validationRules = generateValidationRules(sdJwtVcMetadata)

// Validate a credential against the rules
val validationResult = validationRules.validate(credential)
```

5. **UI Generation**: Automatically generate UI components based on metadata.

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