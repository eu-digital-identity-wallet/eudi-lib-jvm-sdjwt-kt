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
import kotlinx.serialization.json.JsonObject

/**
 * An SD-JWT-VC specific verifier
 *
 */
interface SdJwtVcVerifier<out JWT> {

    /**
     * Verifies an SD-JWT serialized using compact serialization.
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT, or to Verifier that wants to verify
     * a presented SD-JWT in case the KB-JWT [must not be present][KeyBindingVerifier.MustNotBePresent].
     *
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.jwt] as both string and decoded payload
     */
    suspend fun verify(unverifiedSdJwt: String): Result<SdJwt<JWT>>

    /**
     * Verifies an SD-JWT serialized using JWS JSON serialization (either general or flattened format) as defined by RFC7515
     * and extended by SD-JWT specification.
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT, or to Verifier that wants to verify
     * a presented SD-JWT in case the KB-JWT [must not be present][KeyBindingVerifier.MustNotBePresent].
     *
     * @param unverifiedSdJwt the SD-JWT to be verified.
     * A JSON Object that is expected to be in general
     * or flatten form as defined in RFC7515 and extended by SD-JWT specification.
     * @return the verified SD-JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.jwt] as both string and decoded payload
     */
    suspend fun verify(unverifiedSdJwt: JsonObject): Result<SdJwt<JWT>>

    /**
     * Verifies a SD-JWT+KB serialized using compact serialization.
     * Typically, this is useful to Verifier that want to verify presentation SD-JWT communicated by Holders.
     *
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @param challenge verifier's challenge, expected to be found in KB-JWT (signed by wallet)
     * @return the verified SD-JWT and the KeyBinding JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.jwt] and KeyBinding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verify(unverifiedSdJwt: String, challenge: JsonObject?): Result<SdJwtAndKbJwt<JWT>>

    /**
     * Verifies a SD-JWT+KB in JWS JSON serialization.
     * Typically, this is useful to Verifier that want to verify presentation SD-JWT communicated by Holders
     *
     * @param unverifiedSdJwt the SD-JWT to be verified in JWS JSON
     * @param challenge verifier's challenge, expected to be found in KB-JWT (signed by wallet)
     * @return the verified SD-JWT and KeyBinding JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.jwt] and KeyBinding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verify(unverifiedSdJwt: JsonObject, challenge: JsonObject?): Result<SdJwtAndKbJwt<JWT>>
}

fun <JWT, JWT1> SdJwtVcVerifier<JWT>.map(f: (JWT) -> JWT1): SdJwtVcVerifier<JWT1> {
    return object : SdJwtVcVerifier<JWT1> {
        override suspend fun verify(unverifiedSdJwt: String): Result<SdJwt<JWT1>> =
            this@map.verify(unverifiedSdJwt).map { sdJwt -> sdJwt.map(f) }

        override suspend fun verify(unverifiedSdJwt: JsonObject): Result<SdJwt<JWT1>> =
            this@map.verify(unverifiedSdJwt).map { sdJwt -> sdJwt.map(f) }

        override suspend fun verify(
            unverifiedSdJwt: String,
            challenge: JsonObject?,
        ): Result<SdJwtAndKbJwt<JWT1>> =
            this@map.verify(unverifiedSdJwt, challenge).map { it.map(f) }

        override suspend fun verify(
            unverifiedSdJwt: JsonObject,
            challenge: JsonObject?,
        ): Result<SdJwtAndKbJwt<JWT1>> =
            this@map.verify(unverifiedSdJwt, challenge).map { it.map(f) }
    }
}

/**
 * SD-JWT VC verification errors.
 */
sealed interface SdJwtVcVerificationError {

    /**
     * Verification errors regarding the resolution of the Issuer's key or the verification of the Issuer's signature.
     */
    sealed interface IssuerKeyVerificationError : SdJwtVcVerificationError {

        /**
         * Indicates the key verification methods is not supported.
         *
         * @property method one of 'issuer-metadata', 'x5c', or 'did'
         */
        data class UnsupportedVerificationMethod(val method: String) : IssuerKeyVerificationError

        /**
         * Indicates an error while trying to resolve the Issuer's metadata.
         */
        data class IssuerMetadataResolutionFailure(val cause: Throwable? = null) : IssuerKeyVerificationError

        /**
         * Indicates the leaf certificate of the 'x5c' certificate chain is not trusted.
         */
        data class UntrustedIssuerCertificate(val reason: String? = null) : IssuerKeyVerificationError

        /**
         * DID resolution failed.
         */
        data class DIDLookupFailure(val message: String, val cause: Throwable? = null) : IssuerKeyVerificationError

        /**
         * Indicates a key source for the Issuer could not be determined.
         */
        data object CannotDetermineIssuerVerificationMethod : IssuerKeyVerificationError
    }
}

interface SdJwtVcVerifierFactory<JWT> {
    fun create(
        jwtSignatureVerifier: SdJwtVcJwtSignatureVerifier<JWT>,
        resolveTypeMetadata: ResolveTypeMetadata?,
    ): SdJwtVcVerifier<JWT>

    fun <JWT1> transform(
        f1: (JWT1) -> JWT,
        f2: (JWT) -> JWT1,
    ): SdJwtVcVerifierFactory<JWT1> = object : SdJwtVcVerifierFactory<JWT1> {
        override fun create(
            jwtSignatureVerifier: SdJwtVcJwtSignatureVerifier<JWT1>,
            resolveTypeMetadata: ResolveTypeMetadata?,
        ): SdJwtVcVerifier<JWT1> = this@SdJwtVcVerifierFactory.create(jwtSignatureVerifier.map(f1), resolveTypeMetadata).map(f2)
    }
}
