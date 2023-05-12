package niscy.eudiw.sdjwt

import com.nimbusds.jose.util.JSONObjectUtils as MimbusJSONObjectUtils
import kotlinx.serialization.json.JsonObject
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSObject as NimbusJWSObject
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.Payload as NimbusPayload
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimSet



private fun JsonObject.asBytes(): ByteArray = toString().encodeToByteArray()

fun flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: JsonObject = JsonObject(emptyMap()),
    claimToBeDisclosed: Pair<String, JsonObject>
): Result<CombinedIssuanceSdJwt> = runCatching {

    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
        hashAlgorithm,
        saltProvider,
        jwtClaims,
        claimToBeDisclosed
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }

    jwt + disclosures.concat()
}

fun flatDiscloseAndEncode(
    signer: NimbusJWSSigner,
    algorithm: NimbusJWSAlgorithm,
    hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_256,
    saltProvider: SaltProvider = SaltProvider.Default,
    jwtClaims: NimbusJWTClaimSet?,
    claimToBeDisclosed: Pair<String, Map<String, Any>>
): Result<CombinedIssuanceSdJwt> = runCatching {

    val (disclosures, jwtClaimSet) = DisclosureOps.flatDisclose(
        hashAlgorithm = hashAlgorithm,
        saltProvider = saltProvider,
        target = jwtClaims?.toString(),
        claimToBeDisclosed = claimToBeDisclosed.first to claimToBeDisclosed.second.let { MimbusJSONObjectUtils.toJSONString(it) }
    ).getOrThrow()

    val header = NimbusJWSHeader(algorithm)
    val payload = NimbusPayload(jwtClaimSet.asBytes())

    val jwt: Jwt = with(NimbusJWSObject(header, payload)) {
        sign(signer)
        serialize()
    }

    jwt + disclosures.concat()
}


