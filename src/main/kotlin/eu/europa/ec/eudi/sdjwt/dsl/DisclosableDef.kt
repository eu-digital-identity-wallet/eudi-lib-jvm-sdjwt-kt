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
package eu.europa.ec.eudi.sdjwt.dsl

interface DisclosableDefObject<K, out A> {
    val content: Map<K, DisclosableElementDefinition<K, A>>
}

interface DisclosableDefArray<K, out A> {
    val content: DisclosableElementDefinition<K, A>
}

typealias DisclosableElementDefinition<K, A> = Disclosable<DisclosableDef<K, A>>

sealed interface DisclosableDef<K, out A> {

    @JvmInline
    value class Id<K, out A>(
        val value: A,
    ) : DisclosableDef<K, A>

    @JvmInline
    value class Obj<K, out A>(
        val value: DisclosableDefObject<K, A>,
    ) : DisclosableDef<K, A>

    @JvmInline
    value class Arr<K, out A>(
        val value: DisclosableDefArray<K, A>,
    ) : DisclosableDef<K, A>

    @JvmInline
    value class Alt<K, out A> (
        val value: Set<DisclosableElementDefinition<K, A>>,
    ) : DisclosableDef<K, A> {
        init {
            require(value.size >= 2) { "At least 2 values must be provided" }
        }
    }
}
