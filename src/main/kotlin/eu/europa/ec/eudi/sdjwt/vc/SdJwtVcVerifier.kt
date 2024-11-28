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

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcIssuerPublicKeySource.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import java.security.cert.X509Certificate

fun interface X509CertificateTrust {
    suspend fun isTrusted(chain: List<X509Certificate>): Boolean

    companion object {
        val None: X509CertificateTrust = X509CertificateTrust { false }
    }
}

/**
 * A function to look up public keys from DIDs/DID URLs.
 */
fun interface LookupPublicKeysFromDIDDocument {

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
}

/**
 * An SD-JWT-VC specific verifier
 *
 * @param httpClientFactory a factory for getting http clients, used to fetch SD-JWT-VC Issuer metadata. A `null`
 * value indicates that holder doesn't support fetching SD-JWT-VC Issuer metadata.
 * @param trust a function that accepts a chain of certificates (contents of `x5c` claim) and
 * indicates whether is trusted or not. A `null` value indicates that holder doesn't support X509 Certificate trust.
 * @param lookup an optional way of looking up keys from DID Documents. A `null` value indicates that holder doesn't
 * support DIDs.
 */
class SdJwtVcVerifier(
    private val httpClientFactory: KtorHttpClientFactory? = null,
    private val trust: X509CertificateTrust? = null,
    private val lookup: LookupPublicKeysFromDIDDocument? = null,
) {
    init {
        require(httpClientFactory != null || trust != null || lookup != null) {
            "at least one of httpClientFactory, trust, or lookup must be provided"
        }
    }

    /**
     * Verifies an SD-JWT (in non enveloped, simple format)
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT
     *
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    suspend fun verifyIssuance(
        unverifiedSdJwt: String,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = coroutineScope {
        val jwtSignatureVerifier = async { sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup) }
        SdJwtVerifier.verifyIssuance(jwtSignatureVerifier.await(), unverifiedSdJwt)
    }

    /**
     * Verifies an SD-JWT in JWS JSON general of flattened format as defined by RFC7515 and extended by SD-JWT
     * specification
     *
     * Typically, this is useful to Holder that wants to verify an issued SD-JWT
     *
     * @param unverifiedSdJwt the SD-JWT to be verified.
     * A JSON Object that is expected to be in general
     * or flatten form as defined in RFC7515 and extended by SD-JWT specification.
     * @return the verified SD-JWT, if valid.
     * Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will contain a [JWT][SdJwt.Issuance.jwt] as both string and decoded payload
     */
    suspend fun verifyIssuance(
        unverifiedSdJwt: JsonObject,
    ): Result<SdJwt.Issuance<JwtAndClaims>> = coroutineScope {
        val jwtSignatureVerifier = async { sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup) }
        SdJwtVerifier.verifyIssuance(jwtSignatureVerifier.await(), unverifiedSdJwt)
    }

    /**
     * Verifies a SD-JWT in Combined Presentation Format
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @param challenge verifier's challenge, expected to be found in KB-JWT (signed by wallet)
     * @return the verified SD-JWT and KB-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and key binding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verifyPresentation(
        unverifiedSdJwt: String,
        challenge: JsonObject? = null,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> = coroutineScope {
        val jwtSignatureVerifier = async { sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup) }
        val keyBindingVerifier = KeyBindingVerifier.forSdJwtVc(challenge)
        SdJwtVerifier.verifyPresentation(jwtSignatureVerifier.await(), keyBindingVerifier, unverifiedSdJwt)
    }

    /**
     * Verifies a SD-JWT in Combined Presentation Format
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param unverifiedSdJwt the SD-JWT to be verified in JWS JSON
     * @param challenge verifier's challenge, expected to be found in KB-JWT (signed by wallet)
     * @return the verified SD-JWT and KB-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and key binding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verifyPresentation(
        unverifiedSdJwt: JsonObject,
        challenge: JsonObject? = null,
    ): Result<Pair<SdJwt.Presentation<JwtAndClaims>, JwtAndClaims?>> = coroutineScope {
        val jwtSignatureVerifier = async { sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup) }
        val keyBindingVerifier = KeyBindingVerifier.forSdJwtVc(challenge)
        SdJwtVerifier.verifyPresentation(jwtSignatureVerifier.await(), keyBindingVerifier, unverifiedSdJwt)
    }

    companion object {

        /**
         * Creates a new [SdJwtVcVerifier] with SD-JWT-VC Issuer Metadata resolution enabled.
         */
        fun usingIssuerMetadata(
            httpClientFactory: KtorHttpClientFactory,
        ): SdJwtVcVerifier = SdJwtVcVerifier(httpClientFactory = httpClientFactory)

        /**
         * Creates a new [SdJwtVcVerifier] with X509 Certificate trust enabled.
         */
        fun usingX5c(x509CertificateTrust: X509CertificateTrust): SdJwtVcVerifier =
            SdJwtVcVerifier(trust = x509CertificateTrust)

        /**
         * Creates a new [SdJwtVcVerifier] with DID resolution enabled.
         */
        fun usingDID(didLookup: LookupPublicKeysFromDIDDocument): SdJwtVcVerifier = SdJwtVcVerifier(lookup = didLookup)

        /**
         * Creates a new [SdJwtVcVerifier] with X509 Certificate trust, and SD-JWT-VC Issuer Metadata resolution enabled.
         */
        fun usingX5cOrIssuerMetadata(
            x509CertificateTrust: X509CertificateTrust,
            httpClientFactory: KtorHttpClientFactory,
        ): SdJwtVcVerifier = SdJwtVcVerifier(httpClientFactory = httpClientFactory, trust = x509CertificateTrust)
    }
}

