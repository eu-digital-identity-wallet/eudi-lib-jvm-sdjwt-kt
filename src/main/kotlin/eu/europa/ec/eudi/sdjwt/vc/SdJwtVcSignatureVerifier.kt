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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JWT signature verifier for the issuer-signed JWT of an SD-JWT VC.
 */
fun interface SdJwtVcJwtSignatureVerifier<out JWT> : JwtSignatureVerifier<JWT>

fun <JWT, JWT1> SdJwtVcJwtSignatureVerifier<JWT>.map(f: (JWT) -> JWT1): SdJwtVcJwtSignatureVerifier<JWT1> =
    SdJwtVcJwtSignatureVerifier { jwt -> checkSignature(jwt)?.let { f(it) } }

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

interface SdJwtVcSignatureVerifierFactory<out JWT, in JWK, out X509Chain> {

    /**
     * Creates a new [JwtSignatureVerifier] with SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcJwtSignatureVerifier<JWT>

    /**
     * Creates a new [JwtSignatureVerifier] with X509 Certificate trust enabled.
     */
    fun usingX5c(x509CertificateTrust: X509CertificateTrust<X509Chain>): SdJwtVcJwtSignatureVerifier<JWT>

    /**
     * Creates a new [JwtSignatureVerifier] with DID resolution enabled.
     */
    fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK>): SdJwtVcJwtSignatureVerifier<JWT>

    /**
     * Creates a new [JwtSignatureVerifier] with X509 Certificate trust, and SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust<X509Chain>,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcJwtSignatureVerifier<JWT>

    fun <JWT1, JWK1, X509Chain1> transform(
        convertJwt: (JWT) -> JWT1,
        convertJwk: (JWK1) -> JWK,
        convertX509Chain: (X509Chain) -> X509Chain1,
    ): SdJwtVcSignatureVerifierFactory<JWT1, JWK1, X509Chain1> =
        object : SdJwtVcSignatureVerifierFactory<JWT1, JWK1, X509Chain1> {
            override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcJwtSignatureVerifier<JWT1> =
                this@SdJwtVcSignatureVerifierFactory.usingIssuerMetadata(httpClientFactory).map(convertJwt)

            override fun usingX5c(x509CertificateTrust: X509CertificateTrust<X509Chain1>): SdJwtVcJwtSignatureVerifier<JWT1> =
                this@SdJwtVcSignatureVerifierFactory.usingX5c(x509CertificateTrust.contraMap(convertX509Chain)).map(convertJwt)

            override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK1>): SdJwtVcJwtSignatureVerifier<JWT1> =
                this@SdJwtVcSignatureVerifierFactory.usingDID(didLookup.map(convertJwk)).map(convertJwt)

            override fun usingX5cOrIssuerMetadata(
                x509CertificateTrust: X509CertificateTrust<X509Chain1>,
                httpClientFactory: KtorHttpClientFactory,
            ): SdJwtVcJwtSignatureVerifier<JWT1> =
                this@SdJwtVcSignatureVerifierFactory.usingX5cOrIssuerMetadata(
                    httpClientFactory = httpClientFactory,
                    x509CertificateTrust = x509CertificateTrust.contraMap(convertX509Chain),
                ).map(convertJwt)
        }
}
