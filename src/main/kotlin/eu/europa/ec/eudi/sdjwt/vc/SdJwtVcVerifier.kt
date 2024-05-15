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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.*
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor
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
 * @param httpClientFactory a factory for getting http clients, used while interacting with issuer
 * @param trust a function that accepts a chain of certificates (contents of `x5c` claim) and
 * indicates whether is trusted or not. If it is not provided, defaults to [X509CertificateTrust.None]
 * @param lookup an optional way of looking up keys from DID Documents. A `null` value indicates that holder doesn't
 * support DIDs
 */
class SdJwtVcVerifier(
    private val httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
    private val trust: X509CertificateTrust = X509CertificateTrust.None,
    private val lookup: LookupPublicKeysFromDIDDocument? = null,
) {
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
        val jwtSignatureVerifier = async { jwtSignatureVerifier() }
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
        val jwtSignatureVerifier = async { jwtSignatureVerifier() }
        SdJwtVerifier.verifyIssuance(jwtSignatureVerifier.await(), unverifiedSdJwt)
    }

    /**
     * Verifies a SD-JWT in Combined Presentation Format
     * Typically, this is useful to Verifier that wants to verify presentation SD-JWT communicated by Holder
     *
     * @param unverifiedSdJwt the SD-JWT to be verified
     * @param challenge verifier's challenge, expected to be found in KB-JWT (signed by wallet)
     * @return the verified SD-JWT, if valid. Otherwise, method could raise a [SdJwtVerificationException]
     * The verified SD-JWT will the [JWT][SdJwt.Presentation.jwt] and key binding JWT
     * are representing in both string and decoded payload.
     * Expected errors are reported via a [SdJwtVerificationException]
     */
    suspend fun verifyPresentation(
        unverifiedSdJwt: String,
        challenge: JsonObject? = null,
    ): Result<SdJwt.Presentation<JwtAndClaims>> = coroutineScope {
        val jwtSignatureVerifier = async { jwtSignatureVerifier() }
        val keyBindingVerifier = KeyBindingVerifier.forSdJwtVc(challenge)
        SdJwtVerifier.verifyPresentation(jwtSignatureVerifier.await(), keyBindingVerifier, unverifiedSdJwt)
    }

    private suspend fun jwtSignatureVerifier(): JwtSignatureVerifier =
        sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup)
}

fun KeyBindingVerifier.Companion.forSdJwtVc(challenge: JsonObject?): KeyBindingVerifier.MustBePresentAndValid =
    KeyBindingVerifier.mustBePresentAndValid(HolderPubKeyInConfirmationClaim, challenge)

/**
 * Factory method for producing a SD-JWT-VC specific signature verifier.
 * This verifier will get the Issuer's public key from the JWT part of the SD-JWT.
 * In particular,
 * - If `iss` claim is a URI and there is no `x5c` and no `kid` in the header, SD-JWT-VC metadata will be used
 * - If `iss` claim is a DNS URI and there is a `x5c` claim key will be extracted from the leaf certificate,
 * if it is trusted & it contains a SAN DNS equal to `iss`
 * - If `iss` claim is a URI and there is a `x5c` claim key will be extracted from the leaf certificate,
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
suspend fun sdJwtVcSignatureVerifier(
    httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
    trust: X509CertificateTrust = X509CertificateTrust.None,
    lookup: LookupPublicKeysFromDIDDocument? = null,
): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = SignedJWT.parse(unverifiedJwt)
        val keySelector = issuerJwsKeySelector(httpClientFactory, trust, lookup, signedJwt)
        checkNotNull(keySelector) { "Failed to resolve issuer public key" }
        val jwtProcessor = sdJwtVcProcessor(keySelector)
        jwtProcessor.process(signedJwt, null).asClaims()
    } catch (e: Throwable) {
        null
    }
}

private suspend fun issuerJwsKeySelector(
    httpClientFactory: KtorHttpClientFactory,
    trust: X509CertificateTrust,
    lookup: LookupPublicKeysFromDIDDocument?,
    signedJwt: SignedJWT,
): JWSKeySelector<SecurityContext>? {
    val algorithm = requireNotNull(signedJwt.header.algorithm) { "missing 'alg'" }
    suspend fun fromMetadata(source: Metadata): JWSKeySelector<SecurityContext> =
        httpClientFactory.invoke().use { httpClient ->
            val fetcher = SdJwtVcIssuerMetaDataFetcher(httpClient)
            val (_, jwks) = fetcher.fetchMetaData(source.iss)
            JWSVerificationKeySelector(algorithm, ImmutableJWKSet(jwks))
        }

    suspend fun fromX509CertChain(source: X509CertChain): JWSKeySelector<SecurityContext>? =
        if (trust.isTrusted(source.chain)) {
            val publicKey = source.chain.first().publicKey
            SingleKeyJWSKeySelector(algorithm, publicKey)
        } else null

    suspend fun fromDid(source: DIDUrl): JWSKeySelector<SecurityContext>? =
        lookup
            ?.lookup(source.did, source.didUrl)
            ?.takeIf { it.isNotEmpty() }
            ?.let { publicKeys -> JWSVerificationKeySelector(algorithm, ImmutableJWKSet(JWKSet(publicKeys))) }

    return when (val source = keySource(signedJwt)) {
        null -> null
        is Metadata -> fromMetadata(source)
        is X509CertChain -> fromX509CertChain(source)
        is DIDUrl -> fromDid(source)
    }
}

/**
 * The source from which to get Issuer's public key
 */
