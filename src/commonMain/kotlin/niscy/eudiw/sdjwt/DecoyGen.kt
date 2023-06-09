package niscy.eudiw.sdjwt

import kotlin.random.Random

fun interface DecoyGen {

    fun gen(hashingAlgorithm: HashAlgorithm): HashedDisclosure

    fun gen(hashingAlgorithm: HashAlgorithm, numOfDecoys: Int): Collection<HashedDisclosure> {
        return (1..numOfDecoys).map { gen(hashingAlgorithm) }
    }

    companion object {
        val Default: DecoyGen by lazy {
            DecoyGen { hashingAlgorithm ->

                fun base64UrlEncodedDigestOf(random: String): String {
                    val hashFunction = hashing().of(hashingAlgorithm)
                    val digest = hashFunction(random.encodeToByteArray())
                    return JwtBase64.encodeString(digest)
                }

                val saltProvider = SaltProvider.randomSaltProvider(Random.nextInt(12, 24))
                val random = saltProvider.salt()
                val value = base64UrlEncodedDigestOf(random)
                HashedDisclosure.wrap(value).getOrThrow()
            }
        }
    }
}