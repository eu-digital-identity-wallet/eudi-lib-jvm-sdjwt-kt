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

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

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
fun <JWT> SdJwt.Presentation<JWT, Nothing>.toCombinedPresentationFormat(
    serializeJwt: (JWT) -> String,
): String = toCombinedPresentationFormat(serializeJwt, { it })

/**
 * Concatenates the given disclosures into a single string, separated by
 * `~`. The string also starts with "~".
 *
 * @receiver the disclosures to concatenate
 * @return the string as described above
 */
private fun Iterable<Disclosure>.concat(): String = fold("") { acc, disclosure -> "$acc~${disclosure.value}" }

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes the following claims:
 * - `iat`
 * - `nonce`
 * - `aud`
 * - `_sd_jwt`
 *
 * @param issuedAt issuance time of the envelope JWT. It will be included as `iat` claim
 * @param audience the audience of the envelope JWT. It will be included as `aud` claim
 * @param nonce the nonce of the envelope JWT. It will be included as `nonce` claim
 * @param serializeJwt a way to serialize the JWT part of the [SdJwt.Presentation]. Will be used to
 * produce the Combined Presentation format.
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped. If it contains [SdJwt.Presentation.keyBindingJwt]
 * it will be removed.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt.Presentation<JWT, *>.toEnvelopedFormat(
    issuedAt: Instant,
    nonce: String,
    audience: String,
    serializeJwt: (JWT) -> String,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val otherClaims = buildJsonObject {
        iat(issuedAt.epochSecond)
        aud(audience)
        put("nonce", nonce)
    }
    return toEnvelopedFormat(otherClaims, serializeJwt, signEnvelop)
}

/**
 * Creates an enveloped representation of the SD-JWT
 * This produces a JWT (not SD-JWT) which includes in addition to the [otherClaims]
 * the claim `_sd_jwt`
 *
 * @param otherClaims claims to be included in the envelope JWT, except "_sd_jwt"
 * @param serializeJwt a way to serialize the JWT part of the [SdJwt.Presentation]. Will be used to
 * produce the Combined Presentation format.
 * @param signEnvelop a way to sign the claims of the envelope JWT
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param ENVELOPED_JWT the type representing the envelope JWT
 * @receiver the SD-JWT (presentation) to be enveloped. If it contains [SdJwt.Presentation.keyBindingJwt]
 * it will be removed.
 * @return a JWT (not SD-JWT) as described above
 */
fun <JWT, ENVELOPED_JWT> SdJwt.Presentation<JWT, *>.toEnvelopedFormat(
    otherClaims: Claims,
    serializeJwt: (JWT) -> String,
    signEnvelop: (Claims) -> Result<ENVELOPED_JWT>,
): Result<ENVELOPED_JWT> {
    val sdJwtInCombined = noKeyBinding().toCombinedPresentationFormat(serializeJwt)
    val envelopedClaims = otherClaims.toMutableMap()
    envelopedClaims["_sd_jwt"] = JsonPrimitive(sdJwtInCombined)
    return signEnvelop(envelopedClaims)
}
