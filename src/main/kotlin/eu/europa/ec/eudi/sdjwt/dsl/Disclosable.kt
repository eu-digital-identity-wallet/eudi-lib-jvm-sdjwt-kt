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

import kotlin.contracts.contract

/**
 * Embellishment of a value [A] in regard to selectively disclosing it.
 *
 * The possible states are:
 * - [NeverSelectively] means that the value is never disclosed selectively. That is, value is always disclosed.
 * - [AlwaysSelectively] means that the value is always disclosed selectively. That is, it is disclosed
 *
 * @param [A] the type for which the embellishment is defined.
 */
sealed interface Disclosable<out A> {
    val value: A

    @JvmInline
    value class NeverSelectively<out A>(override val value: A) : Disclosable<A>

    @JvmInline
    value class AlwaysSelectively<out A>(override val value: A) : Disclosable<A>
}

/**
 * Lifts a value [A] to a [Disclosable.NeverSelectively] context.
 */
inline operator fun <reified A> A.not(): Disclosable.NeverSelectively<A> = Disclosable.NeverSelectively(this)

/**
 * Lists a value [A] to a [Disclosable.AlwaysSelectively] context.
 */
inline operator fun <reified A> A.unaryPlus(): Disclosable.AlwaysSelectively<A> = Disclosable.AlwaysSelectively(this)

inline fun <A, B> Disclosable<A>.map(f: (A) -> B): Disclosable<B> {
    contract {
        callsInPlace(f, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return when (this) {
        is Disclosable.NeverSelectively<A> -> Disclosable.NeverSelectively(f(value))
        is Disclosable.AlwaysSelectively<A> -> Disclosable.AlwaysSelectively(f(value))
    }
}
