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

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object JwtBase64 {

    fun decode(value: String): ByteArray = Base64.UrlSafe.decode(value)

    // Since the complement character "=" is optional,
    // we can remove it to save some bits in the HTTP header
    fun encode(value: ByteArray): String = removePadding(Base64.UrlSafe.encode(value))

    fun removePadding(value: String): String = value.replace("=", "")
}
