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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

fun JsonObject.extractClaim(attributeName: String): Pair<JsonObject, JsonObject> {
    val otherClaims = JsonObject(filterKeys { it != attributeName })
    val claimToBeDisclosed: JsonObject = firstNotNullOfOrNull {
        if (it.key == attributeName) {
            it.value
        } else {
            null
        }
    }?.let { JsonObject(mapOf(attributeName to it)) } ?: JsonObject(emptyMap())
    return otherClaims to claimToBeDisclosed
}

val json = Json { prettyPrint = true }
private fun JsonElement.pretty(): String = json.encodeToString(this)
fun <JWT> SdJwt<JWT, *>.prettyPrint(f: (JWT) -> Claims) {
    val type = when (this) {
        is SdJwt.Issuance -> "issuance"
        is SdJwt.Presentation -> "presentation"
    }
    println("SD-JWT of $type type with ${disclosures.size} disclosures")
    disclosures.forEach { d ->
        val kind = when (d) {
            is Disclosure.ArrayElement -> "\t - ArrayEntry ${d.claim().value().pretty()}"
            is Disclosure.ObjectProperty -> "\t - ObjectProperty ${d.claim().first} = ${d.claim().second}"
        }
        println(kind)
    }
    println("SD-JWT payload")
    f(jwt).also { println(json.encodeToString(it)) }
}

fun String.removeNewLine(): String = replace("\n", "")

/**
 * Creates a [presentation SD-JWT][SdJwt.Presentation]
 *
 * @param keyBindingJwt optional, the Holder Binding JWT to include
 * @param selectivelyDisclose a predicate of the [claims][SdJwt.Issuance.disclosures]
 * to be selectively disclosed into the presentation
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param KB_JWT the type representing the Key Binding part of the SD-JWT
 * @return the presentation
 *
 * @receiver the [issued SD-JWT][SdJwt.Issuance] from which the presentation will be created
 */
fun <JWT, KB_JWT> SdJwt.Issuance<JWT>.present(
    keyBindingJwt: KB_JWT? = null,
    selectivelyDisclose: (Claim) -> Boolean,
): SdJwt.Presentation<JWT, KB_JWT> =
    SdJwt.Presentation(
        jwt,
        disclosures.filter { disclosure ->
            when (disclosure) {
                is Disclosure.ArrayElement -> true // TODO Figure out what to do
                is Disclosure.ObjectProperty -> selectivelyDisclose(disclosure.claim())
            }
        }.toSet(),
        keyBindingJwt,
    )
