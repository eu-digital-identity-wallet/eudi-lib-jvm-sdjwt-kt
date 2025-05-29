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
 * Suspend function that returns the result of the first suspend function in [functions] that returns a non-null value.
 */
class FirstNotNullOfOrNull<A : Any, B : Any>(
    private val functions: Iterable<suspend (A) -> B?>,
) : suspend (A) -> B? {
    override suspend fun invoke(value: A): B? = functions.firstNotNullOfOrNull { it(value) }

    companion object {
        operator fun <A : Any, B : Any> invoke(
            first: suspend (A) -> B?,
            vararg remaining: suspend (A) -> B?,
        ): FirstNotNullOfOrNull<A, B> = FirstNotNullOfOrNull(listOf(first, *remaining))
    }
}
