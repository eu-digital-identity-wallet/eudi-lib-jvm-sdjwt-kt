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

fun base64UrlCodec(): Base64UrlCodec = Base64UrlCodecJavaAdapter

object Base64UrlCodecJavaAdapter : Base64UrlCodec {
    private val encoder: Base64.Encoder by lazy { Base64.getUrlEncoder() }
    private val decoder: Base64.Decoder by lazy { Base64.getUrlDecoder() }
    override fun encode(value: ByteArray): ByteArray = encoder.encode(value)
    override fun encodeToString(value: ByteArray): String = encoder.encodeToString(value)
    override fun decode(value: ByteArray): ByteArray = decoder.decode(value)
    override fun decode(value: String): ByteArray = decoder.decode(value)
}
