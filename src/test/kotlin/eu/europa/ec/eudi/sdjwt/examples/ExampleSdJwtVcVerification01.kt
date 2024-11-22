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
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifier
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals

val sdJwtVcVerification = runBlocking {
    val key = OctetKeyPairGenerator(Curve.Ed25519).generate()
    val didJwk = "did:jwk:${Base64.UrlSafe.encode(key.toPublicJWK().toJSONString().toByteArray())}"

    val sdJwt = run {
        val spec = sdJwt {
            iss(didJwk)
        }
        val signer = SdJwtIssuer.nimbus(signer = Ed25519Signer(key), signAlgorithm = JWSAlgorithm.EdDSA) {
            type(JOSEObjectType("vc+sd-jwt"))
        }
        signer.issue(spec).getOrThrow()
    }

    val verifier = SdJwtVcVerifier.usingDID { did, _ ->
        assertEquals(didJwk, did)
        listOf(key.toPublicJWK())
    }

    verifier.verifyIssuance(sdJwt.serialize())
}
