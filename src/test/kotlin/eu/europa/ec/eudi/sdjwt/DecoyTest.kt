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
import kotlin.test.Test

sealed interface Membership {
    val name: String

    data class Simple(override val name: String) : Membership
    data class Premium(override val name: String, val premiumMembershipNumber: String) : Membership
}

internal class DecoyTest {
    private val simpleMembership = Membership.Simple(name = "Markus")
    private val premiumMembership = Membership.Premium(name = "Markus", premiumMembershipNumber = "1234")

    @Test
    fun baseline() {
        fun issue(m: Membership): SdJwt.Issuance<SignedJWT> {
            val issuer = SampleIssuer.issuer()
            val spec = sdJwtSpec(m)
            return issuer.issue(spec).getOrThrow()
        }

        val (simpleSdJwts, premiumSdJwts) =
            (1..100)
                .map { issue(simpleMembership) to issue(premiumMembership) }
                .unzip()

        fun printFreq(s: String, f: Map<Int, Int>) {
            println("$s\t(DigestNo/Occurrences) $f")
        }

        simpleSdJwts.digestFrequency().also { printFreq("simple", it) }
        premiumSdJwts.digestFrequency().also { printFreq("premium", it) }
    }

    private fun Iterable<SdJwt.Issuance<SignedJWT>>.digestFrequency() =
        this
            .map { it.countDigests() }
            .groupBy { it }
            .mapValues { (_, vs) -> vs.size }
            .toSortedMap()

    // That's not safe, but it will do for them example
    // counts only top-level digests
    private fun SdJwt.Issuance<SignedJWT>.countDigests() = jwt.jwtClaimsSet.asClaims().directDigests().count()

    private fun sdJwtSpec(membership: Membership) = sdJwt(5) {
        sd("name", membership.name)
        if (membership is Membership.Premium) {
            sd("premiumMembershipNumber", membership.premiumMembershipNumber)
        }
    }
}

private object SampleIssuer {
    const val KEY_ID = "signing-key-01"
    private val alg = JWSAlgorithm.ES256
    val key: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(KEY_ID)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate()
    private val factory = SdJwtFactory(numOfDecoysLimit = 5)

    fun issuer(): SdJwtIssuer<SignedJWT> =
        SdJwtIssuer.nimbus(signer = ECDSASigner(key), signAlgorithm = alg, sdJwtFactory = factory)
}
