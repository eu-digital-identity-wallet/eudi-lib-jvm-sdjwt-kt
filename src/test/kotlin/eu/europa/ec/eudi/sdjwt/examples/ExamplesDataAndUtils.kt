/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.sdjwt.examples

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.X509CertUtils
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import eu.europa.ec.eudi.sdjwt.loadRsaKey
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant

internal val issuerEcKeyPair: ECKey by lazy {
    ECKeyGenerator(Curve.P_256)
        .algorithm(JWSAlgorithm.ES256)
        .generate()
}

internal val issuerRsaKeyPair: RSAKey by lazy { loadRsaKey("/examplesIssuerKey.json") }

internal val serializedUnverifiedPresentationSdJwt: String = runBlocking {
    val issuer = with(NimbusSdJwtOps) {
        issuer(signer = RSASSASigner(issuerRsaKeyPair), signAlgorithm = JWSAlgorithm.RS256)
    }

    val spec = sdJwt {
        claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        objClaim("address") {
            sdClaim("locality", "Schulpforta")
            sdClaim("region", "Sachsen-Anhalt")
        }
        claim("iss", "https://example.com/issuer")
        val now = Clock.System.now()
        claim("iat", now.epochSeconds)
        claim("exp", (now + 31.days).epochSeconds)
    }

    val sdJwt = issuer.issue(spec).getOrThrow()
    with(NimbusSdJwtOps) {
        sdJwt.serialize()
    }
}

internal val serializedUnverifiedIssuanceSdJwt: String = runBlocking {
    val issuer = with(NimbusSdJwtOps) {
        issuer(signer = RSASSASigner(issuerRsaKeyPair), signAlgorithm = JWSAlgorithm.RS256)
    }

    val spec = sdJwt {
        claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        objClaim("address") {
            sdClaim("locality", "Schulpforta")
            sdClaim("region", "Sachsen-Anhalt")
            sdClaim("region", "Sachsen-Anhalt")
            sdClaim("country", "DE")
        }
        claim("iss", "https://example.com/issuer")
        val now = Clock.System.now()
        claim("iat", now.epochSeconds)
        claim("exp", (now + 31.days).epochSeconds)
    }

    val sdJwt = issuer.issue(spec).getOrThrow()
    with(NimbusSdJwtOps) {
        sdJwt.serialize()
    }
}

internal val issuerEcKeyPairWithCertificate: ECKey by lazy {
    val issuer = Url("https://issuer.example.com")

    val key = ECKeyGenerator(Curve.P_521)
        .algorithm(JWSAlgorithm.ES512)
        .generate()

    val certificate = run {
        val issuedAt = Clock.System.now()
        val expiresAt = issuedAt.plus(365.days)
        val subject = X500Principal("CN=${issuer.host}")
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(key.toECPrivateKey())
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date.from(issuedAt.toJavaInstant()),
            Date.from(expiresAt.toJavaInstant()),
            subject,
            key.toECPublicKey(),
        ).addExtension(
            Extension.subjectAlternativeName,
            true,
            GeneralNames.getInstance(DERSequence(GeneralName(GeneralName.dNSName, issuer.host))),
        ).build(signer)
        X509CertUtils.parse(holder.encoded)
    }

    ECKey.Builder(key)
        .x509CertChain(listOf(Base64.encode(certificate.encoded)))
        .build()
}

internal val X509Certificate.base64: Base64
    get() = Base64.encode(encoded)
