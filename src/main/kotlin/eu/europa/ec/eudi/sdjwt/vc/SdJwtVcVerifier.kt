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
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor
import eu.europa.ec.eudi.sdjwt.*
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.security.PublicKey
import java.security.cert.X509Certificate

fun interface X509CertificateTrust {
    fun isTrusted(chain: List<X509Certificate>): Boolean
}

fun interface DIDResolver {
    suspend fun resolve(didUrl: URI): PublicKey?
}

class SdJwtVcVerifier(
    private val trust: X509CertificateTrust?,
    private val didResolver: DIDResolver?,
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

    private suspend fun signatureVerifier() = sdJwtVcSignatureVerifier(trust, didResolver)
}

suspend fun sdJwtVcSignatureVerifier(
    trust: X509CertificateTrust?,
    didResolver: DIDResolver?,
): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = SignedJWT.parse(unverifiedJwt)
        val issuerPublicKey = issuerPublicKey(trust, didResolver, signedJwt)
        checkNotNull(issuerPublicKey) { "Failed to resolve issuer public key" }
        val jwtProcessor = sdJwtVcProcessor(issuerPublicKey)
        jwtProcessor.process(signedJwt, null).asClaims()
    } catch (e: Throwable) {
        null
    }
}

internal suspend fun issuerPublicKey(
    trust: X509CertificateTrust?,
    didResolver: DIDResolver?,
    signedJwt: SignedJWT,
): PublicKey? =
    when (val source = keySource(signedJwt)) {
        null -> null
        is SdJwtVCIssuerPubKeySource.MetaData -> {
            // TODO Fetch meta-data
            //  - match iss
            //  - Resolve JWK or JWK_URI
            TODO()
        }

        is SdJwtVCIssuerPubKeySource.X509CertChain ->
            if (trust?.isTrusted(source.chain) == true) source.chain.first().publicKey
            else null

        is SdJwtVCIssuerPubKeySource.DIDUrl -> didResolver?.resolve(source.url)
    }

/**
 * The source from which to get Issuer's public key
 */
private sealed interface SdJwtVCIssuerPubKeySource {

    @JvmInline
    value class MetaData(val iss: URI) : SdJwtVCIssuerPubKeySource

    interface X509CertChain : SdJwtVCIssuerPubKeySource {
        val chain: List<X509Certificate>
    }

    data class X509SanDns(val iss: URI, override val chain: List<X509Certificate>) : X509CertChain
    data class X509SanURI(val iss: URI, override val chain: List<X509Certificate>) : X509CertChain

    @JvmInline
    value class DIDUrl(val url: URI) : SdJwtVCIssuerPubKeySource
}

private fun keySource(jwt: SignedJWT): SdJwtVCIssuerPubKeySource? {
    val kid = jwt.header.keyID?.let { runCatching { URI.create(it) }.getOrNull() }
    val certChain = jwt.header.x509CertChain.orEmpty().mapNotNull { X509CertUtils.parse(it.decode()) }
    val iss = jwt.jwtClaimsSet.issuer?.let { runCatching { URI.create(it) }.getOrNull() }
    val issScheme = iss?.scheme?.lowercase()

    return when {
        iss == null -> null
        issScheme == "https" && certChain.isEmpty() && kid == null -> SdJwtVCIssuerPubKeySource.MetaData(iss)
        certChain.isNotEmpty() && kid == null ->
            when (issScheme) {
                "dns" -> {
                    val names = certChain[0].sanOfDNSName().getOrDefault(emptyList())
                    // TODO iss should be in leaf certificate san DNS entries
                    if (true) SdJwtVCIssuerPubKeySource.X509SanDns(iss, certChain)
                    else null
                }

                else -> {
                    val names = certChain[0].sanOfUniformResourceIdentifier().getOrDefault(emptyList())
                    if (iss.toString() in names) SdJwtVCIssuerPubKeySource.X509SanURI(iss, certChain)
                    else null
                }
            }

        issScheme == "did" && certChain.isEmpty() && kid != null -> {
            // TODO check if Kid is absolute or relative URL
            //  in case of absolute URL make sure that it is sub-resource of iss => didUrl = kid
            //  in case of relative URL => didURL = iss + did
            val didUrl = iss
            SdJwtVCIssuerPubKeySource.DIDUrl(didUrl)
        }

        else -> null
    }
}

private fun sdJwtVcProcessor(issuerPublicKey: PublicKey): JWTProcessor<SecurityContext> =
    DefaultJWTProcessor<SecurityContext>().apply {
        jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(JOSEObjectType(SD_JWT_VC_TYPE))
        jwsKeySelector = JWSKeySelector { header, context ->
            val alg = checkNotNull(header.algorithm) { "Could not find alg claim in JWT header" }
            val nested = SingleKeyJWSKeySelector<SecurityContext>(alg, issuerPublicKey)
            nested.selectJWSKeys(header, context)
        }
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().build(),
            setOf("iss"),
        )
    }

private const val SD_JWT_VC_TYPE = "vc+sd-jwt"
