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

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcIssuerMetadata
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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

fun SdJwt<SignedJWT>.prettyPrint() {
    prettyPrint { it.jwtClaimsSet.jsonObject() }
}

fun <JWT> SdJwt<JWT>.prettyPrint(f: (JWT) -> JsonObject) {
    println("SD-JWT with ${disclosures.size} disclosures")
    disclosures.forEach { d ->
        val kind = when (d) {
            is Disclosure.ArrayElement -> "\t - ArrayEntry ${d.claim().value().pretty()}"
            is Disclosure.ObjectProperty -> "\t - ObjectProperty ${d.claim().first} = ${d.claim().second}"
        }
        println(kind)
    }
    println("SD-JWT payload")
    f(jwt).also { println(json.encodeToString(it)) }

    println("SD-JWT disclosures")
    disclosures.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { disclosure ->
        val (_, name, value) = Disclosure.decode(disclosure.value).getOrThrow()
        buildJsonArray {
            add(JsonPrimitive("...salt..."))
            name?.let { add(JsonPrimitive(it)) }
            add(value)
        }.toString().prependIndent("\t")
    }.run(::println)
}

fun DisclosuresPerClaimPath.prettyPrint() {
    println("SD-JWT disclosures per claim")
    forEach { (claim, disclosures) ->
        println("$claim ->")
        disclosures.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { disclosure ->
            val (_, name, value) = Disclosure.decode(disclosure.value).getOrThrow()
            buildJsonArray {
                add(JsonPrimitive("...salt..."))
                name?.let { add(JsonPrimitive(it)) }
                add(value)
            }.toString().prependIndent("\t")
        }.run(::println)
    }
}

fun String.removeNewLine(): String = replace("\n", "")

internal fun DisclosableObject.assertThat(description: String = "", expectedDisclosuresNo: Int = 0) {
    println(description)
    val sdJwtFactory = SdJwtFactory.Default
    val sdJwt = assertNotNull(sdJwtFactory.createSdJwt(this).getOrNull()).apply { prettyPrint { it } }
    assertEquals(expectedDisclosuresNo, sdJwt.disclosures.size)
    println("=====================================")
}

internal fun loadRsaKey(name: String): RSAKey = RSAKey.parse(loadResource(name))

internal fun loadSdJwt(name: String): String = loadResource(name).removeNewLine()

internal fun loadJwt(name: String): String = loadResource(name).removeNewLine()

internal object HttpMock {

    fun clientReturning(issuerMeta: SdJwtVcIssuerMetadata): HttpClient =
        HttpClient { _ ->
            respond(
                Json.encodeToString(issuerMeta),
                HttpStatusCode.OK,
                headers { append(HttpHeaders.ContentType, ContentType.Application.Json) },
            )
        }

    @Suppress("TestFunctionName")
    private fun HttpClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
        }
}
