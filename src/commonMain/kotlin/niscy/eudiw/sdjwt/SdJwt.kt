package niscy.eudiw.sdjwt


import kotlinx.serialization.json.JsonObject


interface JwtEncoder {
    fun encode(key: String, algorithm: JwtAlgorithm, jwtClaimSet: JsonObject): Result<Jwt>
}

object SdJwtOps {

    private val jwtEncoder: JwtEncoder by lazy { jwtOps() }
    private const val disclosureSeparator = "~"


    fun flatDiscloseAndEncode(
        key: String,
        algorithm: JwtAlgorithm,
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        jwtClaims: JsonObject = JsonObject(emptyMap()),
        claimToBeDisclosed: Pair<String, JsonObject>
    ):Result<CombinedIssuanceSdJwt> = runCatching {


        val (ds, updateJwtClaims) = DislosureOps.flatDisclose(
            hashAlgorithm,
            saltProvider,
            jwtClaims,
            claimToBeDisclosed
        ).getOrThrow()

        val jwt = jwtEncoder.encode(key, algorithm, updateJwtClaims).getOrThrow()

        jwt + encodeDisclosures(ds)
    }

    fun structureDiscloseAndEncode(
        key: String,
        algorithm: JwtAlgorithm,
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
        saltProvider: SaltProvider = SaltProvider.Default,
        jwtClaims: JsonObject = JsonObject(emptyMap()),
        claimsToBeDisclosed: JsonObject
    ): Result<CombinedIssuanceSdJwt> = runCatching {

        val (ds, updateJwtClaims) = DislosureOps.structureDisclose(
            hashAlgorithm,
            saltProvider,
            jwtClaims,
            claimsToBeDisclosed
        ).getOrThrow()

        val jwt = jwtEncoder.encode(key, algorithm, updateJwtClaims).getOrThrow()

        jwt + encodeDisclosures(ds)
    }

    fun encodeDisclosures(ds: Iterable<Disclosure>): String =
        ds.fold("") { acc, d -> "$acc$disclosureSeparator${d.value}" }

}