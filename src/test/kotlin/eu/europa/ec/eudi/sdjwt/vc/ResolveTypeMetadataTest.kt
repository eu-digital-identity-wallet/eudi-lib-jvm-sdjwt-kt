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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResolveTypeMetadataTest {
    companion object {
        val childVct = Vct("urn:test:child")
        val parentVct = Vct("urn:test:parent")
        val claimPath = ClaimPath.claim("testClaim")
    }

    private fun resolver(typeMetadata: Map<Vct, SdJwtVcTypeMetadata>): ResolveTypeMetadata =
        ResolveTypeMetadata(
            lookupTypeMetadata = { vct, _ ->
                Result.success(typeMetadata[vct])
            },
        )

    @Test
    fun `Resolve succeed when child and parent has the same mandatory value`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    mandatory = true,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    mandatory = true,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val result = typeMetadataResolver(childVct, expectedIntegrity = null)
        val resolved = result.getOrNull()
        val claims = assertNotNull(resolved).claims
        assertEquals(1, claims.size)
        assertEquals(claimPath, claims.first().path)
        assertTrue(claims.first().mandatoryOrDefault)
    }

    @Test
    fun `Resolve succeed when child has mandatory field but parent doesn't`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    mandatory = true,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val result = typeMetadataResolver(childVct, expectedIntegrity = null)
        val resolved = result.getOrNull()
        val claims = assertNotNull(resolved).claims
        assertEquals(1, claims.size)
        assertEquals(claimPath, claims.first().path)
        assertTrue(claims.first().mandatoryOrDefault)
    }

    @Test
    fun `Resolve fails when child overrides mandatory field of parent`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    mandatory = true,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    mandatory = false,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val error = assertFailsWith<IllegalStateException> {
            typeMetadataResolver(childVct, expectedIntegrity = null).getOrThrow()
        }
        assertContains("The mandatory property of claim $claimPath cannot be overridden", error.message!!)
    }

    @Test
    fun `Resolve succeed when child has Sd Always as parent`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Always,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Always,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val result = typeMetadataResolver(childVct, expectedIntegrity = null)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.claims?.size)
        assertEquals(ClaimSelectivelyDisclosable.Always, result.getOrNull()?.claims?.first()?.selectivelyDisclosable)
    }

    @Test
    fun `Resolve succeed when child has Sd Always and parent has Allowed`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Allowed,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Always,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val result = typeMetadataResolver(childVct, expectedIntegrity = null)
        val resolved = result.getOrNull()
        val claims = assertNotNull(resolved).claims
        assertEquals(1, claims.size)
        assertEquals(claimPath, claims.first().path)
        assertEquals(ClaimSelectivelyDisclosable.Always, claims.first().selectivelyDisclosableOrDefault)
    }

    @Test
    fun `Resolve succeed when child has Sd Always and parent doesn't specify`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Always,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val result = typeMetadataResolver(childVct, expectedIntegrity = null)
        val resolved = result.getOrNull()
        val claims = assertNotNull(resolved).claims
        assertEquals(1, claims.size)
        assertEquals(claimPath, claims.first().path)
        assertEquals(ClaimSelectivelyDisclosable.Always, claims.first().selectivelyDisclosableOrDefault)
    }

    @Test
    fun `Resolve fails when child overrides Sd Always of parent`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Always,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Allowed,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val error = assertFailsWith<IllegalStateException> {
            typeMetadataResolver(childVct, expectedIntegrity = null).getOrThrow()
        }
        assertContains("Selectively disclosable property of claim $claimPath cannot be overridden", error.message!!)
    }

    @Test
    fun `Resolve fails when child overrides Sd never of parent`() = runTest {
        val parent = SdJwtVcTypeMetadata(
            vct = parentVct,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Never,
                ),
            ),
        )
        val child = SdJwtVcTypeMetadata(
            vct = childVct,
            extends = parent.vct.value,
            claims = listOf(
                ClaimMetadata(
                    path = claimPath,
                    selectivelyDisclosable = ClaimSelectivelyDisclosable.Allowed,
                ),
            ),
        )

        val typeMetadataResolver = resolver(mapOf(childVct to child, parentVct to parent))

        val error = assertFailsWith<IllegalStateException> {
            typeMetadataResolver(childVct, expectedIntegrity = null).getOrThrow()
        }
        assertContains("Selectively disclosable property of claim $claimPath cannot be overridden", error.message!!)
    }
}
