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

import eu.europa.ec.eudi.sdjwt.FirstNotNullOfOrNull
import eu.europa.ec.eudi.sdjwt.map
import io.ktor.http.*

/**
 * Lookup the Type Metadata of a VCT.
 */
fun interface LookupTypeMetadata : suspend (Vct) -> Result<SdJwtVcTypeMetadata?> {

    companion object {

        operator fun invoke(
            lookup: suspend (Vct) -> SdJwtVcTypeMetadata?,
        ): LookupTypeMetadata = LookupTypeMetadata { vct -> runCatching { lookup(vct) } }

        fun firstNotNullOfOrNull(first: LookupTypeMetadata, vararg remaining: LookupTypeMetadata): LookupTypeMetadata =
            LookupTypeMetadata(FirstNotNullOfOrNull(listOf(first, *remaining).map { f -> f.map { it.getOrNull() } }))
    }
}

/**
 * Lookup the Type Metadata of an HTTPS URL VCT using Ktor.
 */
class LookupTypeMetadataUsingKtor(
    private val httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
) : LookupTypeMetadata {
    override suspend fun invoke(vct: Vct): Result<SdJwtVcTypeMetadata?> = runCatching {
        val url = Url(vct.value)
        require(URLProtocol.HTTPS == url.protocol) { "$vct is not an https url" }
        httpClientFactory().use { httpClient -> httpClient.getJsonOrNull(url) }
    }
}

/**
 * Lookup a [JsonSchema] from a Uri.
 */
fun interface LookupJsonSchema : suspend (String) -> Result<JsonSchema?> {

    companion object {
        operator fun invoke(
            lookup: suspend (String) -> JsonSchema?,
        ): LookupJsonSchema = LookupJsonSchema { runCatching { lookup(it) } }

        fun firstNotNullOfOrNull(first: LookupJsonSchema, vararg remaining: LookupJsonSchema): LookupJsonSchema =
            LookupJsonSchema(FirstNotNullOfOrNull(listOf(first, *remaining).map { f -> f.map { it.getOrNull() } }))
    }
}

/**
 * Lookup a [JsonSchema] from a Uri that is a Url using Ktor.
 */
class LookupJsonSchemaUsingKtor(
    private val httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
) : LookupJsonSchema {

    override suspend fun invoke(uri: String): Result<JsonSchema?> =
        runCatching {
            httpClientFactory().use { it.getJsonOrNull(Url(uri)) }
        }
}

data class ResolvedTypeMetadata(
    val vct: Vct,
    val name: String?,
    val description: String?,
    val display: List<DisplayMetadata>,
    val claims: List<ClaimMetadata>,
    val schemas: List<JsonSchema>,
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
        schemas = emptyList(),
    )

/**
 * Resolver for [ResolvedTypeMetadata].
 */
interface ResolveTypeMetadata {

    val lookupTypeMetadata: LookupTypeMetadata
    val lookupJsonSchema: LookupJsonSchema

    /**
     * Resolves the [ResolvedTypeMetadata] for [vct].
     */
    suspend operator fun invoke(vct: Vct): Result<ResolvedTypeMetadata> =
        runCatching {
            tailrec suspend fun resolve(vct: Vct, accumulator: ResolvedTypeMetadata, resolved: Set<Vct>): ResolvedTypeMetadata {
                require(vct !in resolved) { "cyclical reference detected, vct $vct has been previously resolved" }
                val current = run {
                    val current = lookupTypeMetadata(vct).getOrThrow() ?: error("unable to lookup Type Metadata for $vct")
                    val schema = current.schemaUri?.let { schemaUri ->
                        lookupJsonSchema(schemaUri).getOrThrow() ?: error("unable to lookup JsonSchema for $schemaUri")
                    } ?: current.schema
                    current.copy(schema = schema, schemaUri = null)
                }
                val updatedAccumulator = accumulator + current
                val parent = current.extends?.let { Vct(it) }
                return if (null != parent) {
                    resolve(parent, updatedAccumulator, resolved + vct)
                } else {
                    updatedAccumulator
                }
            }

            resolve(vct, ResolvedTypeMetadata.empty(vct), emptySet())
        }

    companion object {
        operator fun invoke(lookupTypeMetadata: LookupTypeMetadata, lookupJsonSchema: LookupJsonSchema): ResolveTypeMetadata =
            object : ResolveTypeMetadata {
                override val lookupTypeMetadata: LookupTypeMetadata = lookupTypeMetadata
                override val lookupJsonSchema: LookupJsonSchema = lookupJsonSchema
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
 * 6. schemas: the schemas of this instance and the schema of parent if one is present
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
    val newSchemas = schemas + listOfNotNull(parent.schema)
    return ResolvedTypeMetadata(
        vct = vct,
        name = newName,
        description = newDescription,
        display = newDisplay,
        claims = newClaims,
        schemas = newSchemas,
    )
}

/**
 * Merges the [ResolvedTypeMetadata] of a [Vct] with those of its [parent].
 *
 * The resulting [ResolvedTypeMetadata] has:
 * 1. vct: the vct of this instance
 * 2. name: the name of this instance, or the name of parent in case this has no name
 * 3. description: the description of this instance, or the description of parent in case this has no description
 * 4. display: the display of this instance and the display of its parent for the language tags that are not present in this
 * 5. claims: the claims of this instance and the claims of parent not already present in this.
 *   for claims present both in this and parent, their display are merged keeping display of this and display of the parent for
 *   languages not defined by this
 * 6. schemas: the schemas of this instance and the schema of parent if one is present
 *
 * @param parent the Type Metadata of a parent Vct
 */
private operator fun ResolvedTypeMetadata.plus(parent: SdJwtVcTypeMetadata): ResolvedTypeMetadata =
    mergeWith(
        parent = parent,
        mergeDisplay = { thisDisplays, parentDisplays ->
            thisDisplays.mergeWith(parentDisplays, DisplayMetadata::lang) { thisDisplay, _ -> thisDisplay }
        },
        mergeClaims = { thisClaims, parentClaims ->
            thisClaims.mergeWith(parentClaims, ClaimMetadata::path, ClaimMetadata::mergeWith)
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

private fun ClaimMetadata.mergeWith(parent: ClaimMetadata): ClaimMetadata {
    require(path == parent.path)
    return ClaimMetadata(
        path = path,
        display = display.orEmpty().mergeWith(parent.display.orEmpty(), ClaimDisplay::lang) { current, _ -> current },
        selectivelyDisclosable = selectivelyDisclosable,
        svgId = svgId,
    )
}
