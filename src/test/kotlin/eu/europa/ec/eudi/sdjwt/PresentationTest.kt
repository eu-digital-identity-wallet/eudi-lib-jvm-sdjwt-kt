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
import eu.europa.ec.eudi.sdjwt.vc.toClaimPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.test.*

class PresentationTest {

    private val issuerKey = genKey("issuer")
    private val holderKey = genKey("holder")
    private val pidSpec = sdJwt {
        //
        // Claims that are always disclosable (no selectively disclosed)
        //
        plain {
            iss("https://example.com/issuer") // shortcut for put("iss", "https://example.com/issuer")
            exp(1883000000)
            iat(1683000000)
            put("vct", "https://bmi.bund.example/credential/pid/1.0")
        }

        //
        // Selectively disclosable claims
        // Each claim can be selectively disclosed (or not)
        sd {
            put("given_name", "Erika")
            put("also_known_as", "Schwester Agnes")
            put("family_name", "Mustermann")
            put("gender", "female")
            put("birthdate", "1963-8-12")
            putJsonArray("nationalities") {
                add("DE")
            }
            put("birth_family_name", "Gabler")
            put("source_document_type", "id_card")
        }

        //
        // Selectively disclosable claim using recursive options
        // All sub-claims are selectively disclosable
        // Each sub-claim can be individually disclosed
        recursive("address") {
            sd {
                put("postal_code", "51147")
                put("street_address", "Heidestraße 17")
                put("locality", "Köln")
                put("country", "DE")
            }
        }

        //
        // Selectively disclosable claim using recursive option
        // `country` is always disclosable, if `place_of_birth` is requested
        // `locality` is selectively disclosed
        //  This means that `place_of_birth` can be selectively disclosed or not.
        //  If it is selected, `country` will be also disclosed (no option to hide it)
        //  and `locality` is selectively disclosable
        recursive("place_of_birth") {
            plain("country", "DE")
            sd("locality", "Berlin")
        }

        //
        // Selectively disclosable claim using structured option
        // All sub-claims are selectively disclosable
        // This means that each sub-claim can be disclosed (or not)
        structured("age_equal_or_over") {
            sd {
                put("65", false)
                put("12", true)
                put("21", true)
                put("14", true)
                put("16", true)
                put("18", true)
            }
        }
        cnf(holderKey.toPublicJWK())
    }

    private val issuer = SdJwtIssuer.nimbus(signer = ECDSASigner(issuerKey), signAlgorithm = JWSAlgorithm.ES256) {
        type(JOSEObjectType("example+sd-jwt"))
        keyID(issuerKey.keyID)
    }

    private val issuedSdJwt: SdJwt.Issuance<SignedJWT> by lazy {
        val sdJwt = issuer.issue(pidSpec).getOrThrow()
        println("Issued: ${sdJwt.serialize()}")
        sdJwt.prettyPrint { it.jwtClaimsSet.asClaims() }
        sdJwt.recreateClaimsAndDisclosuresPerClaim { it.jwtClaimsSet.asClaims() }.also { (json, map) ->
            println(json.pretty())
            map.prettyPrint()
        }
        sdJwt
    }

    @Test
    fun `querying AllClaims or NonSdClaims against an sd-jwt with no disclosures is the same`() {
        val sdJwt = run {
            val spec = sdJwt {
                iss("foo")
                iat(Instant.now().epochSecond)
            }
            issuer.issue(spec).getOrThrow().also {
                assertTrue { it.disclosures.isEmpty() }
            }
        }

        val allClaims = sdJwt.presentJsonPointersMatching { true }

        assertNotNull(allClaims)
        assertEquals(sdJwt.jwt, allClaims.jwt)

        assertTrue { allClaims.disclosures.isEmpty() }
    }

    @Test
    fun `query for all claims should returned the issued sd-jwt`() {
        val presentationSdJwt = issuedSdJwt.presentJsonPointersMatching { true }
        assertNotNull(presentationSdJwt)
        assertEquals(issuedSdJwt.jwt, presentationSdJwt.jwt)
        assertEquals(issuedSdJwt.disclosures.size, presentationSdJwt.disclosures.size)
    }

    @Test
    fun `query for non SD claim should not reveal disclosure`() {
        val claimsToPresent = setOf("iss", "vct", "cnf")
        val query = claimsToPresent.map { JsonPointer.Root.child(it).toClaimPath(false) }.toSet()
        val presentation = issuedSdJwt.present(query)
        assertNotNull(presentation)
        assertTrue { presentation.disclosures.isEmpty() }
    }

