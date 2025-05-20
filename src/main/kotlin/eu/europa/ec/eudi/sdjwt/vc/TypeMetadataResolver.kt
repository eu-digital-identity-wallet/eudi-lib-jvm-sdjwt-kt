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

/**
 * Resolver for SD-JWT VC Type Metadata.
 */
fun interface TypeMetadataResolver {

    /**
     * Resolves the Type Metadata of the provided [vct], verifies its [integrity], and merges it the Type Metadata of its parents.
     */
    suspend operator fun invoke(vct: Vct, integrity: DocumentIntegrity?): Result<Any>
}

/**
 * Type Metadata resolution mechanism.
 */
fun interface TypeMetadataResolutionMechanism {

    /**
     * Resolves the [SdJwtVcTypeMetadata] of the provided [vct], and checks its [integrity].
     */
    suspend operator fun invoke(vct: Vct, integrity: DocumentIntegrity?): Result<SdJwtVcTypeMetadata>
}

/**
 * Default implementation for [TypeMetadataResolver] using a provided [resolutionMechanism].
 */
class DefaultTypeMetadataResolver(
    private val resolutionMechanism: TypeMetadataResolutionMechanism,
) : TypeMetadataResolver {

    override suspend fun invoke(vct: Vct, integrity: DocumentIntegrity?): Result<Any> =
        runCatching {
            TODO("Not yet implemented")
        }

    private suspend fun resolve(vct: Vct, integrity: DocumentIntegrity?): Map<Vct, SdJwtVcTypeMetadata> {
        tailrec suspend fun doResolve(
            vct: Vct,
            integrity: DocumentIntegrity?,
            accumulator: Map<Vct, SdJwtVcTypeMetadata>,
        ): Map<Vct, SdJwtVcTypeMetadata> {
            require(vct !in accumulator) { "cyclic reference detected" }

            val current = resolutionMechanism.invoke(vct, integrity).getOrThrow()
            val updatedAccumulator = accumulator + (vct to current)

            val parent = current.extends
            val parentIntegrity = current.extendsIntegrity

            return if (parent != null) {
                doResolve(parent, parentIntegrity, updatedAccumulator)
            } else {
                updatedAccumulator
            }
        }

        return doResolve(vct, integrity, emptyMap())
    }
}
