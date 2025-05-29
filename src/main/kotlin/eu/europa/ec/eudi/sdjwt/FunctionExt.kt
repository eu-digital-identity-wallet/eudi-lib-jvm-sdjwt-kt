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

/**
 * Converts [this] to a function from [A] to [C] given a mapping function from [B] to [C].
 */
inline fun <A, B, C> ((A) -> B).map(
    crossinline f: (B) -> C,
): (A) -> C = {
    f(this(it))
}

/**
 * Converts [this] to a function from [C] to [B] given a mapping function from [A] to [C].
 */
inline fun <A, B, C> ((A) -> B).contraMap(
    crossinline f: (C) -> A,
): (C) -> B = {
    this(f(it))
}

/**
 * Converts [this] to a suspending function from [A] to [C] given a mapping function from [B] to [C].
 */
inline fun <A, B, C> (suspend (A) -> B).map(
    crossinline f: (B) -> C,
): suspend (A) -> C = {
    f(this(it))
}

/**
 * Converts [this] to a suspending function from [C] to [B] given a mapping function from [A] to [C].
 */
inline fun <A, B, C> (suspend (A) -> B).contraMap(
    crossinline f: (C) -> A,
): suspend (C) -> B = {
    this(f(it))
}