    @Test
    fun `query for top-level SD claims reveal equal number of disclosures`() {
        val claimsToPresent = listOf("given_name", "also_known_as", "nationalities")
        val query = claimsToPresent.map { JsonPointer.Root.child(it).toClaimPath(false) }.toSet()
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

    @Test
    fun `query for recursive claim's with no nested SD claims should reveal equal no of disclosures`() {
        val claimsToPresent = listOf("place_of_birth", "address")
        val query = claimsToPresent.map { JsonPointer.Root.child(it).toClaimPath(false) }.toSet()
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

    @Test
    fun `query for a sd claim nested inside a recursive object should reveal parent & child disclosures`() {
        val query = setOf("/place_of_birth/locality".claimPointer().toClaimPath(false))
        val presentation = issuedSdJwt.present(query)
        assertNotNull(presentation)
        assertEquals(2, presentation.disclosures.size)
        val disclosedClaimNames = presentation.disclosures.map { it.claim().name() }
        assertContains(disclosedClaimNames, "place_of_birth")
        assertContains(disclosedClaimNames, "locality")
    }

    @Test
    fun `query for a sd claim nested inside a structured SD object should reveal the child disclosure`() {
        val query = setOf("/age_equal_or_over/18".claimPointer())
        val presentation = issuedSdJwt.presentJsonPointersMatching { it in query }
        assertNotNull(presentation)
        assertEquals(1, presentation.disclosures.size)
        assertEquals("18", presentation.disclosures.first().claim().name())
    }

    @Test
    fun `query for a structured SD claim with only plain sub-claims reveals no disclosures`() {
        val spec = sdJwt {
            structured("credentialSubject") {
                plain {
                    put("type", "VaccinationEvent")
                }
            }
        }
        val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }
        val p = sdJwt.present(setOf("/credentialSubject/type".claimPointer().toClaimPath(false)))
        assertNotNull(p)
        assertTrue { p.disclosures.isEmpty() }
    }

    @Test
    fun `query for a recursive SD claim with only plain sub-claims reveals only the container disclosure`() {
        val spec = sdJwt {
            recursive("credentialSubject") {
                plain {
                    put("type", "VaccinationEvent")
                }
            }
        }
        val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }

        val p = sdJwt.present(setOf("/credentialSubject/type".claimPointer().toClaimPath(false)))
        assertNotNull(p)
        assertEquals(1, p.disclosures.size)
        assertEquals("credentialSubject", p.disclosures.firstOrNull()?.claim()?.name())
    }

    @Test
    fun `query for sd array`() {
        val spec = sdJwt {
            sdArray("evidence") {
                buildSdObject {
                    sd {
                        put("type", "document")
                    }
                }
                plain {
                    put("foo", "bar")
                }
            }
        }

        val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }
        // All claims below should not require a disclosure
        val q1 = setOf("/evidence", "/evidence/1", "/evidence/1/foo").map { it.claimPointer().toClaimPath(false) }.toSet()
        val p1 = sdJwt.present(q1)
        assertNotNull(p1)
        assertTrue { p1.disclosures.isEmpty() }

        val p2 = sdJwt.present(setOf("/evidence/0/type".claimPointer().toClaimPath(false)))
        assertNotNull(p2)
        // To reveal `type` we need
        // - an array disclosure for the first element of the array
        // - a disclosure to reveal `type`
        assertEquals(2, p2.disclosures.size)
    }

    @Test
    fun `querying for a recursive SD array`() {
        val spec = sdJwt {
            recursiveArray("evidence") {
                buildSdObject {
                    sd {
                        put("type", "document")
                    }
                }
                plain {
                    put("foo", "bar")
                }
            }
        }

        val sdJwt = issuer.issue(spec).getOrThrow().also { it.prettyPrintAll() }

        val q1 = setOf("/evidence", "/evidence/1", "/evidence/1/foo").map { it.claimPointer().toClaimPath(false) }.toSet()
        val p1 = sdJwt.present(q1)
        assertNotNull(p1)
        assertEquals(1, p1.disclosures.size)
        assertEquals("evidence", p1.disclosures.firstOrNull()?.claim()?.name())

        val p2 = sdJwt.present(setOf("/evidence/0/type".claimPointer().toClaimPath(false)))
        assertNotNull(p2)
        // To reveal `type` we need
        // - "evidence" <- since it is a recursive array
        // - an array disclosure for the first element of the array
        // - a disclosure to reveal `type`
        assertEquals(3, p2.disclosures.size)
    }

    @Test
    fun complexStructuredSdJwt() {
        val sdJwt = issuer.issue(complexStructuredSdJwt).getOrThrow().also { it.prettyPrintAll() }
        val p =
            sdJwt.present(setOf("/verified_claims/verification/evidence/0/document".claimPointer().toClaimPath(false)))
        assertNotNull(p)
    }

    @Test
    fun sdJwtVcDataV2() {
        val sdJwt = issuer.issue(sdJwtVcDataV2).getOrThrow().also { it.prettyPrintAll() }
        val p = sdJwt.present(setOf("/credentialSubject/recipient/gender".claimPointer().toClaimPath(false)))
        assertNotNull(p)
    }
}

private fun SdJwt<SignedJWT>.prettyPrintAll() {
    val (claims, disclosuresPerClaim) = recreateClaimsAndDisclosuresPerClaim { it.jwtClaimsSet.asClaims() }
    prettyPrint { it.jwtClaimsSet.asClaims() }
    println(claims.pretty())
    disclosuresPerClaim.forEach { (p, ds) ->
        println("$p - ${if (ds.isEmpty()) "plain" else "$ds"}")
    }
}

private fun String.claimPointer(): JsonPointer = requireNotNull(JsonPointer.parse(this))

private fun Claims.pretty(): String = jsonSupport.encodeToString(JsonObject(this))
private val jsonSupport: Json = Json { prettyPrint = true }
private fun genKey(kid: String): ECKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