fun KeyBindingVerifier.Companion.forSdJwtVc(challenge: JsonObject?): KeyBindingVerifier.MustBePresentAndValid =
    KeyBindingVerifier.mustBePresentAndValid(HolderPubKeyInConfirmationClaim, challenge)

/**
 * Factory method for producing a SD-JWT-VC specific signature verifier.
 * This verifier will get the Issuer's public key from the JWT part of the SD-JWT.
 * In particular,
 * - If `iss` claim is an HTTPS URI and there is no `x5c` in the header, SD-JWT-VC metadata will be used
 * - If `iss` claim is an HTTPS URI and there is a `x5c` claim key will be extracted from the leaf certificate,
 * if it is trusted & it contains a SAN DNS equal to `iss` FQDN
 * - If `iss` claim is an HTTPS URI and there is a `x5c` claim key will be extracted from the leaf certificate,
 *  if it is trusted & it contains a SAN URI equal to `iss`
 * - If `iss` claim is a DID the key will be extracted by resolving it.
 *
 *  In addition, the verifier will ensure that `typ` claim is equal to vc+sd-jwt
 *
 * @param httpClientFactory a factory for getting http clients, used while interacting with issuer
 * @param trust a function that accepts a chain of certificates (contents of `x5c` claim) and
 * indicates whether is trusted or not. If it is not provided, defaults to [X509CertificateTrust.None]
 * @param lookup an optional way of looking up public keys from DID Documents. A `null` value indicates
 * that holder doesn't support DIDs
 *
 * @return a SD-JWT-VC specific signature verifier as described above
 */
fun sdJwtVcSignatureVerifier(
    httpClientFactory: KtorHttpClientFactory? = null,
    trust: X509CertificateTrust? = null,
    lookup: LookupPublicKeysFromDIDDocument? = null,
): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = SignedJWT.parse(unverifiedJwt)
        val jwkSource = issuerJwkSource(httpClientFactory, trust, lookup, signedJwt)
        val jwtProcessor = SdJwtVcJwtProcessor(jwkSource)
        jwtProcessor.process(signedJwt, null).asClaims()
    } catch (t: BadJOSEException) {
        null
    }
}

private fun invalidSdJwtVc(msg: String): Nothing = throw VerificationError.Other(msg).asException()

