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
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcIssuerPublicKeySource.*
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerificationError.IssuerKeyVerificationError.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.cert.X509Certificate
import java.text.ParseException
import com.nimbusds.jose.jwk.JWK as NimbusJWK
import com.nimbusds.jose.jwk.JWKSet as NimbusJWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet as NimbusImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource as NimbusJWKSource
import com.nimbusds.jose.proc.BadJOSEException as NimbusBadJOSEException
import com.nimbusds.jose.proc.SecurityContext as NimbusSecurityContext
import com.nimbusds.jose.util.X509CertUtils as NimbusX509CertUtils
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

internal object NimbusSdJwtVcFactory : SdJwtVcVerifierFactory<NimbusSignedJWT, NimbusJWK, List<X509Certificate>> {
    override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(httpClientFactory = httpClientFactory)

    override fun usingX5c(x509CertificateTrust: X509CertificateTrust<List<X509Certificate>>): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(trust = x509CertificateTrust)

    override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<NimbusJWK>): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(lookup = didLookup)

    override fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust<List<X509Certificate>>,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(httpClientFactory = httpClientFactory, trust = x509CertificateTrust)
}

private class NimbusSdJwtVcVerifier(
    httpClientFactory: KtorHttpClientFactory? = null,
    trust: X509CertificateTrust<List<X509Certificate>>? = null,
    lookup: LookupPublicKeysFromDIDDocument<NimbusJWK>? = null,
) : SdJwtVcVerifier<NimbusSignedJWT> {
    init {
        require(httpClientFactory != null || trust != null || lookup != null) {
            "at least one of httpClientFactory, trust, or lookup must be provided"
        }
    }

    private val jwtSignatureVerifier: JwtSignatureVerifier<NimbusSignedJWT> =
        sdJwtVcSignatureVerifier(httpClientFactory, trust, lookup)

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
internal fun sdJwtVcSignatureVerifier(
    httpClientFactory: KtorHttpClientFactory? = null,
    trust: X509CertificateTrust<List<X509Certificate>>? = null,
    lookup: LookupPublicKeysFromDIDDocument<NimbusJWK>? = null,
): JwtSignatureVerifier<NimbusSignedJWT> = JwtSignatureVerifier { unverifiedJwt ->
    withContext(Dispatchers.IO) {
        val signedJwt = try {
            NimbusSignedJWT.parse(unverifiedJwt)
        } catch (_: ParseException) {
            throw VerificationError.ParsingError.asException()
        }

        val jwkSource = issuerJwkSource(httpClientFactory, trust, lookup, signedJwt)
        yield()

        try {
            val jwtProcessor = SdJwtVcJwtProcessor(jwkSource)
            jwtProcessor.process(signedJwt, null)
            yield()
            signedJwt
        } catch (e: NimbusBadJOSEException) {
            throw VerificationError.InvalidJwt(e).asException()
        }
    }
}

private fun raise(error: SdJwtVcVerificationError): Nothing = throw SdJwtVerificationException(VerificationError.SdJwtVcError(error))

private suspend fun issuerJwkSource(
    httpClientFactory: KtorHttpClientFactory?,
    trust: X509CertificateTrust<List<X509Certificate>>?,
    lookup: LookupPublicKeysFromDIDDocument<NimbusJWK>?,
    signedJwt: NimbusSignedJWT,
): NimbusJWKSource<NimbusSecurityContext> {
    suspend fun fromMetadata(source: Metadata): NimbusJWKSource<NimbusSecurityContext> {
        if (httpClientFactory == null) raise(UnsupportedVerificationMethod("issuer-metadata"))
        val jwks = runCatching {
            val json = httpClientFactory().use { httpClient ->
                with(GetSdJwtVcIssuerJwkSetKtorOps) { httpClient.getSdJwtIssuerKeySet(source.iss) }
            }
            NimbusJWKSet.parse(Json.encodeToString(json))
        }.getOrElse { raise(IssuerMetadataResolutionFailure(it)) }
        return NimbusImmutableJWKSet(jwks)
    }

    suspend fun fromX509CertChain(source: X509CertChain): NimbusJWKSource<NimbusSecurityContext> {
        if (null == trust) raise(UnsupportedVerificationMethod("x5c"))

        val claimSet = signedJwt.jwtClaimsSet.jsonObject()
        if (!trust.isTrusted(source.chain, claimSet)) raise(UntrustedIssuerCertificate())

        val jwk = NimbusJWK.parse(source.chain.first())
        return NimbusImmutableJWKSet(NimbusJWKSet(mutableListOf(jwk)))
    }

    suspend fun fromDid(source: DIDUrl): NimbusJWKSource<NimbusSecurityContext> {
        if (null == lookup) raise(UnsupportedVerificationMethod("did"))
        val jwks = runCatching {
            lookup.lookup(source.iss, source.kid)?.let(::NimbusJWKSet)
        }.getOrElse { raise(DIDLookupFailure("Failed to resolve $source", it)) }
        if (null == jwks) raise(DIDLookupFailure("Failed to resolve $source"))

        return SdJwtVcJwtProcessor.didJwkSet(signedJwt.header, jwks)
    }

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

internal fun keySource(jwt: NimbusSignedJWT): SdJwtVcIssuerPublicKeySource {
    val kid = jwt.header?.keyID
    val certChain = jwt.header?.x509CertChain.orEmpty().mapNotNull { NimbusX509CertUtils.parse(it.decode()) }
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
            if (leaf.containsIssuerUri(issUrl) || leaf.containsIssuerDnsName(issUrl)) X509CertChain(
                issUrl,
                certChain,
            )
            else raise(UntrustedIssuerCertificate("Failed to find $issUrl in SAN URI or SAN DNS entries of provided leaf certificate"))
        }

        issScheme == HTTPS_URI_SCHEME -> Metadata(issUrl, kid)
        issScheme == DID_URI_SCHEME && certChain.isEmpty() -> DIDUrl(iss, kid)
        else -> raise(CannotDetermineIssuerVerificationMethod)
    }
}
