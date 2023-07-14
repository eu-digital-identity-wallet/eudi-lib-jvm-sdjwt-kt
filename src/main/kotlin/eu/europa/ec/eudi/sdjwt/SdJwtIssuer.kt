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
 * Representation of a function capable of producing a [issuance SD-JWT][SdJwt.Issuance]
 *
 *  @param JWT the type representing the JWT part of the SD-JWT
 */
fun interface SdJwtIssuer<JWT> {

    /**
     * Issues an SD-JWT
     *
     * @param disclosuresCreator specifies the details of producing disclosures & hashes, such as [HashAlgorithm],
     * decoys to use etc.
     * @param sdElements the contents of the SD-JWT
     * @return the issuance SD-JWT
     */
    fun issue(
        disclosuresCreator: DisclosuresCreator = DefaultDisclosureCreator,
        sdElements: SdElement.SdObject,
    ): Result<SdJwt.Issuance<JWT>> = runCatching {
        val (disclosures, claimSet) = disclosuresCreator.discloseSdJwt(sdElements).getOrThrow()
        issue(disclosures, claimSet)
    }

    /**
     * Issues an SD-JWT
     * @param disclosures the disclosures to include
     * @param claimSet the claims to include in the JWT payload of the SD-JWT
     */
    fun issue(disclosures: Set<Disclosure>, claimSet: Claims): SdJwt.Issuance<JWT>
}
