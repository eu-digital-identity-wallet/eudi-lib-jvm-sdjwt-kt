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
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.RFC7519
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import eu.europa.ec.eudi.sdjwt.vc.IssuerVerificationMethod
import eu.europa.ec.eudi.sdjwt.vc.TypeMetadataPolicy
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

val sdJwtVcVerification = runBlocking {
    val issuer = Url("https://issuer.example.com")

    with(NimbusSdJwtOps) {
        val sdJwt = run {
            val spec = sdJwt {
                claim(RFC7519.ISSUER, issuer.toString())
                claim(SdJwtVcSpec.VCT, "urn:credential:sample")
            }

            val signer = issuer(signer = ECDSASigner(issuerEcKeyPairWithCertificate), signAlgorithm = JWSAlgorithm.ES512) {
                type(JOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))
                x509CertChain(issuerEcKeyPairWithCertificate.x509CertChain)
            }
            signer.issue(spec).getOrThrow().serialize()
        }

        val verifier = SdJwtVcVerifier(
            issuerVerificationMethod = IssuerVerificationMethod.usingX5c { chain, _ ->
                chain.first().base64 == issuerEcKeyPairWithCertificate.x509CertChain.first()
            },
            typeMetadataPolicy = TypeMetadataPolicy.NotUsed,
        )
        verifier.verify(sdJwt)
    }
}
