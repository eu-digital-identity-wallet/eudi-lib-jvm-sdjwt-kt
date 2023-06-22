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


import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSObject as NimbusJWSObject
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.Payload as NimbusPayload

private fun JsonObject.asBytes(): ByteArray = toString().encodeToByteArray()

/**
 *
 */
object SdJwt {

    /**
     * @param signer the signer that will be used to sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * It MUST NOT use none or an identifier for a symmetric algorithm (MAC).
     * @param hashAlgorithm the algorithm to be used for hashing disclosures
     * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
     * @param plainClaims Claims that are not selectively disclosable are included in the SD-JWT in plaintext
     * just as they would be in any other JWT
     * @param claimsToBeDisclosed
     */
    fun flatDiscloseAndEncode(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        plainClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject,
        numOfDecoys: Int,
    ): Result<CombinedIssuanceSdJwt> =
        encode(signer, signAlgorithm) {
            DisclosedClaimSet.flat(hashAlgorithm, saltProvider, plainClaims, claimsToBeDisclosed, numOfDecoys)
        }

    /**
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * It MUST NOT use none or an identifier for a symmetric algorithm (MAC).
     * @param plainClaims Claims that are not selectively disclosable are included in the SD-JWT in plaintext
     * just as they would be in any other JWT
     */
    fun structureDiscloseAndEncode(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        plainClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject,
        numOfDecoys: Int,
    ): Result<CombinedIssuanceSdJwt> =
        encode(signer, signAlgorithm) {
            DisclosedClaimSet.structured(
                hashAlgorithm,
                saltProvider,
                plainClaims,
                claimsToBeDisclosed,
                numOfDecoys,
                true
            )
        }

    /**
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * It MUST NOT use none or an identifier for a symmetric algorithm (MAC).
     */
    private fun encode(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        disclosedClaimSet: () -> Result<DisclosedClaimSet>,
    ): Result<CombinedIssuanceSdJwt> = runCatching {

        require(signAlgorithm.isAsymmetric()) { "Only asymmetric algorithm can be used" }

        val (disclosures, jwtClaimSet) = disclosedClaimSet().getOrThrow()
        val header = NimbusJWSHeader(signAlgorithm)
        val payload = NimbusPayload(jwtClaimSet.asBytes())

        val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
            sign(signer)
            serialize()
        }

        jwt + disclosures.concat()
    }

    /**
     * Indicates whether an [NimbusJWSAlgorithm] is asymmetric
     */
    private fun NimbusJWSAlgorithm.isAsymmetric(): Boolean = NimbusJWSAlgorithm.Family.SIGNATURE.contains(this)


}
