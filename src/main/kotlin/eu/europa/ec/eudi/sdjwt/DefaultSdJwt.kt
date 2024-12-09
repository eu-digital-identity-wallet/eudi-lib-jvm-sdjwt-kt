package eu.europa.ec.eudi.sdjwt



interface DefaultSdJwtOps : SdJwtVerifier, SdJwtSerializationOps<JwtAndClaims> {

    companion object : DefaultSdJwtOps,
        SdJwtSerializationOps<JwtAndClaims> by SdJwtSerializationOps({(jwt,_)->jwt}){}
}