private suspend fun issuerJwkSource(
    httpClientFactory: KtorHttpClientFactory?,
    trust: X509CertificateTrust?,
    lookup: LookupPublicKeysFromDIDDocument?,
    signedJwt: SignedJWT,
): JWKSource<SecurityContext> {
    suspend fun fromMetadata(source: Metadata): JWKSource<SecurityContext> =
        httpClientFactory?.invoke()?.use { httpClient ->
            val fetcher = SdJwtVcIssuerMetaDataFetcher(httpClient)
            fetcher.fetchJWKSetFromMetaData(source.iss)
                ?.let { jwks -> ImmutableJWKSet(jwks) }
                ?: invalidSdJwtVc("Failed to get SD-JWT-VC metadata of ${source.iss}")
        } ?: invalidSdJwtVc("SD-JWT-VC validation requires Issuer's metadata resolution, which is not enabled")

    suspend fun fromX509CertChain(source: X509CertChain): JWKSource<SecurityContext> =
        trust?.let {
            if (it.isTrusted(source.chain)) {
                val jwk = JWK.parse(source.chain.first())
                ImmutableJWKSet(JWKSet(mutableListOf(jwk)))
            } else invalidSdJwtVc("Leaf certificate is not trusted")
        } ?: invalidSdJwtVc("SD-JWT-VC validation requires x5c validation, which is not enabled")

    suspend fun fromDid(source: DIDUrl): JWKSource<SecurityContext> =
        lookup?.let {
            val jwks = it.lookup(source.iss, source.kid) ?: invalidSdJwtVc("Failed to resolve $source")
            SdJwtVcJwtProcessor.didJwkSet(signedJwt.header, JWKSet(jwks))
        } ?: invalidSdJwtVc("SD-JWT-VC validation requires DID resolution, which is not enabled")

    return when (val source = keySource(signedJwt)) {
        is Metadata -> fromMetadata(source)
        is X509CertChain -> fromX509CertChain(source)
        is DIDUrl -> fromDid(source)
    }
}

/**
 * The source from which to get Issuer's public key
 */
internal sealed interface SdJwtVcIssuerPublicKeySource {

    data class Metadata(val iss: Url, val kid: String?) : SdJwtVcIssuerPublicKeySource

    data class X509CertChain(val iss: Url, val chain: List<X509Certificate>) : SdJwtVcIssuerPublicKeySource

    data class DIDUrl(val iss: String, val kid: String?) : SdJwtVcIssuerPublicKeySource
}

private const val HTTPS_URI_SCHEME = "https"
private const val DID_URI_SCHEME = "did"

internal fun keySource(jwt: SignedJWT): SdJwtVcIssuerPublicKeySource {
    val kid = jwt.header?.keyID
    val certChain = jwt.header?.x509CertChain.orEmpty().mapNotNull { X509CertUtils.parse(it.decode()) }
    val iss = jwt.jwtClaimsSet?.issuer
    val issUrl = iss?.let { runCatching { Url(it) }.getOrNull() }
    val issScheme = issUrl?.protocol?.name

    fun X509Certificate.containsIssuerDnsName(iss: Url): Boolean {
        val issuerFQDN = iss.host
        val dnsNames = sanOfDNSName().getOrDefault(emptyList())
        return issuerFQDN in dnsNames
    }

    fun X509Certificate.containsIssuerUri(iss: Url): Boolean {
        val names = sanOfUniformResourceIdentifier().getOrDefault(emptyList())
        return iss.toString() in names
    }

    return when {
        issScheme == HTTPS_URI_SCHEME && certChain.isNotEmpty() -> {
            val leaf = certChain.first()
            if (leaf.containsIssuerUri(issUrl) || leaf.containsIssuerDnsName(issUrl)) X509CertChain(issUrl, certChain)
            else invalidSdJwtVc("Failed to find $issUrl in URI or DNS entries of provided leaf certificate")
        }

        issScheme == HTTPS_URI_SCHEME -> Metadata(issUrl, kid)
        issScheme == DID_URI_SCHEME && certChain.isEmpty() -> DIDUrl(iss, kid)
        else -> invalidSdJwtVc("Failed to identify a source for Issuer's pub key")
    }
}