private sealed interface SdJwtVcIssuerPublicKeySource {

    @JvmInline
    value class Metadata(val iss: Url) : SdJwtVcIssuerPublicKeySource

    interface X509CertChain : SdJwtVcIssuerPublicKeySource {
        val chain: List<X509Certificate>
    }

    data class X509SanDns(val iss: Url, override val chain: List<X509Certificate>) : X509CertChain
    data class X509SanURI(val iss: Url, override val chain: List<X509Certificate>) : X509CertChain

    data class DIDUrl(val did: String, val didUrl: String?) : SdJwtVcIssuerPublicKeySource
}

private const val HTTPS_URI_SCHEME = "https"
private const val DID_URI_SCHEME = "did"

private fun keySource(jwt: SignedJWT): SdJwtVcIssuerPublicKeySource? {
    val kid = jwt.header.keyID
    val certChain = jwt.header.x509CertChain.orEmpty().mapNotNull { X509CertUtils.parse(it.decode()) }
    val iss = jwt.jwtClaimsSet.issuer
    val issUrl = iss?.let { runCatching { Url(it) }.getOrNull() }
    val issScheme = issUrl?.protocol?.name

    fun X509Certificate.containsIssuerDnsName(iss: DnsUri): Boolean {
        val issuerDnsName = iss.dnsName()
        val dnsNames = sanOfDNSName().getOrDefault(emptyList())
        return issuerDnsName in dnsNames
    }

    fun X509Certificate.containsIssuerUri(iss: Url): Boolean {
        val names = sanOfUniformResourceIdentifier().getOrDefault(emptyList())
        return iss.toString() in names
    }

    return when {
        issUrl == null -> null
        issScheme == HTTPS_URI_SCHEME && certChain.isEmpty() && kid == null -> Metadata(issUrl)

        certChain.isNotEmpty() && kid == null ->
            when (issScheme) {
                DNS_URI_SCHEME ->
                    certChain
                        .takeIf { (leaf, _) -> DnsUri(issUrl)?.let { leaf.containsIssuerDnsName(it) } ?: false }
                        ?.let { X509SanDns(issUrl, it) }

                else ->
                    certChain
                        .takeIf { (leaf, _) -> leaf.containsIssuerUri(issUrl) }
                        ?.let { X509SanURI(issUrl, it) }
            }

        issScheme == DID_URI_SCHEME && certChain.isEmpty() -> {
            // do not use Url for DIDs. Url adds localhost as a host when parsing DIDs. use the original value instead.
            DIDUrl(iss, kid)
        }

        else -> null
    }
}

private fun sdJwtVcProcessor(keySelector: JWSKeySelector<SecurityContext>): JWTProcessor<SecurityContext> =
    DefaultJWTProcessor<SecurityContext>().apply {
        jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(JOSEObjectType(SD_JWT_VC_TYPE))
        jwsKeySelector = keySelector
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().build(),
            setOf("iss"),
        )
    }

const val SD_JWT_VC_TYPE = "vc+sd-jwt"
