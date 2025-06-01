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
package eu.europa.ec.eudi.sdjwt.dsl.sdjwt

import eu.europa.ec.eudi.sdjwt.dsl.*
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

/**
 * Gets a flat set of the claim paths
 * representing the "shape" of a [DisclosableObject] contents.
 * Assumes that DisclosableObject use a [String] key.
 */
fun <A> DisclosableObject<String, A>.claimPaths(): Set<ClaimPath> =
    fold(
        objectHandlers = ObjectHandlers(),
        arrayHandlers = ArrayHandlers(),
        initial = Folded(
            path = emptyList(),
            metadata = Unit,
            result = emptySet(),
        ),
        combine = { acc, current -> Folded(acc.path, Unit, acc.result + current.result) },
        arrayResultWrapper = { elementResults -> elementResults.flatten().toSet() },
        arrayMetadataCombiner = { },
    ).result

private class ObjectHandlers<A> : ClaimPathAwareObjectFoldHandlers<A, Unit, Set<ClaimPath>>() {
    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(path)

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = foldedArray.metadata to setOf(path) + foldedArray.result

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = foldedObject.metadata to setOf(path) + foldedObject.result
}

private class ArrayHandlers<A>() : ClaimPathAwareArrayFoldHandlers<A, Unit, Set<ClaimPath>>() {

    override fun ifId(
        path: ClaimPath,
        id: Disclosable<DisclosableValue.Id<String, A>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent()))

    override fun ifArray(
        path: ClaimPath,
        array: Disclosable<DisclosableValue.Arr<String, A>>,
        foldedArray: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent())) + foldedArray.result

    override fun ifObject(
        path: ClaimPath,
        obj: Disclosable<DisclosableValue.Obj<String, A>>,
        foldedObject: Folded<String, Unit, Set<ClaimPath>>,
    ): Pair<Unit, Set<ClaimPath>> = Unit to setOf(checkNotNull(path.parent())) + foldedObject.result
}
