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

import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.SdJwtVcVerifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface X509CertificateTrust<in X509Chain> {
    suspend fun isTrusted(chain: X509Chain, claimSet: JsonObject): Boolean

    fun <X509Chain1> contraMap(convert: (X509Chain1) -> X509Chain): X509CertificateTrust<X509Chain1> =
        X509CertificateTrust { chain -> isTrusted(convert(chain)) }

    companion object {
        val None: X509CertificateTrust<*> = X509CertificateTrust<Any> { _, _ -> false }

        fun <X509Chain> usingVct(trust: suspend (X509Chain, String) -> Boolean): X509CertificateTrust<X509Chain> =
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

interface SdJwtVcVerifierFactory<out JWT, in JWK, out X509Chain> {

    /**
     * Creates a new [SdJwtVcVerifier] with SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust enabled.
     */
    fun usingX5c(x509CertificateTrust: X509CertificateTrust<X509Chain>): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with DID resolution enabled.
     */
    fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK>): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust, and SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust<X509Chain>,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<JWT>

    fun <JWT1, JWK1, X509Chain1> transform(
        convertJwt: (JWT) -> JWT1,
        convertJwk: (JWK1) -> JWK,
        convertX509Chain: (X509Chain) -> X509Chain1,
    ): SdJwtVcVerifierFactory<JWT1, JWK1, X509Chain1> =
        object : SdJwtVcVerifierFactory<JWT1, JWK1, X509Chain1> {
            override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JWT1> =
                this@SdJwtVcVerifierFactory.usingIssuerMetadata(httpClientFactory).map(convertJwt)

            override fun usingX5c(x509CertificateTrust: X509CertificateTrust<X509Chain1>): SdJwtVcVerifier<JWT1> =
                this@SdJwtVcVerifierFactory.usingX5c(x509CertificateTrust.contraMap(convertX509Chain)).map(convertJwt)

            override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK1>): SdJwtVcVerifier<JWT1> =
                this@SdJwtVcVerifierFactory.usingDID(didLookup.map(convertJwk)).map(convertJwt)

            override fun usingX5cOrIssuerMetadata(
                x509CertificateTrust: X509CertificateTrust<X509Chain1>,
                httpClientFactory: KtorHttpClientFactory,
            ): SdJwtVcVerifier<JWT1> =
                this@SdJwtVcVerifierFactory.usingX5cOrIssuerMetadata(
                    httpClientFactory = httpClientFactory,
                    x509CertificateTrust = x509CertificateTrust.contraMap(convertX509Chain),
                ).map(convertJwt)
        }
}
