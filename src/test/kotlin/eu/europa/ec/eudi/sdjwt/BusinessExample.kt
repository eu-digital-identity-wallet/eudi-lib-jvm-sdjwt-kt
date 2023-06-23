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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class Address(
    val country: String,
    @SerialName("street_address") val streetAddress: String,
    val locality: String? = null,
    val region: String,
)

@Serializable
data class FooClaim(
    @SerialName("sub") val subject: String,
    @SerialName("iat") val issuedAt: Int,
    @SerialName("home_address") val homeAddress: Address,
    @SerialName("work_address") val workAddress: Address,
)

fun FooClaim.usingDsl(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
    saltProvider: SaltProvider = SaltProvider.Default,
): DisclosedClaimSet {
    val jsonObject = json.encodeToJsonElement(this).jsonObject

    // Here we define that home & work address are to be selectively disclosable
    val plainClaims = JsonObject(jsonObject.filterNot { it.key == "home_address" || it.key == "work_address" })

    fun Address.disclose(claimName: String, plainClaims: (Claim) -> Boolean = { false }) =
        SdJwtDsl.structured(claimName, json.encodeToJsonElement<Address>(this).jsonObject, plainClaims)

    val dsl = SdJwtDsl.TopLevel(
        plain = SdJwtDsl.plain(plainClaims),
        structured = setOf(
            homeAddress.disclose("home_address"),
            workAddress.disclose("work_address") { it.name() == "country" },
        ),
    )

    return DisclosedClaimSet.disclose(hashAlgorithm, saltProvider, 2, dsl).getOrThrow()
}

fun FooClaim.usignBuilder(
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA3_256,
    saltProvider: SaltProvider = SaltProvider.Default,
): DisclosedClaimSet {
    val jsonObject = json.encodeToJsonElement(this).jsonObject

    // Here we define that home & work address are to be selectively disclosable
    val plainClaims = JsonObject(jsonObject.filterNot { it.key == "home_address" || it.key == "work_address" })
    val sdJwtDsl = sdJwt {
        plain(plainClaims)
        structured("work_address") {
            jsonObject["work_address"]!!
                .jsonObject
                .forEach { x -> flat(x.toPair()) }
        }

        structured("home_address") {
            flat {
                put("country", "GR")
                put("steet", "Markopoulo")
            }
        }
    }
    return DisclosedClaimSet.disclose(HashAlgorithm.SHA3_256, SaltProvider.Default, 0, sdJwtDsl).getOrThrow()
}

private val json = Json { prettyPrint = true }

fun main() {
    val homeAddress =
        Address(country = "GR", streetAddress = "12 Foo Str", locality = null, region = "Attica")
    val workAddress =
        Address(country = "DE", streetAddress = "Schulstr. 12", locality = "Schulpforta", region = "Sachsen-Anhalt")
    val fooClaim = FooClaim(
        subject = "subject",
        issuedAt = 10,
        homeAddress = homeAddress,
        workAddress = workAddress,
    )

    println("Advanced  example. Using DSL")
    println("All attributes of both work and home address are selectively disclosable")
    println("For work address country is plain")
    fooClaim.usingDsl().also { it.print() }

    println("Advanced  example. Using builder")

    fooClaim.usignBuilder().also { it.print() }
}

fun DisclosedClaimSet.print() {
    disclosures.forEach { println(it.claim()) }
    claimSet.also { println(json.encodeToString(it)) }
}
