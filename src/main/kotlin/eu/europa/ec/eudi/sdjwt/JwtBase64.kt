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

object JwtBase64 {

    private val encoder: Base64.Encoder by lazy { Base64.getUrlEncoder() }
    private val decoder: Base64.Decoder by lazy { Base64.getUrlDecoder() }

    fun decode(value: String): ByteArray = decoder.decode(value)

    // Since the complement character "=" is optional,
    // we can remove it to save some bits in the HTTP header
    fun encodeString(value: ByteArray): String =
        encoder.encodeToString(value).replace("=", "")

    fun encodeString(value: String): String = encodeString(value.encodeToByteArray())
    fun decodeString(value: String): String = decoder.decode(value.encodeToByteArray()).decodeToString()
}
