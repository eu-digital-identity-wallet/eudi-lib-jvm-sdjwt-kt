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

/**
 * Signs an SD-JWT
 *
 * @param SIGNED_JWT the type representing the JWT part of the SD-JWT, signed
 **/
typealias SignSdJwt<SIGNED_JWT> = suspend (SdJwt<JsonObject>) -> SdJwt<SIGNED_JWT>

/**
 * Representation of a function capable of producing an [issuance SD-JWT][SdJwt]
 *
 * @param SIGNED_JWT the type representing the JWT part of the SD-JWT, signed
 */
fun interface SdJwtIssuer<out SIGNED_JWT> {

    /**
     * Issues an SD-JWT
     *
     * @param sdJwtSpec the contents of the SD-JWT
     * @return the issuance SD-JWT
     */
    suspend fun issue(sdJwtSpec: DisclosableObject): Result<SdJwt<SIGNED_JWT>>

    companion object {
        /**
         * Factory method
         * @param sdJwtFactory factory for unsigned SD-JWT
         * @param signSdJwt signer
         */
        operator fun <SIGNED_JWT> invoke(
            sdJwtFactory: SdJwtFactory,
            signSdJwt: SignSdJwt<SIGNED_JWT>,
        ): SdJwtIssuer<SIGNED_JWT> = SdJwtIssuer { sdElements ->
            runCatching {
                val unsignedSdJwt = sdJwtFactory.createSdJwt(sdElements).getOrThrow()
                signSdJwt(unsignedSdJwt)
            }
        }
    }
}
