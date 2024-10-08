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

import java.util.*
import java.util.Base64.Decoder
import java.util.Base64.Encoder

object JwtBase64 {

    private val encoder: Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Decoder = Base64.getUrlDecoder()

    /**
     * Decodes the given [value].
     * @param value Expected to be base64 URL-encoded without padding
     * @return the decoded content
     * @throws IllegalArgumentException in case padding used
     */
    fun decode(value: String): ByteArray {
        require(value.lastOrNull() != '=') { "No padding" }
        return decoder.decode(value)
    }

    /**
     * URL-Encodes the given [value] without padding
     * @param value what to encode
     * @return the base64 URL-encoded without padding
     */
    fun encode(value: ByteArray): String = encoder.encodeToString(value)
}
