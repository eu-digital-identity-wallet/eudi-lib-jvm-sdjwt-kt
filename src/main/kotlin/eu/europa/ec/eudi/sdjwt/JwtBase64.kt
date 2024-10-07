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

    /**
     * Base64 encoder that doesn't add padding.
     */
    private val encoder: Encoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * Base64 decoder that doesn't require padding.
     */
    private val decoder: Decoder = Base64.getUrlDecoder()

    fun decode(value: String): ByteArray = decoder.decode(value)
    fun encode(value: ByteArray): String = encoder.encodeToString(value)
    fun removePadding(value: String): String = value.replace("=", "")
}
