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
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
    suspend fun lookup(did: String, didUrl: String?): List<NimbusJWK>?
}

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

interface SdJwtVcVerifierFactory<out JWT> {

    /**
     * Creates a new [SdJwtVcVerifier] with SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust enabled.
     */
    fun usingX5c(x509CertificateTrust: X509CertificateTrust): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with DID resolution enabled.
     */
    fun usingDID(didLookup: LookupPublicKeysFromDIDDocument): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust, and SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<JWT>
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

internal object DefaultSdJwtVcFactory : SdJwtVcVerifierFactory<JwtAndClaims> {
    override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingIssuerMetadata(httpClientFactory).map(::nimbusToJwtAndClaims)

    override fun usingX5c(x509CertificateTrust: X509CertificateTrust): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingX5c(x509CertificateTrust).map(::nimbusToJwtAndClaims)

    override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingDID(didLookup).map(::nimbusToJwtAndClaims)

    override fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<JwtAndClaims> =
        NimbusSdJwtVcFactory.usingX5cOrIssuerMetadata(x509CertificateTrust, httpClientFactory).map(::nimbusToJwtAndClaims)
}

internal object NimbusSdJwtVcFactory : SdJwtVcVerifierFactory<NimbusSignedJWT> {
    override fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(httpClientFactory = httpClientFactory)

    override fun usingX5c(x509CertificateTrust: X509CertificateTrust): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(trust = x509CertificateTrust)

    override fun usingDID(didLookup: LookupPublicKeysFromDIDDocument): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(lookup = didLookup)

    override fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<NimbusSignedJWT> =
        NimbusSdJwtVcVerifier(httpClientFactory = httpClientFactory, trust = x509CertificateTrust)
}

private class NimbusSdJwtVcVerifier(
    httpClientFactory: KtorHttpClientFactory? = null,
    trust: X509CertificateTrust? = null,
    lookup: LookupPublicKeysFromDIDDocument? = null,
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
fun sdJwtVcSignatureVerifier(
    httpClientFactory: KtorHttpClientFactory? = null,
    trust: X509CertificateTrust? = null,
    lookup: LookupPublicKeysFromDIDDocument? = null,
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
    trust: X509CertificateTrust?,
    lookup: LookupPublicKeysFromDIDDocument?,
    signedJwt: NimbusSignedJWT,
): NimbusJWKSource<NimbusSecurityContext> {
    suspend fun fromMetadata(source: Metadata): NimbusJWKSource<NimbusSecurityContext> {
        if (httpClientFactory == null) raise(UnsupportedVerificationMethod("issuer-metadata"))
        val jwks = runCatching {
            httpClientFactory().use { httpClient ->
                with(MetadataOps) { httpClient.getJWKSetFromSdJwtVcIssuerMetadata(source.iss) }
            }
        }.getOrElse { raise(IssuerMetadataResolutionFailure(it)) }
        return NimbusImmutableJWKSet(jwks)
    }

    suspend fun fromX509CertChain(source: X509CertChain): NimbusJWKSource<NimbusSecurityContext> {
        if (null == trust) raise(UnsupportedVerificationMethod("x5c"))
        if (!trust.isTrusted(source.chain)) raise(UntrustedIssuerCertificate())

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
            if (leaf.containsIssuerUri(issUrl) || leaf.containsIssuerDnsName(issUrl)) X509CertChain(issUrl, certChain)
            else raise(UntrustedIssuerCertificate("Failed to find $issUrl in SAN URI or SAN DNS entries of provided leaf certificate"))
        }

        issScheme == HTTPS_URI_SCHEME -> Metadata(issUrl, kid)
        issScheme == DID_URI_SCHEME && certChain.isEmpty() -> DIDUrl(iss, kid)
        else -> raise(CannotDetermineIssuerVerificationMethod)
    }
}

internal interface MetadataOps : GetSdJwtVcIssuerMetadataOps, GetJwkSetKtorOps {

    suspend fun HttpClient.getJWKSetFromSdJwtVcIssuerMetadata(issuer: Url): NimbusJWKSet = coroutineScope {
        val metadata = getSdJwtVcIssuerMetadata(issuer)
        checkNotNull(metadata) { "Failed to obtain issuer metadata for $issuer" }
        val jwkSet = jwkSetOf(metadata)
        checkNotNull(jwkSet) { "Failed to obtain JWKSet from metadata" }
        val nJwkSet = jwkSet.asNimbusJWKSet().getOrNull()
        checkNotNull(nJwkSet) { "Failed to parse JWKSet" }
    }

    private suspend fun HttpClient.jwkSetOf(metadata: SdJwtVcIssuerMetadata): JsonObject? = coroutineScope {
        when {
            metadata.jwksUri != null -> getJWKSet(Url(metadata.jwksUri))
            else -> metadata.jwks
        }
    }

    private fun JsonObject.asNimbusJWKSet(): Result<NimbusJWKSet> =
        runCatching { NimbusJWKSet.parse(toString()) }

    companion object : MetadataOps
}
