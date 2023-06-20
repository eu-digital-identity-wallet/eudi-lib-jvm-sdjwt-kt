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
 * @param signer the signer that will be used to sign the SD-JWT
 * @param algorithm
*  @param hashAlgorithm the algorithm to be used for hashing disclosures
 * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
 * @param jwtClaims
 * @param claimToBeDisclosed
 */
public fun flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: JsonObject = JsonObject(emptyMap()),
    claimsToBeDisclosed: JsonObject,
    numOfDecoys: Int,
): Result<CombinedIssuanceSdJwt> = runCatching {
    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
        hashAlgorithm,
        saltProvider,
        jwtClaims,
        claimsToBeDisclosed,
        numOfDecoys,
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }

    jwt + disclosures.concat()
}
//
// /**
// * @param hashAlgorithm the algorithm to be used for hashing disclosures
// * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
// */
// public fun flatDiscloseAndEncode(
//    signer: NimbusJWSSigner,
//    algorithm: NimbusJWSAlgorithm,
//    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
//    saltProvider: SaltProvider = SaltProvider.Default,
//    jwtClaims: NimbusJWTClaimSet?,
//    claimsToBeDisclosed: Map<String, Any>,
//    numOfDecoys: Int
// ): Result<CombinedIssuanceSdJwt> = runCatching {
//
//    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
//        hashAlgorithm = hashAlgorithm,
//        saltProvider = saltProvider,
//        otherJwtClaims = jwtClaims?.toString(),
//        claimToBeDisclosed = claimToBeDisclosed.first to claimToBeDisclosed.second.let {
//            MimbusJSONObjectUtils.toJSONString(it)
//        },
//        numOfDecoys = numOfDecoys
//    ).getOrThrow()
//
//    val header = NimbusJWSHeader(algorithm)
//    val payload = NimbusPayload(jwtClaimSet.asBytes())
//
//    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
//        sign(signer)
//        serialize()
//    }
//
//    jwt + disclosures.concat()
// }
