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

import kotlin.random.Random

/**
 * Generates decoy [HashedDisclosure]
 */
fun interface DecoyGen {

    fun gen(hashingAlgorithm: HashAlgorithm): HashedDisclosure

    fun gen(hashingAlgorithm: HashAlgorithm, numOfDecoys: Int): Collection<HashedDisclosure> {
        return (1..numOfDecoys).map { gen(hashingAlgorithm) }
    }

    companion object {
        val Default: DecoyGen by lazy {
            DecoyGen { hashingAlgorithm ->
                val saltProvider = SaltProvider.randomSaltProvider(Random.nextInt(12, 24))
                val random = saltProvider.salt()
                val value = HashedDisclosure.base64UrlEncodedDigestOf(hashingAlgorithm, random)
                HashedDisclosure.wrap(value).getOrThrow()
            }
        }
    }
}
