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
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.runCatchingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Lookup the Type Metadata of a VCT.
 */
fun interface LookupTypeMetadata {

    suspend operator fun invoke(vct: Vct, expectedIntegrity: DocumentIntegrity?): Result<SdJwtVcTypeMetadata?>

    companion object {
        fun firstNotNullOfOrNull(first: LookupTypeMetadata, vararg remaining: LookupTypeMetadata): LookupTypeMetadata {
            val lookups = listOf(first, *remaining)
            return LookupTypeMetadata { vct, expectedIntegrity ->
                runCatchingCancellable {
                    lookups.firstNotNullOfOrNull { lookup -> lookup(vct, expectedIntegrity).getOrNull() }
                }
            }
        }
    }
}

/**
 * Lookup the Type Metadata of an HTTPS URL VCT using Ktor.
 */
class LookupTypeMetadataUsingKtor(
    private val httpClient: HttpClient,
    private val sriValidator: SRIValidator? = SRIValidator(),
) : LookupTypeMetadata {

    override suspend fun invoke(vct: Vct, expectedIntegrity: DocumentIntegrity?): Result<SdJwtVcTypeMetadata?> {
        val url = runCatching { Url(vct.value) }.getOrNull()
        return runCatchingCancellable {
            when (url) {
                is Url ->
                    with(GetSubResourceKtorOps(sriValidator)) {
                        httpClient.getJsonOrNull<SdJwtVcTypeMetadata>(url, expectedIntegrity)
                    }

                else -> null
            }
        }
    }
}

data class ResolvedTypeMetadata(
    val vct: Vct,
    val name: String?,
    val description: String?,
    val display: List<DisplayMetadata>,
    val claims: List<ClaimMetadata>,
) {
    init {
        SdJwtVcTypeMetadata.ensureValidPaths(claims)
    }

    companion object
}

private fun ResolvedTypeMetadata.Companion.empty(vct: Vct): ResolvedTypeMetadata =
    ResolvedTypeMetadata(
        vct = vct,
        name = null,
        description = null,
        display = emptyList(),
        claims = emptyList(),
    )

/**
 * Resolver for [ResolvedTypeMetadata].
 */
interface ResolveTypeMetadata {

    val lookupTypeMetadata: LookupTypeMetadata

    /**
     * Resolves the [ResolvedTypeMetadata] for [vct].
     */
    suspend operator fun invoke(
        vct: Vct,
        expectedIntegrity: DocumentIntegrity?,
    ): Result<ResolvedTypeMetadata> =
        runCatchingCancellable {
            tailrec suspend fun resolve(
                vct: Vct,
                expectedIntegrity: DocumentIntegrity?,
                accumulator: ResolvedTypeMetadata,
                resolved: Set<Vct>,
            ): ResolvedTypeMetadata {
                require(vct !in resolved) { "cyclical reference detected, vct $vct has been previously resolved" }
                val current = lookupTypeMetadata(vct, expectedIntegrity)
                    .getOrThrow() ?: error("unable to lookup Type Metadata for $vct")
                val updatedAccumulator = accumulator + current
                val parent = current.extends?.let { Vct(it) }
                val parentIntegrity = current.extendsIntegrity
                return if (null != parent) {
                    resolve(parent, parentIntegrity, updatedAccumulator, resolved + vct)
                } else {
                    updatedAccumulator
                }
            }

            resolve(vct, expectedIntegrity, ResolvedTypeMetadata.empty(vct), emptySet())
        }

    companion object {
        operator fun invoke(lookupTypeMetadata: LookupTypeMetadata): ResolveTypeMetadata =
            object : ResolveTypeMetadata {
                override val lookupTypeMetadata: LookupTypeMetadata = lookupTypeMetadata
            }
    }
}

