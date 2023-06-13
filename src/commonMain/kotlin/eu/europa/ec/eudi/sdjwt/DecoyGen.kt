package eu.europa.ec.eudi.sdjwt

import kotlin.random.Random

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