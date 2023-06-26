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

    /**
     * Given a [hashingAlgorithm] method produces a decoy [HashedDisclosure]
     * @param hashingAlgorithm the algorithm to be used for producing the decoy
     * @return a decoy [HashedDisclosure]
     */
    fun gen(hashingAlgorithm: HashAlgorithm): HashedDisclosure

    /**
     * Produces a series of decoy [HashedDisclosure]
     * @param hashingAlgorithm the algorithm to be used for producing the decoy
     * @param numOfDecoys the number of decoys to produce
     * @return a series of decoy [HashedDisclosure]
     */
    fun gen(hashingAlgorithm: HashAlgorithm, numOfDecoys: Int): Set<HashedDisclosure> {
        return if (numOfDecoys < 1) emptySet()
        else (1..numOfDecoys).map { gen(hashingAlgorithm) }.toSet()
    }

    /**
     * Produces a set of decoy [HashedDisclosure]
     * The number of decoys is random up to [numOfDecoysLimit]
     *
     * @param hashingAlgorithm the algorithm to be used for producing the decoy
     * @param numOfDecoysLimit the upper limit of the decoys to generate
     * @return a series of decoy [HashedDisclosure]
     */
    fun genUpTo(hashingAlgorithm: HashAlgorithm, numOfDecoysLimit: Int): Set<HashedDisclosure> =
        gen(hashingAlgorithm, Random.nextInt(1, numOfDecoysLimit))

    companion object {
        /**
         * Default implementation of [DecoyGen] which produces random decoy [HashedDisclosure]
         */
        val Default: DecoyGen by lazy {
            DecoyGen { hashingAlgorithm ->
                val saltProvider = SaltProvider.randomSaltProvider(Random.nextInt(12, 24))
                val random = saltProvider.salt()
                HashedDisclosure.create(hashingAlgorithm, random).getOrThrow()
            }
        }
    }
}
