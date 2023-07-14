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

/**
 * Signs an SD-JWT
 *
 * @param SIGNED_JWT the type representing the JWT part of the SD-JWT, signed
 **/
typealias SignSdJwt<SIGNED_JWT> = (UnsignedSdJwt) -> SdJwt.Issuance<SIGNED_JWT>

/**
 * Representation of a function capable of producing a [issuance SD-JWT][SdJwt.Issuance]
 *
 * @param sdJwtFactory factory for un-signed SD-JWT
 * @param signSdJwt signer
 * @param SIGNED_JWT the type representing the JWT part of the SD-JWT, signed
 */
class SdJwtIssuer<SIGNED_JWT>(
    private val sdJwtFactory: SdJwtFactory,
    private val signSdJwt: SignSdJwt<SIGNED_JWT>,
) {

    /**
     * Issues an SD-JWT
     *
     * @param sdElements the contents of the SD-JWT
     * @return the issuance SD-JWT
     */
    fun issue(sdElements: SdElement.SdObject): Result<SdJwt.Issuance<SIGNED_JWT>> = runCatching {
        val unsignedSdJwt = sdJwtFactory.createSdJwt(sdElements).getOrThrow()
        signSdJwt(unsignedSdJwt)
    }

    companion object
}
