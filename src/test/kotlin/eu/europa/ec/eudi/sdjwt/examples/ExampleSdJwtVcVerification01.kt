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
package eu.europa.ec.eudi.sdjwt.examples

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.X509CertUtils
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.RFC7519
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.sdJwt
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.URL
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.time.Duration.Companion.days

val sdJwtVcVerification = runBlocking {
    val issuer = URL("https://issuer.example.com")
    val key = ECKeyGenerator(Curve.P_521).generate()
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

    with(NimbusSdJwtOps) {
        val sdJwt = run {
            val spec = sdJwt {
                claim(RFC7519.ISSUER, issuer.toExternalForm())
                claim(SdJwtVcSpec.VCT, "urn:credential:sample")
            }

            val signer = issuer(signer = ECDSASigner(key), signAlgorithm = JWSAlgorithm.ES512) {
                type(JOSEObjectType("vc+sd-jwt"))
                x509CertChain(listOf(Base64.encode(certificate.encoded)))
            }
            signer.issue(spec).getOrThrow().serialize()
        }

        val verifier = SdJwtVcVerifier.usingX5c { chain -> chain.firstOrNull() == certificate }
        verifier.verify(sdJwt)
    }
}
