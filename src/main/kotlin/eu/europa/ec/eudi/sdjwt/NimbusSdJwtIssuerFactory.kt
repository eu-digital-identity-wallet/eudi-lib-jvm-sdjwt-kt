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
package eu.europa.ec.eudi.sdjwt

import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

/**
 *
 */
object NimbusSdJwtIssuerFactory {

    private const val allowSymmetric = true

    /**
     * Factory method for creating a [NimbusIssuer]
     *
     * @param signer the signer that will sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * @return a [NimbusIssuer]
     */
    fun createIssuer(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
    ): SdJwtIssuer<NimbusSignedJWT> = SdJwtIssuer { disclosures, claims ->

        require(allowSymmetric || signAlgorithm.isAsymmetric()) { "Only asymmetric algorithm can be used" }
        val header = with(NimbusJWSHeader.Builder(signAlgorithm)) {
            jwsHeaderCustomization()
            build()
        }
        val jwt = NimbusSignedJWT(header, NimbusJWTClaimsSet.parse(claims.toString())).apply { sign(signer) }
        SdJwt.Issuance(jwt, disclosures)
    }

    /**
     * Indicates whether an [NimbusJWSAlgorithm] is asymmetric
     * @receiver the algorithm to check
     * @return true if algorithm is asymmetric.
     */
    private fun NimbusJWSAlgorithm.isAsymmetric(): Boolean = NimbusJWSAlgorithm.Family.SIGNATURE.contains(this)
}

/**
 * Serializes a [SdJwt] into either Combined Issuance or Combined Presentation format
 * depending on the case
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param HB_JWT the type representing the Holder Binding part of the SD
 * @receiver the SD-JWT to be serialized
 * @return the SD-JWT in either  Combined Issuance or Combined Presentation format depending on the case
 */
fun <JWT : NimbusJWT, HB_JWT : NimbusJWT> SdJwt<JWT, HB_JWT>.serialize(): String = when (this) {
    is SdJwt.Issuance<JWT> -> toCombinedIssuanceFormat(NimbusJWT::serialize)
    is SdJwt.Presentation<JWT, HB_JWT> -> toCombinedPresentationFormat(NimbusJWT::serialize, NimbusJWT::serialize)
}

/**
 *
 */
inline fun sdJwt(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    disclosuresCreator: DisclosuresCreator = DefaultDisclosureCreator,
    builderAction: SdJwtElementsBuilder.() -> Unit,
): SdJwt.Issuance<NimbusSignedJWT> =
    with(NimbusSdJwtIssuerFactory.createIssuer(signer, signAlgorithm)) {
        issue(disclosuresCreator, sdJwt(builderAction))
    }.getOrThrow()
