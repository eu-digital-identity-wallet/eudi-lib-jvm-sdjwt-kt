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
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.Payload as NimbusPayload
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT

private fun JsonObject.asBytes(): ByteArray = toString().encodeToByteArray()

/**
 *
 */
object SdJwtSigner {

    val Default: DisclosuresCreator = DisclosuresCreator(HashAlgorithm.SHA_256, SaltProvider.Default, 0)

    /**
     * Creates and signs the SD-JWT using the provided parameters
     *
     * @param signer the signer that will sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * It MUST NOT use none or an identifier for a symmetric algorithm (MAC).
     * @param disclosuresCreator specifies the details of producing disclosures & hashes, such as [HashAlgorithm],
     * decoys to use etc.
     * @param holderBindingJwt if provided it will be appended to the end of the SD-JWT.
     * @param sdJwtElements the contents of the SD-JWT
     *
     * @return the SD-JWT
     */
    fun sign(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        disclosuresCreator: DisclosuresCreator = Default,
        holderBindingJwt: NimbusJWT? = null,
        sdJwtElements: List<SdJwtElement>,
    ): Result<SdJwt<NimbusSignedJWT, NimbusJWT>> = runCatching {
        require(signAlgorithm.isAsymmetric()) { "Only asymmetric algorithm can be used" }

        val (disclosures, claims) = disclosuresCreator.discloseSdJwt(sdJwtElements).getOrThrow()
        val header = NimbusJWSHeader(signAlgorithm)
        val payload = NimbusPayload(claims.asBytes())

        val jwt = NimbusSignedJWT(header, NimbusJWTClaimsSet.parse(payload.toJSONObject())).also { it.sign(signer) }

        SdJwt(jwt, disclosures, holderBindingJwt)
    }

    /**
     * Creates and signs the SD-JWT using the provided parameters
     *
     * @param signer the signer that will sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * It MUST NOT use none or an identifier for a symmetric algorithm (MAC).
     * @param disclosuresCreator specifies the details of producing disclosures & hashes, such as [HashAlgorithm],
     * decoys to use etc.
     * @param holderBindingJwt if provided it will be appended to the end of the SD-JWT.
     * @param sdJwtElements the contents of the SD-JWT
     *
     * @return the SD-JWT as string
     */
    fun signAndSerialize(
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        disclosuresCreator: DisclosuresCreator = Default,
        holderBindingJwt: NimbusJWT? = null,
        sdJwtElements: List<SdJwtElement>,
    ): Result<CombinedIssuanceSdJwt> =
        sign(signer, signAlgorithm, disclosuresCreator, holderBindingJwt, sdJwtElements)
            .map { sdjwt -> sdjwt.serialize({ it.serialize() }, { it.serialize() }) }

    /**
     * Indicates whether an [NimbusJWSAlgorithm] is asymmetric
     * @receiver the algorithm to check
     * @return true if algorithm is asymmetric.
     */
    private fun NimbusJWSAlgorithm.isAsymmetric(): Boolean = NimbusJWSAlgorithm.Family.SIGNATURE.contains(this)
}

fun List<SdJwtElement>.sign(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    disclosuresCreator: DisclosuresCreator = SdJwtSigner.Default,
    holderBindingJwt: NimbusJWT? = null,
): Result<SdJwt<NimbusSignedJWT, NimbusJWT>> =
    SdJwtSigner.sign(signer, signAlgorithm, disclosuresCreator, holderBindingJwt, this)

fun List<SdJwtElement>.signAndSerialize(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    disclosuresCreator: DisclosuresCreator = SdJwtSigner.Default,
    holderBindingJwt: NimbusJWT? = null,
): Result<CombinedIssuanceSdJwt> =
    SdJwtSigner.signAndSerialize(signer, signAlgorithm, disclosuresCreator, holderBindingJwt, this)

fun SdJwtSigner.sign(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    disclosuresCreator: DisclosuresCreator = Default,
    holderBindingJwt: NimbusJWT? = null,
    builderAction: SdJwtElementsBuilder.() -> Unit,
): Result<SdJwt<NimbusSignedJWT, NimbusJWT>> =
    sign(signer, signAlgorithm, disclosuresCreator, holderBindingJwt, sdJwt(builderAction))

fun SdJwtSigner.signAndSerialize(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    disclosuresCreator: DisclosuresCreator = Default,
    holderBindingJwt: NimbusJWT? = null,
    builderAction: SdJwtElementsBuilder.() -> Unit,
): Result<CombinedIssuanceSdJwt> =
    signAndSerialize(signer, signAlgorithm, disclosuresCreator, holderBindingJwt, sdJwt(builderAction))
