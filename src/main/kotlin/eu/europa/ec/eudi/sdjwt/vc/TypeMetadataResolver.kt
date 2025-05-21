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

data class TypeMetadata(
    val vct: Vct,
    val name: String? = null,
    val description: String? = null,
    val display: List<DisplayMetadata> = emptyList(),
    val claims: List<ClaimMetadata> = emptyList(),
    val schemas: List<JsonSchema> = emptyList(),
)

/**
 * Resolver for [TypeMetadata].
 */
interface TypeMetadataResolver {

    /**
     * Resolves the [TypeMetadata] for [vct].
     */
    suspend fun resolve(vct: Vct): Result<TypeMetadata>
}

/**
 * Default implementation for [TypeMetadataResolver].
 * Relies on a list of configured [TypeMetadataFetcher] and [JsonSchemaFetcher] that are registered in order of precedence.
 */
class DefaultTypeMetadataResolver(
    private val typeMetadataFetchers: List<TypeMetadataFetcher>,
    private val jsonSchemaFetchers: List<JsonSchemaFetcher>,
) : TypeMetadataResolver {
    init {
        require(typeMetadataFetchers.isNotEmpty()) { "no typeMetadataFetchers configured" }
        require(jsonSchemaFetchers.isNotEmpty()) { "no jsonSchemaFetchers configured" }
    }

    override suspend fun resolve(vct: Vct): Result<TypeMetadata> = runCatching {
        tailrec suspend fun resolve(vct: Vct, accumulator: TypeMetadata, resolved: Set<Vct>): TypeMetadata {
            require(vct !in resolved) { "cyclical reference detected, vct $vct has been previously resolved" }
            val current = fetchTypeMetadata(vct)
                .let {
                    when {
                        it.schemaUri != null -> {
                            val schema = fetchJsonSchema(it.schemaUri)
                            it.copy(schema = schema, schemaUri = null)
                        }

                        else -> it
                    }
                }
            val updatedAccumulator = accumulator + current
            val parent = current.extends?.let { Vct(it) }
            return if (parent != null) {
                resolve(parent, updatedAccumulator, resolved + vct)
            } else {
                updatedAccumulator
            }
        }

        resolve(vct, TypeMetadata(vct = vct), emptySet())
    }

    private suspend fun fetchTypeMetadata(vct: Vct): SdJwtVcTypeMetadata =
        typeMetadataFetchers.firstNotNullOfOrNull { fetcher -> fetcher.fetch(vct).getOrNull() }
            ?: error("Unable to fetch Type Metadata for $vct using registered TypeMetadataFetchers")

    private suspend fun fetchJsonSchema(uri: String): JsonSchema =
        jsonSchemaFetchers.firstNotNullOfOrNull { it.fetch(uri).getOrNull() }
            ?: error("Unable to fetch JsonSchema for $uri using registered JsonSchemaFetchers")
}

/**
 * Merges the [TypeMetadata] of a [Vct] with those of its [parent].
 *
 * The resulting [TypeMetadata] has:
 * 1. vct: the vct of this instance
 * 2. name: the name of this instance, or the name of parent in case this has no name
 * 3. description: the description of this instance, or the description of parent in case this has no description
 * 4. display: the display of this instance and the display of its parent for the language tags that are not present in this
 * 5. claims: the claims of this instance and the claims of parent not already present in this.
 *   for claims present both in this and parent, their display are merged keeping display of this and display of the parent for
 *   languages not defined by this
 * 6. schemas: the schemas of this instance and the schema of parent if one is present
 */
private operator fun TypeMetadata.plus(parent: SdJwtVcTypeMetadata): TypeMetadata {
    val newName = name ?: parent.name
    val newDescription = description ?: parent.description
    val newDisplay = merge(display, parent.display?.value.orEmpty(), DisplayMetadata::lang) { current, _ -> current }
    val newClaims = merge(claims, parent.claims.orEmpty(), ClaimMetadata::path, ClaimMetadata::mergeWith)
    val newSchemas = schemas + listOfNotNull(parent.schema)
    return TypeMetadata(
        vct = vct,
        name = newName,
        description = newDescription,
        display = newDisplay,
        claims = newClaims,
        schemas = newSchemas,
    )
}

private fun <T, K> merge(currentValues: List<T>, parentValues: List<T>, extractKey: (T) -> K, mergeValues: (T, T) -> T): List<T> {
    val currentValuesByKey = currentValues.associateBy { extractKey(it) }
    val parentValuesByKey = parentValues.associateBy { extractKey(it) }
    val allKeys = currentValuesByKey.keys + parentValuesByKey.keys
    return allKeys.map { key ->
        val currentValue = currentValuesByKey[key]
        val parentValue = parentValuesByKey[key]

        when {
            currentValue != null && parentValue != null -> mergeValues(currentValue, parentValue)
            currentValue != null && parentValue == null -> currentValue
            currentValue == null && parentValue != null -> parentValue
            else -> error("cannot find value for $key")
        }
    }
}

private fun ClaimMetadata.mergeWith(parent: ClaimMetadata): ClaimMetadata {
    require(path == parent.path)
    return ClaimMetadata(
        path = path,
        display = merge(display.orEmpty(), parent.display.orEmpty(), ClaimDisplay::lang) { current, _ -> current },
        selectivelyDisclosable = selectivelyDisclosable,
        svgId = svgId,
    )
}
