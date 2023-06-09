package niscy.eudiw.sdjwt

import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSObject as NimbusJWSObject
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.Payload as NimbusPayload
import com.nimbusds.jose.util.JSONObjectUtils as MimbusJSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimSet


private fun JsonObject.asBytes(): ByteArray = toString().encodeToByteArray()

/**
 * @param signer the signer that will be used to sign the SD-JWT
 * @param algorithm
*  @param hashAlgorithm the algorithm to be used for hashing disclosures
 * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
 * @param jwtClaims
 * @param claimToBeDisclosed
 */
fun flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: JsonObject = JsonObject(emptyMap()),
    claimToBeDisclosed: Claim,
    numOfDecoys: Int
): Result<CombinedIssuanceSdJwt> = runCatching {

    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
        hashAlgorithm,
        saltProvider,
        jwtClaims,
        claimToBeDisclosed,
        numOfDecoys
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }

    jwt + disclosures.concat()
}

/**
 * @param hashAlgorithm the algorithm to be used for hashing disclosures
 * @param saltProvider provides [Salt] for the creation of [disclosures][Disclosure]
 */
fun flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: NimbusJWTClaimSet?,
    claimToBeDisclosed: Pair<String, Map<String, Any>>,
    numOfDecoys: Int
): Result<CombinedIssuanceSdJwt> = runCatching {

    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
        hashAlgorithm = hashAlgorithm,
        saltProvider = saltProvider,
        otherJwtClaims = jwtClaims?.toString(),
        claimToBeDisclosed = claimToBeDisclosed.first to claimToBeDisclosed.second.let {
            MimbusJSONObjectUtils.toJSONString(it)
        },
        numOfDecoys = numOfDecoys
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }

    jwt + disclosures.concat()
}


