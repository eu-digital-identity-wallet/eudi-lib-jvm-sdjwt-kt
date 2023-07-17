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
 * Serializes a [SdJwt.Issuance] to Combined Issuance Format
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @receiver the SD-JWT to serialize
 * @return the Combined Issuance format of the SD-JWT
 */
fun <JWT> SdJwt.Issuance<JWT>.toCombinedIssuanceFormat(
    serializeJwt: (JWT) -> String,
): String {
    val serializedJwt = serializeJwt(jwt)
    val serializedDisclosures = disclosures.concat()
    return "$serializedJwt$serializedDisclosures"
}

/**
 * Serialized a [SdJwt.Presentation] to Combined Presentation Format
 *
 * @param serializeJwt a function to serialize the [JWT]
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param serializeKeyBindingJwt a function to serialize the [KB_JWT]
 * @param KB_JWT the type representing the Key Binding part of the SD
 * @receiver the SD-JWT to serialize
 * @return the Combined Presentation format of the SD-JWT
 */
fun <JWT, KB_JWT> SdJwt.Presentation<JWT, KB_JWT>.toCombinedPresentationFormat(
    serializeJwt: (JWT) -> String,
    serializeKeyBindingJwt: (KB_JWT) -> String,
): String {
    val serializedJwt = serializeJwt(jwt)
    val serializedDisclosures = disclosures.concat()
    val serializedKbJwt = keyBindingJwt?.run(serializeKeyBindingJwt) ?: ""
    return "$serializedJwt$serializedDisclosures~$serializedKbJwt"
}

/**
 * Concatenates the given disclosures into a single string, separated by
 * "~". The string also starts with "~".
 *
 * @receiver the disclosures to concatenate
 * @return the string as described above
 */
private fun Iterable<Disclosure>.concat(): String = fold("") { acc, disclosure -> "$acc~${disclosure.value}" }
