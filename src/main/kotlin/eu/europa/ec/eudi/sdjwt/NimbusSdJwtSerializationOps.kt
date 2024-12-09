package eu.europa.ec.eudi.sdjwt


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT


interface NimbusSdJwtSerializationOps {

    fun SdJwt<NimbusSignedJWT>.serialize(): String = with(defaultOps) { serialize() }

    fun SdJwt<NimbusSignedJWT>.asJwsJsonObject(option: JwsSerializationOption, kbJwt: Jwt?): JsonObject =
        with(defaultOps) { asJwsJsonObject(option, kbJwt) }

    /**
     * Serializes a [SdJwt.Presentation] with a Key Binding JWT.
     *
     * @param hashAlgorithm [HashAlgorithm] to be used for generating the [SdJwtDigest] that will be included
     * in the generated Key Binding JWT
     * @param keyBindingSigner function used to sign the generated Key Binding JWT
     * @param claimSetBuilderAction a function that can be used to further customize the claims
     * of the generated Key Binding JWT.
     * @param JWT the type representing the JWT part of the SD-JWT
     * @receiver the SD-JWT to be serialized
     * @return the serialized SD-JWT including the generated Key Binding JWT
     */
    suspend fun SdJwt.Presentation<NimbusSignedJWT>.serializeWithKeyBinding(
        hashAlgorithm: HashAlgorithm,
        keyBindingSigner: KeyBindingSigner,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    ): Result<String> =
        serializedAndKeyBinding(hashAlgorithm, keyBindingSigner, claimSetBuilderAction)
            .map { (sdJwt, kbJwt) -> "$sdJwt$kbJwt" }

    suspend fun SdJwt.Presentation<NimbusSignedJWT>.asJwsJsonObjectWithKeyBinding(
        option: JwsSerializationOption,
        hashAlgorithm: HashAlgorithm,
        keyBindingSigner: KeyBindingSigner,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    ): Result<JsonObject> =
        serializedAndKeyBinding(hashAlgorithm, keyBindingSigner, claimSetBuilderAction)
            .map { (_, kbJwt) -> asJwsJsonObject(option, kbJwt) }

    private suspend fun SdJwt.Presentation<NimbusSignedJWT>.serializedAndKeyBinding(
        hashAlgorithm: HashAlgorithm,
        keyBindingSigner: KeyBindingSigner,
        claimSetBuilderAction: NimbusJWTClaimsSet.Builder.() -> Unit,
    ): Result<Pair<String, Jwt>> = runCatching {
        // Serialize the presentation SD-JWT with no Key binding
        val presentationSdJwt = serialize()
        // Calculate its digest
        val sdJwtDigest = SdJwtDigest.digest(hashAlgorithm, presentationSdJwt).getOrThrow()
        // Create the Key Binding JWT, sign it and serialize it
        val kbJwt = withContext(Dispatchers.IO) {
            kbJwt(keyBindingSigner, claimSetBuilderAction, sdJwtDigest).serialize()
        }
        presentationSdJwt to kbJwt
    }

    companion object : NimbusSdJwtSerializationOps {
        private val defaultOps = SdJwtSerializationOps<NimbusSignedJWT>({ jwt -> jwt.serialize() })
    }
}