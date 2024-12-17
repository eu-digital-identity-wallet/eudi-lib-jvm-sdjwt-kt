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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

sealed interface Membership {
    val name: String

    data class Simple(override val name: String) : Membership
    data class Premium(override val name: String, val premiumMembershipNumber: String) : Membership
}

internal class DecoyTest {
    private val simpleMembership = Membership.Simple(name = "Markus")
    private val premiumMembership = Membership.Premium(name = "Markus", premiumMembershipNumber = "1234")

    @Test
    fun `make sure that kind membership is not revealed via digests number using hint on the spec`() = runTest {
        val minimumDigests = 5
        val simpleMembershipSpec = simpleMembership.sdJwtSpec(minimumDigests)
        val premiumMembershipSpec = premiumMembership.sdJwtSpec(minimumDigests)
        val issuer = SampleIssuer()
        val (simpleSdJwts, premiumSdJwts) =
            (1..100)
                .map { issuer.issue(simpleMembershipSpec) to issuer.issue(premiumMembershipSpec) }
                .unzip()

        fun printFreq(s: String, f: Map<Int, Int>) {
            println("$s\t(DigestNo/Occurrences) $f")
        }

        val simpleFreq = simpleSdJwts.digestFrequency().also { printFreq("simple", it) }
        val premiumFreq = premiumSdJwts.digestFrequency().also { printFreq("premium", it) }

        assertEquals(1, simpleFreq.size)
        assertEquals(1, premiumFreq.size)

        assertTrue { simpleFreq.keys.first() == premiumFreq.keys.first() }
    }

    @Test
    fun `make sure that kind membership is not revealed via digests number using global hint`() = runTest {
        val simpleMembershipSpec = simpleMembership.sdJwtSpec(null)
        val premiumMembershipSpec = premiumMembership.sdJwtSpec(null)

        val minimumDigests = 5
        val issuer = SampleIssuer(globalMinDigests = minimumDigests)

        assertEquals(minimumDigests, issuer.issue(simpleMembershipSpec).countDigests())
        assertEquals(minimumDigests, issuer.issue(premiumMembershipSpec).countDigests())
    }

    private fun Iterable<SdJwt<SignedJWT>>.digestFrequency() =
        this
            .map { it.countDigests() }
            .groupBy { it }
            .mapValues { (_, vs) -> vs.size }
            .toSortedMap()

    // That's not safe, but it will do for them example
    // counts only top-level digests
    private fun SdJwt<SignedJWT>.countDigests() = jwt.jwtClaimsSet.jsonObject().directDigests().count()

    private fun Membership.sdJwtSpec(minimumDigests: Int?) =
        sdJwt(minimumDigests) {
            sdClaim("name", name)
            if (this@sdJwtSpec is Membership.Premium) {
                sdClaim("premiumMembershipNumber", premiumMembershipNumber)
            }
        }
}

private class SampleIssuer(globalMinDigests: Int? = null) {
    val keyId = "signing-key-01"
    private val alg = JWSAlgorithm.ES256
    val key: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(keyId)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate()

    private val issuer: SdJwtIssuer<SignedJWT> =
        NimbusSdJwtOps.issuer(
            sdJwtFactory = SdJwtFactory(fallbackMinimumDigests = globalMinDigests?.let(::MinimumDigests)),
            signer = ECDSASigner(key),
            signAlgorithm = alg,
        )

    suspend fun issue(sdElements: DisclosableObject): SdJwt<SignedJWT> =
        issuer.issue(sdElements).getOrThrow()
}
