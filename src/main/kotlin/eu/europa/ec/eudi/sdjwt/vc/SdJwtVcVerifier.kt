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
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.security.PublicKey
import java.security.cert.X509Certificate

fun interface X509CertificateTrust {
    suspend fun isTrusted(chain: List<X509Certificate>): Boolean
    companion object {
        val None: X509CertificateTrust = X509CertificateTrust { false }
    }
}

fun interface DIDResolver {
    suspend fun resolve(didUrl: URI): PublicKey?
}

class SdJwtVcVerifier(
    private val httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
    private val trust: X509CertificateTrust = X509CertificateTrust.None,
    private val didResolver: DIDResolver? = null,
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
    ): Result<SdJwt.Issuance<JwtAndClaims>> =
        SdJwtVerifier.verifyIssuance(signatureVerifier(), unverifiedSdJwt)

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
    ): Result<SdJwt.Issuance<JwtAndClaims>> = SdJwtVerifier.verifyIssuance(signatureVerifier(), unverifiedSdJwt)

    private suspend fun signatureVerifier() = sdJwtVcSignatureVerifier(httpClientFactory, trust, didResolver)
}

suspend fun sdJwtVcSignatureVerifier(
    httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
    trust: X509CertificateTrust = X509CertificateTrust.None,
    didResolver: DIDResolver? = null,
): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = SignedJWT.parse(unverifiedJwt)
        val keySelector = issuerJwsKeySelector(httpClientFactory, trust, didResolver, signedJwt)
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
    didResolver: DIDResolver?,
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
        didResolver
            ?.resolve(source.url.toURI())
            ?.let { publicKey -> SingleKeyJWSKeySelector(algorithm, publicKey) }

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

    @JvmInline
    value class DIDUrl(val url: Url) : SdJwtVcIssuerPublicKeySource
}

private const val HTTPS_URI_SCHEME = "https"
private const val DID_URI_SCHEME = "did"

private fun keySource(jwt: SignedJWT): SdJwtVcIssuerPublicKeySource? {
    val kid = jwt.header.keyID?.let { runCatching { Url(it) }.getOrNull() }
    val certChain = jwt.header.x509CertChain.orEmpty().mapNotNull { X509CertUtils.parse(it.decode()) }
    val iss = jwt.jwtClaimsSet.issuer?.let { runCatching { Url(it) }.getOrNull() }
    val issScheme = iss?.protocol?.name
    fun X509Certificate.containsIssuerDnsName(iss: Url): Boolean =
        dnsName(iss)?.let { issuerDnsName ->
            val dnsNames = sanOfDNSName().getOrDefault(emptyList())
            issuerDnsName in dnsNames
        } ?: false

    fun X509Certificate.containsIssuerUri(iss: Url): Boolean {
        val names = sanOfUniformResourceIdentifier().getOrDefault(emptyList())
        return iss.toString() in names
    }

    return when {
        iss == null -> null
        issScheme == HTTPS_URI_SCHEME && certChain.isEmpty() && kid == null -> Metadata(iss)

        certChain.isNotEmpty() && kid == null ->
            when (issScheme) {
                DNS_URI_SCHEME ->
                    certChain
                        .takeIf { (leaf, _) -> leaf.containsIssuerDnsName(iss) }
                        ?.let { X509SanDns(iss, it) }

                else ->
                    certChain
                        .takeIf { (leaf, _) -> leaf.containsIssuerUri(iss) }
                        ?.let { X509SanURI(iss, it) }
            }

        issScheme == DID_URI_SCHEME && certChain.isEmpty() -> {
            // TODO check if Kid is absolute or relative URL
            //  in case of absolute URL make sure that it is sub-resource of iss => didUrl = kid
            //  in case of relative URL => didURL = iss + did
            val didUrl = iss
            DIDUrl(didUrl)
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

private const val SD_JWT_VC_TYPE = "vc+sd-jwt"
