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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

private typealias DisclosurePerDigest = Map<DisclosureDigest, Disclosure>

fun SdJwt<Claims, *>.recreateClaims(): Claims =
    RecreateClaims.recreateClaims(disclosures, jwt)

fun DisclosedClaims.recreateClaims(): Claims =
    RecreateClaims.recreateClaims(disclosures, claimSet)

object RecreateClaims {

    fun recreateClaims(disclosures: Set<Disclosure>, claims: Claims): Claims {
        val sdAlgorithm = claims.hashAlgorithm()
        require(sdAlgorithm != null || disclosureDigests(claims).isEmpty()) { "Missing hashing algorithm" }
        if (sdAlgorithm == null) {
            return claims
        }
        val disclosuresPerDigest = disclosures.associateBy { DisclosureDigest.digest(sdAlgorithm, it.value).getOrThrow() }
        val (outDisclosuresPerDigest, outClaims) = replaceDigestsRecursively(disclosuresPerDigest, claims - "_sd_alg")
        require(outDisclosuresPerDigest.isEmpty()) {
            "Could not find digests for disclosures ${outDisclosuresPerDigest.values.map { it.claim().name() }}"
        }
        return outClaims
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun replaceDigestsRecursively(disclosuresPerDigest: DisclosurePerDigest, claims: Claims): Pair<DisclosurePerDigest, Claims> {
        val (tmpDpD, tmpCs) = replaceDigests(disclosuresPerDigest, claims)
        val outCs = tmpCs.toMutableMap()
        var outDpd = tmpDpD.toMutableMap()
        for (c in tmpCs) {
            when (val cVal = c.value) {
                is JsonObject -> {
                    val (iDpd, iCs) = replaceDigestsRecursively(tmpDpD, cVal)
                    outCs[c.key] = JsonObject(iCs)
                    outDpd = iDpd.toMutableMap()
                }
                is JsonArray -> {
                    outCs[c.key] = buildJsonArray {
                        addAll(
                            cVal.map {
                                if (it is JsonObject) {
                                    val (iDpd, iCs) = replaceDigestsRecursively(tmpDpD, it.jsonObject)
                                    outDpd = iDpd.toMutableMap()
                                    JsonObject(iCs)
                                } else { it }
                            },
                        )
                    }
                }
                else -> {
                    outCs[c.key] = cVal
                }
            }
        }

        return outDpd to outCs
    }

    private fun replaceDigests(
        disclosuresPerDigest: DisclosurePerDigest,
        claims: Claims,
    ): Pair<DisclosurePerDigest, Claims> {
        val outClaims = claims.toMutableMap()
        val outDisclosuresPerDigest = disclosuresPerDigest.toMutableMap()

        fun replaceDigest(digest: DisclosureDigest, disclosure: Disclosure) {
            val (name, value) = disclosure.claim()
            require(!claims.containsKey(name)) { "Failed to embed disclosure with key $name. Already present" }
            outDisclosuresPerDigest.remove(digest)
            outClaims[name] = value
        }

        // Replace each digest with the claim from the disclosure
        for (digest in claims.ownDigests()) {
            outDisclosuresPerDigest[digest]?.let { disclosure -> replaceDigest(digest, disclosure) }
        }
        // Remove _sd claim
        outClaims.remove("_sd")

        return outDisclosuresPerDigest to outClaims
    }

    private fun Claims.ownDigests(): Set<DisclosureDigest> =
        this["_sd"]?.jsonArray
            ?.map { DisclosureDigest.wrap(it.jsonPrimitive.content).getOrThrow() }
            ?.toSet()
            ?: emptySet()

    private fun Claims.hashAlgorithm(): HashAlgorithm? =
        this["_sd_alg"]?.let { HashAlgorithm.fromString(it.jsonPrimitive.content) }
}
