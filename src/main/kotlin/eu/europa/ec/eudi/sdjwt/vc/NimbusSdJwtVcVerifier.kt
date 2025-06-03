/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

/**
 * Nimbus implementation of [SdJwtVcVerifier].
 *
 * @param jwtSignatureVerifier the [SdJwtVcJwtSignatureVerifier] to use for verification
 * @param resolveTypeMetadata optional resolver for the [Type Metadata][ResolvedTypeMetadata] of a given vct
 */
// TODO: Add resolution of TypeMetadata and verification
internal class NimbusSdJwtVcVerifier(
    private val jwtSignatureVerifier: SdJwtVcJwtSignatureVerifier<NimbusSignedJWT>,
    private val resolveTypeMetadata: ResolveTypeMetadata?,
) : SdJwtVcVerifier<NimbusSignedJWT> {
    private fun keyBindingVerifierForSdJwtVc(challenge: JsonObject?): KeyBindingVerifier.MustBePresentAndValid<NimbusSignedJWT> =
        with(NimbusSdJwtOps) {
            KeyBindingVerifier.mustBePresentAndValid(HolderPubKeyInConfirmationClaim, challenge)
        }

    override suspend fun verify(unverifiedSdJwt: String): Result<SdJwt<NimbusSignedJWT>> =
        NimbusSdJwtOps.verify(jwtSignatureVerifier, unverifiedSdJwt)

    override suspend fun verify(unverifiedSdJwt: JsonObject): Result<SdJwt<NimbusSignedJWT>> =
        NimbusSdJwtOps.verify(jwtSignatureVerifier, unverifiedSdJwt)

    override suspend fun verify(
        unverifiedSdJwt: String,
        challenge: JsonObject?,
    ): Result<SdJwtAndKbJwt<NimbusSignedJWT>> = coroutineScope {
        val keyBindingVerifier = keyBindingVerifierForSdJwtVc(challenge)
        NimbusSdJwtOps.verify(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt)
    }

    override suspend fun verify(
        unverifiedSdJwt: JsonObject,
        challenge: JsonObject?,
    ): Result<SdJwtAndKbJwt<NimbusSignedJWT>> = coroutineScope {
        val keyBindingVerifier = keyBindingVerifierForSdJwtVc(challenge)
        NimbusSdJwtOps.verify(jwtSignatureVerifier, keyBindingVerifier, unverifiedSdJwt)
    }
}

/**
 * Nimbus implementations of [SdJwtVcVerifierFactory].
 */
internal object NimbusSdJwtVcVerifierFactory : SdJwtVcVerifierFactory<NimbusSignedJWT> {
    override fun create(
        jwtSignatureVerifier: SdJwtVcJwtSignatureVerifier<NimbusSignedJWT>,
        resolveTypeMetadata: ResolveTypeMetadata?,
    ): SdJwtVcVerifier<NimbusSignedJWT> = NimbusSdJwtVcVerifier(jwtSignatureVerifier, resolveTypeMetadata)
}
