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

import eu.europa.ec.eudi.sdjwt.*

val sdJwtWithMinimumDigests = sdJwt(minimumDigests = 5) {
    // This 5 guarantees that at least 5 digests will be found
    // to the digest array, regardless of the content of the SD-JWT
    objClaim("address", minimumDigests = 10) {
        // This affects the nested array of the digests that will
        // have at list 10 digests.
    }

    sdObjClaim("address1", minimumDigests = 8) {
        // This will affect the digests array that will be found
        // in the disclosure of this recursively disclosable item
        // the whole object will be embedded in its parent
        // as a single digest
    }

    arrClaim("evidence", minimumDigests = 2) {
        // Array will have at least 2 digests
        // regardless of its elements
    }

    sdArrClaim("evidence1", minimumDigests = 2) {
        // Array will have at least 2 digests
        // regardless of its elements
        // the whole array will be embedded in its parent
        // as a single digest
    }
}