/**
 * Merges the [ResolvedTypeMetadata] of a [Vct] with those of its [parent].
 *
 * The resulting [ResolvedTypeMetadata] has:
 * 1. vct: the vct of this instance
 * 2. name: the name of this instance, or the name of parent in case this has no name
 * 3. description: the description of this instance, or the description of parent in case this has no description
 * 4. display: the result of [mergeDisplay]
 * 5. claims: the result of [mergeClaims]
 *
 * @param parent the Type Metadata of a parent Vct
 * @param mergeDisplay function used to merge the [DisplayMetadata] of this instance with those of [parent]
 * @param mergeClaims function used to merge the [ClaimMetadata] of this instance with those of [parent]
 */
private fun ResolvedTypeMetadata.mergeWith(
    parent: SdJwtVcTypeMetadata,
    mergeDisplay: (List<DisplayMetadata>, List<DisplayMetadata>) -> List<DisplayMetadata>,
    mergeClaims: (List<ClaimMetadata>, List<ClaimMetadata>) -> List<ClaimMetadata>,
): ResolvedTypeMetadata {
    val newName = name ?: parent.name
    val newDescription = description ?: parent.description
    val newDisplay = mergeDisplay(display, parent.display?.value.orEmpty())
    val newClaims = mergeClaims(claims, parent.claims.orEmpty())
    return ResolvedTypeMetadata(
        vct = vct,
        name = newName,
        description = newDescription,
        display = newDisplay,
        claims = newClaims,
    )
}

/**
 * Merges the [ResolvedTypeMetadata] of a [Vct] with those of its [parent].
 *
 * The resulting [ResolvedTypeMetadata] has:
 * 1. vct: the vct of this instance
 * 2. name: the name of this instance, or the name of parent in case this has no name
 * 3. description: the description of this instance, or the description of parent in case this has no description
 * 4. display: the display of this instance in case it overrides the display of its parent,
 * otherwise the display of its parent, fallbacks an emtpy list
 * 5. claims: the claims of this instance and the claims of parent not already present in this.
 *
 * @param parent the Type Metadata of a parent Vct
 */
private operator fun ResolvedTypeMetadata.plus(parent: SdJwtVcTypeMetadata): ResolvedTypeMetadata =
    mergeWith(
        parent = parent,
        mergeDisplay = { thisDisplays, parentDisplays -> thisDisplays.ifEmpty { parentDisplays } },
        mergeClaims = { thisClaims, parentClaims ->
            thisClaims.mergeWith(parentClaims, ClaimMetadata::path) { thisClaim, parentClaim ->
                if (parentClaim.mandatoryOrDefault)
                    check(thisClaim.mandatoryOrDefault) {
                        "The mandatory property of claim ${thisClaim.path} cannot be overridden"
                    }

                if (parentClaim.selectivelyDisclosableOrDefault in setOf(
                        ClaimSelectivelyDisclosable.Always,
                        ClaimSelectivelyDisclosable.Never,
                    )
                ) {
                    check(parentClaim.selectivelyDisclosableOrDefault == thisClaim.selectivelyDisclosableOrDefault) {
                        "Selectively disclosable property of claim ${thisClaim.path} cannot be overridden"
                    }
                }

                thisClaim
            }
        },
    )

private fun <T, K> Iterable<T>.mergeWith(other: Iterable<T>, extractKey: (T) -> K, mergeValues: (T, T) -> T): List<T> {
    val thisValuesByKey = associateBy { extractKey(it) }
    val otherValuesByKey = other.associateBy { extractKey(it) }
    val allKeys = thisValuesByKey.keys + otherValuesByKey.keys
    return allKeys.map { key ->
        val thisValue = thisValuesByKey[key]
        val otherValue = otherValuesByKey[key]

        when {
            thisValue != null && otherValue != null -> mergeValues(thisValue, otherValue)
            thisValue != null && otherValue == null -> thisValue
            thisValue == null && otherValue != null -> otherValue
            else -> error("cannot find value for $key")
        }
    }
}
