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
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.JwkSourceJWTProcessor
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import com.nimbusds.jose.JOSEObjectType as NimbusJOSEObjectType
import com.nimbusds.jose.jwk.source.JWKSource as NimbusJWKSource
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier as NimbusDefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier as NimbusJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.SecurityContext as NimbusSecurityContext
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier as NimbusDefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier as NimbusJWTClaimsSetVerifier

internal class SdJwtVcJwtProcessor<C : NimbusSecurityContext>(
    jwkSource: NimbusJWKSource<C>,
    useKeyId: Boolean,
) : JwkSourceJWTProcessor<C>(typeVerifier(), claimSetVerifier(), jwkSource, useKeyId) {

    companion object {
        /**
         * Accepts [SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT].
         */
        private fun <C : NimbusSecurityContext> typeVerifier(): NimbusJOSEObjectTypeVerifier<C> =
            NimbusDefaultJOSEObjectTypeVerifier(NimbusJOSEObjectType(SdJwtVcSpec.MEDIA_SUBTYPE_DC_SD_JWT))

        private fun <C : NimbusSecurityContext> claimSetVerifier(): NimbusJWTClaimsSetVerifier<C> =
            NimbusDefaultJWTClaimsVerifier(
                NimbusJWTClaimsSet.Builder().build(),
                setOf(SdJwtVcSpec.VCT),
            )
    }
}
