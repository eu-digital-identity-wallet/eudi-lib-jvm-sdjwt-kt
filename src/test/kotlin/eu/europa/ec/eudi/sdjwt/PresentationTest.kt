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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.sdjwt.examples.complexStructuredSdJwt
import eu.europa.ec.eudi.sdjwt.examples.sdJwtVcDataV2
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class PresentationTest {

    private val issuerKey = genKey("issuer")
    private val holderKey = genKey("holder")
    private val pidSpec = sdJwt {
        //
        // Claims that are always disclosable (no selectively disclosed)
        //
        claim("iss", "https://example.com/issuer") // shortcut for put("iss", "https://example.com/issuer")
        claim("exp", 1883000000)
        claim("iat", 1683000000)
        claim("vct", "https://bmi.bund.example/credential/pid/1.0")

        //
        // Selectively disclosable claims
        // Each claim can be selectively disclosed (or not)
        sdClaim("given_name", "Erika")
        sdClaim("also_known_as", "Schwester Agnes")
        sdClaim("family_name", "Mustermann")
        sdClaim("gender", "female")
        sdClaim("birthdate", "1963-8-12")
        sdArrClaim("nationalities") {
            claim("DE")
        }
        sdClaim("birth_family_name", "Gabler")
        sdClaim("source_document_type", "id_card")

        //
        // Selectively disclosable claim using recursive options
        // All sub-claims are selectively disclosable
        // Each sub-claim can be individually disclosed
        sdObjClaim("address") {
            sdClaim("postal_code", "51147")
            sdClaim("street_address", "Heidestraße 17")
            sdClaim("locality", "Köln")
            sdClaim("country", "DE")
        }

        //
        // Selectively disclosable claim using recursive option
        // `country` is always disclosable, if `place_of_birth` is requested
        // `locality` is selectively disclosed
        //  This means that `place_of_birth` can be selectively disclosed or not.
        //  If it is selected, `country` will be also disclosed (no option to hide it)
        //  and `locality` is selectively disclosable
        sdObjClaim("place_of_birth") {
            claim("country", "DE")
            sdClaim("locality", "Berlin")
        }

        //
        // Selectively disclosable claim using structured option
        // All sub-claims are selectively disclosable
        // This means that each sub-claim can be disclosed (or not)
        objClaim("age_equal_or_over") {
            sdClaim("65", false)
            sdClaim("12", true)
            sdClaim("21", true)
            sdClaim("14", true)
            sdClaim("16", true)
            sdClaim("18", true)
        }

        cnf(holderKey.toPublicJWK())
    }

    private val issuer = NimbusSdJwtOps.issuer(
        signer = ECDSASigner(issuerKey),
        signAlgorithm = JWSAlgorithm.ES256,
    ) {
        type(JOSEObjectType("example+sd-jwt"))
        keyID(issuerKey.keyID)
    }

    private suspend fun issuedSdJwt(): SdJwt<SignedJWT> = with(NimbusSdJwtOps) {
        val sdJwt = issuer.issue(pidSpec).getOrThrow()
        println("Issued: ${sdJwt.serialize()}")
        sdJwt.prettyPrint { it.jwtClaimsSet.jsonObject() }
        sdJwt.recreateClaimsAndDisclosuresPerClaim().also { (json, map) ->
            println(json.pretty())
            map.prettyPrint()
        }
        return sdJwt
    }

    @Test
    fun `querying AllClaims or NonSdClaims against an sd-jwt with no disclosures is the same`() = runTest {
        with(NimbusSdJwtOps) {
            val sdJwt = run {
                val spec = sdJwt {
                    claim("iss", "foo")
                    claim("iat", Clock.System.now().epochSeconds)
                }
                issuer.issue(spec).getOrThrow().also {
                    assertTrue { it.disclosures.isEmpty() }
                }
            }

            val allClaims = sdJwt.present(emptySet())

            assertNotNull(allClaims)
            assertEquals(sdJwt.jwt, allClaims.jwt)

            assertTrue { allClaims.disclosures.isEmpty() }
        }
    }

    @Test
    fun `query for all claims should returned the issued sd-jwt`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val presentationSdJwt = issuedSdJwt.present(emptySet())
            assertNotNull(presentationSdJwt)
            assertEquals(issuedSdJwt.jwt, presentationSdJwt.jwt)
            assertEquals(issuedSdJwt.disclosures.size, presentationSdJwt.disclosures.size)
        }
    }

    @Test
    fun `query for non SD claim should not reveal disclosure`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val claimsToPresent = setOf("iss", "vct", "cnf")
            val query = claimsToPresent.map { ClaimPath.claim(it) }.toSet()
            val presentation = issuedSdJwt.present(query)
            assertNotNull(presentation)
            assertTrue { presentation.disclosures.isEmpty() }
        }
    }

    @Test
    fun `query for top-level SD claims reveal equal number of disclosures`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val claimsToPresent = listOf("given_name", "also_known_as", "nationalities")
            val query = claimsToPresent.map { ClaimPath.claim(it) }.toSet()
            val presentation = issuedSdJwt.present(query)
            assertNotNull(presentation)
            assertEquals(claimsToPresent.size, presentation.disclosures.size)
            assertTrue {
                presentation.disclosures.all { d ->
                    val (name, _) = d.claim()
                    name in claimsToPresent
                }
            }
        }
    }

    @Test
    fun `query for recursive claim's with no nested SD claims should reveal equal no of disclosures`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val claimsToPresent = listOf("place_of_birth", "address")
            val query = claimsToPresent.map { ClaimPath.claim(it) }.toSet()
            val presentation = issuedSdJwt.present(query)
            assertNotNull(presentation)
            assertEquals(claimsToPresent.size, presentation.disclosures.size)
            assertTrue {
                presentation.disclosures.all { d ->
                    val (name, _) = d.claim()
                    name in claimsToPresent
                }
            }
        }
    }

    @Test
    fun `query for a sd claim nested inside a recursive object should reveal parent & child disclosures`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val query = setOf(ClaimPath.claim("place_of_birth").claim("locality"))
            val presentation = issuedSdJwt.present(query)
            assertNotNull(presentation)
            assertEquals(2, presentation.disclosures.size)
            val disclosedClaimNames = presentation.disclosures.map { it.claim().name() }
            assertContains(disclosedClaimNames, "place_of_birth")
            assertContains(disclosedClaimNames, "locality")
        }
    }

    @Test
    fun `query for a sd claim nested inside a structured SD object should reveal the child disclosure`() = runTest {
        with(NimbusSdJwtOps) {
            val issuedSdJwt = issuedSdJwt()
            val query = setOf(ClaimPath.claim("age_equal_or_over").claim("18"))
            val presentation = issuedSdJwt.present(query)
            assertNotNull(presentation)
            assertEquals(1, presentation.disclosures.size)
            assertEquals("18", presentation.disclosures.first().claim().name())
        }
    }

    @Test
    fun `query for a structured SD claim with only plain sub-claims reveals no disclosures`() = runTest {
        with(NimbusSdJwtOps) {
            val spec = sdJwt {
                objClaim("credentialSubject") {
                    claim("type", "VaccinationEvent")
                }
            }
            val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }
            val p = sdJwt.present(setOf(ClaimPath.claim("credentialSubject").claim("type")))
            assertNotNull(p)
            assertTrue { p.disclosures.isEmpty() }
        }
    }

    @Test
    fun `query for a recursive SD claim with only plain sub-claims reveals only the container disclosure`() = runTest {
        with(NimbusSdJwtOps) {
            val spec = sdJwt {
                sdObjClaim("credentialSubject") {
                    claim("type", "VaccinationEvent")
                }
            }
            val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }

            val p = sdJwt.present(setOf(ClaimPath.claim("credentialSubject").claim("type")))
            assertNotNull(p)
            assertEquals(1, p.disclosures.size)
            assertEquals("credentialSubject", p.disclosures.firstOrNull()?.claim()?.name())
        }
    }

    @Test
    fun `query for sd array`() = runTest {
        with(NimbusSdJwtOps) {
            val spec = sdJwt {
                arrClaim("evidence") {
                    sdObjClaim {
                        sdClaim("type", "document")
                    }
                    objClaim {
                        claim("foo", "bar")
                    }
                }
            }

            val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }
            // All claims below should not require a disclosure
            val q1 = setOf(
                ClaimPath.claim("evidence"),
                ClaimPath.claim("evidence").arrayElement(1),
                ClaimPath.claim("evidence").arrayElement(1).claim("foo"),
            )
            val p1 = sdJwt.present(q1)
            assertNotNull(p1)
            assertTrue { p1.disclosures.isEmpty() }

            val p2 = sdJwt.present(
                setOf(
                    ClaimPath.claim("evidence").arrayElement(0).claim("type"),
                ),
            )
            assertNotNull(p2)
            // To reveal `type` we need
            // - an array disclosure for the first element of the array
            // - a disclosure to reveal `type`
            assertEquals(2, p2.disclosures.size)
        }
    }

    @Test
    fun `querying for a recursive SD array`() = runTest {
        with(NimbusSdJwtOps) {
            val spec = sdJwt {
                sdArrClaim("evidence") {
                    sdObjClaim {
                        sdClaim("type", "document")
                    }
                    objClaim {
                        claim("foo", "bar")
                    }
                }
            }

            val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }

            val q1 = setOf(
                ClaimPath.claim("evidence"),
                ClaimPath.claim("evidence").arrayElement(1),
                ClaimPath.claim("evidence").arrayElement(1).claim("foo"),
            )
            val p1 = sdJwt.present(q1)
            assertNotNull(p1)
            assertEquals(1, p1.disclosures.size)
            assertEquals("evidence", p1.disclosures.firstOrNull()?.claim()?.name())

            val p2 = sdJwt.present(
                setOf(
                    ClaimPath.claim("evidence").arrayElement(0).claim("type"),
                ),
            )
            assertNotNull(p2)
            // To reveal `type` we need
            // - "evidence" <- since it is a recursive array
            // - an array disclosure for the first element of the array
            // - a disclosure to reveal `type`
            assertEquals(3, p2.disclosures.size)
        }
    }

    @Test
    fun complexStructuredSdJwt() = runTest {
        with(NimbusSdJwtOps) {
            val sdJwt = issuer.issue(complexStructuredSdJwt).getOrThrow().also { it.prettyPrintAll() }
            val p =
                sdJwt.present(
                    setOf(
                        ClaimPath.claim("verified_claims")
                            .claim("verification")
                            .claim("evidence")
                            .arrayElement(0)
                            .claim("document"),
                    ),
                )
            assertNotNull(p)
        }
    }

    @Test
    fun sdJwtVcDataV2() = runTest {
        with(NimbusSdJwtOps) {
            val sdJwt = issuer.issue(sdJwtVcDataV2).getOrThrow().also { it.prettyPrintAll() }
            val p = sdJwt.present(
                setOf(
                    ClaimPath.claim("credentialSubject").claim("recipient").claim("gender"),

                ),
            )
            assertNotNull(p)
        }
    }

    private fun SdJwt<SignedJWT>.prettyPrintAll() = with(NimbusSdJwtOps) {
        val (claims, disclosuresPerClaim) = recreateClaimsAndDisclosuresPerClaim()
        prettyPrint { it.jwtClaimsSet.jsonObject() }
        println(claims.pretty())
        disclosuresPerClaim.forEach { (p, ds) ->
            println("$p - ${if (ds.isEmpty()) "plain" else "$ds"}")
        }
    }

    private fun JsonObject.pretty(): String = jsonSupport.encodeToString(JsonObject(this))
    private val jsonSupport: Json = Json { prettyPrint = true }
    private fun genKey(kid: String): ECKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
}
