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

import eu.europa.ec.eudi.sdjwt.JwtSignatureVerifier
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface X509CertificateTrust<in X509Chain> {
    suspend fun isTrusted(chain: X509Chain, claimSet: JsonObject): Boolean

    fun <X509Chain1> contraMap(convert: (X509Chain1) -> X509Chain): X509CertificateTrust<X509Chain1> =
        X509CertificateTrust { chain, claimSet -> isTrusted(convert(chain), claimSet) }

    companion object {
        val None: X509CertificateTrust<*> = X509CertificateTrust<Any> { _, _ -> false }

        inline fun <X509Chain> usingVct(crossinline trust: suspend (X509Chain, String) -> Boolean): X509CertificateTrust<X509Chain> =
            X509CertificateTrust { chain, claimSet ->
                val vct = checkNotNull(claimSet[SdJwtVcSpec.VCT]) { "missing '${SdJwtVcSpec.VCT}' claim" }
                trust(chain, vct.jsonPrimitive.content)
            }
    }
}

/**
 * A function to look up public keys from DIDs/DID URLs.
 */
fun interface LookupPublicKeysFromDIDDocument<out JWK> {

    /**
     * Lookup the public keys from a DID document.
     *
     * @param did the identifier of the DID document
     * @param didUrl optional DID URL, that is either absolute or relative to [did], indicating the exact public key
     * to lookup from the DID document
     *
     * @return the matching public keys or null in case lookup fails for any reason
     */
    suspend fun lookup(did: String, didUrl: String?): List<JWK>?

    fun <JWK1> map(convert: (JWK) -> JWK1): LookupPublicKeysFromDIDDocument<JWK1> =
        LookupPublicKeysFromDIDDocument { did, didUrl -> lookup(did, didUrl)?.map(convert) }
}

/**
 * How the Issuer of the Issuer-signed JWT of an SD-JWT VC will be verified.
 */
sealed interface IssuerVerificationMethod<out JWT, out JWK, in X509Chain> {

    /**
     * Using SD-JWT VC Issuer Metadata
     */
    data class UsingIssuerMetadata(val httpClientFactory: KtorHttpClientFactory) : IssuerVerificationMethod<Nothing, Nothing, Any?>

    /**
     * Using X509 Certificate trust
     */
    data class UsingX5c<X509Chain>(
        val x509CertificateTrust: X509CertificateTrust<X509Chain>,
    ) : IssuerVerificationMethod<Nothing, Nothing, X509Chain>

    /**
     * Using DID resolution
     */
    data class UsingDID<JWK>(val didLookup: LookupPublicKeysFromDIDDocument<JWK>) : IssuerVerificationMethod<Nothing, JWK, Any?>

    /**
     * Using X509 Certificate trust or SD-JWT VC Issuer Metadata
     */
    data class UsingX5cOrIssuerMetadata<X509Chain>(
        val x509CertificateTrust: X509CertificateTrust<X509Chain>,
        val httpClientFactory: KtorHttpClientFactory,
    ) : IssuerVerificationMethod<Nothing, Nothing, X509Chain>

    /**
     * Using a custom [JwtSignatureVerifier]
     */
    data class Custom<JWT>(val jwtSignatureVerifier: JwtSignatureVerifier<JWT>) : IssuerVerificationMethod<JWT, Nothing, Any?>

    fun <JWT1, JWK1, X509Chain1> transform(
        convertToJwt: (JWT) -> JWT1,
        convertToJwk: (JWK) -> JWK1,
        convertFromX509Chain: (X509Chain1) -> X509Chain,
    ): IssuerVerificationMethod<JWT1, JWK1, X509Chain1> =
        when (this) {
            is UsingIssuerMetadata -> UsingIssuerMetadata(httpClientFactory)
            is UsingX5c -> UsingX5c(x509CertificateTrust.contraMap(convertFromX509Chain))
            is UsingDID -> UsingDID(didLookup.map(convertToJwk))
            is UsingX5cOrIssuerMetadata -> UsingX5cOrIssuerMetadata(x509CertificateTrust.contraMap(convertFromX509Chain), httpClientFactory)
            is Custom -> Custom(jwtSignatureVerifier.map(convertToJwt))
        }

    companion object {
        fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): UsingIssuerMetadata = UsingIssuerMetadata(httpClientFactory)
        fun <X509Chain> usingX5c(
            x509CertificateTrust: X509CertificateTrust<X509Chain>,
        ): UsingX5c<X509Chain> = UsingX5c(x509CertificateTrust)
        fun <JWK> usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK>): UsingDID<JWK> = UsingDID(didLookup)
        fun <X509Chain> usingX5cOrIssuerMetadata(
            x509CertificateTrust: X509CertificateTrust<X509Chain>,
            httpClientFactory: KtorHttpClientFactory,
        ): UsingX5cOrIssuerMetadata<X509Chain> = UsingX5cOrIssuerMetadata(x509CertificateTrust, httpClientFactory)
        fun <JWT> usingCustom(jwtSignatureVerifier: JwtSignatureVerifier<JWT>): Custom<JWT> = Custom(jwtSignatureVerifier)
    }
}

/**
 * Defines a Verifier's policy concerning Type Metadata.
 */
sealed interface TypeMetadataPolicy {

    /**
     * Type Metadata are not required.
     * Failure to successfully resolve Type Metadata for any Vct, does not result in the rejection of the SD-JWT VC.
     */
    data object Optional : TypeMetadataPolicy

    /**
     * Type Metadata are always required for all Vcts.
     * Failure to successfully resolve Type Metadata for any Vct, results in the rejection of the SD-JWT VC.
     */
    data object AlwaysRequired : TypeMetadataPolicy

    /**
     * Type Metadata are always required for the specified Vcts.
     * Failure to successfully resolve Type Metadata for any of the specified Vcts, results in the rejection of the SD-JWT VC.
     */
    data class RequiredFor(val vcts: Set<Vct>) : TypeMetadataPolicy {
        init {
            require(vcts.isNotEmpty()) { "at least one VCT must be specified" }
        }
    }
}

interface SdJwtVcVerifierFactory<JWT, in JWK, out X509Chain> {

    operator fun invoke(
        issuerVerificationMethod: IssuerVerificationMethod<JWT, JWK, X509Chain>,
        resolveTypeMetadata: ResolveTypeMetadata?,
        jsonSchemaValidator: JsonSchemaValidator?,
        typeMetadataPolicy: TypeMetadataPolicy,
    ): SdJwtVcVerifier<JWT>

    fun <JWT1, JWK1, X509Chain1> transform(
        convertFromJwt: (JWT1) -> JWT,
        convertToJwt: (JWT) -> JWT1,
        convertFromJwk: (JWK1) -> JWK,
        convertToX509Chain: (X509Chain) -> X509Chain1,
    ): SdJwtVcVerifierFactory<JWT1, JWK1, X509Chain1> =
        object : SdJwtVcVerifierFactory<JWT1, JWK1, X509Chain1> {
            override fun invoke(
                issuerVerificationMethod: IssuerVerificationMethod<JWT1, JWK1, X509Chain1>,
                resolveTypeMetadata: ResolveTypeMetadata?,
                jsonSchemaValidator: JsonSchemaValidator?,
                typeMetadataPolicy: TypeMetadataPolicy,
            ): SdJwtVcVerifier<JWT1> =
                this@SdJwtVcVerifierFactory.invoke(
                    issuerVerificationMethod.transform(convertFromJwt, convertFromJwk, convertToX509Chain),
                    resolveTypeMetadata,
                    jsonSchemaValidator,
                    typeMetadataPolicy,
                ).map(convertToJwt)
        }
}
