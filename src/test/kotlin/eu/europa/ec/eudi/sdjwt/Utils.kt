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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private fun JsonElement.pretty(): String = json.encodeToString(this)

fun <JWT> SdJwt<JWT>.prettyPrint(f: (JWT) -> Claims) {
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

    println("SD-JWT Disclosures without salt")
    disclosures.print()
}

/**
 * Prints these [disclosures array][Disclosure], omitting the salt.
 */
private fun List<Disclosure>.print() {
    println(
        joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") {
            val (_, name, value) = Disclosure.decode(it.value).getOrThrow()
            when (name) {
                null -> "\t[\"...salt...\", $value]"
                else -> "\t[\"...salt...\", \"$name\", $value]"
            }
        },
    )
}

fun String.removeNewLine(): String = replace("\n", "")

internal fun SdObject.assertThat(
    description: String = "",
    numOfDecoysLimit: Int = 0,
    expectedDisclosuresNo: Int = 0,
) {
    println(description)
    val sdJwtFactory = SdJwtFactory(numOfDecoysLimit = numOfDecoysLimit)
    val sdJwt = assertNotNull(sdJwtFactory.createSdJwt(this).getOrNull()).apply { prettyPrint { it } }
    assertEquals(expectedDisclosuresNo, sdJwt.disclosures.size)
    println("=====================================")
}
