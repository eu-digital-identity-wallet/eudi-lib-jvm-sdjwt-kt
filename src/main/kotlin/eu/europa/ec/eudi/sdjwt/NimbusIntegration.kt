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

import com.nimbusds.jose.jwk.JWK
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.ParseException
import com.nimbusds.jose.JOSEException as NimbusJOSEException
import com.nimbusds.jose.JWSAlgorithm as NimbusJWSAlgorithm
import com.nimbusds.jose.JWSHeader as NimbusJWSHeader
import com.nimbusds.jose.JWSSigner as NimbusJWSSigner
import com.nimbusds.jose.JWSVerifier as NimbusJWSVerifier
import com.nimbusds.jwt.JWT as NimbusJWT
import com.nimbusds.jwt.JWTClaimsSet as NimbusJWTClaimsSet
import com.nimbusds.jwt.SignedJWT as NimbusSignedJWT
import com.nimbusds.jwt.proc.JWTProcessor as NimbusJWTProcessor

//
// Signature Verification support
//

val JwtSignatureVerifier.Companion.NoSignatureValidation: JwtSignatureVerifier by lazy {
    JwtSignatureVerifier { unverifiedJwt ->
        try {
            val parsedJwt = NimbusSignedJWT.parse(unverifiedJwt)
            parsedJwt.jwtClaimsSet.asClaims()
        } catch (e: ParseException) {
            null
        }
    }
}

/**
 * Indicates that  presentation SD-JWT must have key binding. Νο signature validation is performed
 */
val KeyBindingVerifier.Companion.MustBePresent: KeyBindingVerifier.MustBePresentAndValid by lazy {
    KeyBindingVerifier.MustBePresentAndValid { JwtSignatureVerifier.NoSignatureValidation }
}

fun NimbusJWSVerifier.asJwtVerifier(): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    try {
        val signedJwt = NimbusSignedJWT.parse(unverifiedJwt)
        if (!signedJwt.verify(this)) null
        else signedJwt.jwtClaimsSet.asClaims()
    } catch (e: ParseException) {
        null
    } catch (e: NimbusJOSEException) {
        null
    }
}

fun NimbusJWTProcessor<*>.asJwtVerifier(): JwtSignatureVerifier = JwtSignatureVerifier { unverifiedJwt ->
    process(unverifiedJwt, null).asClaims()
}

//
// JSON Support
//

fun NimbusJWTClaimsSet.asClaims(): Claims =
    toPayload().toBytes().run {
        val s: String = this.decodeToString()
        Json.parseToJsonElement(s).jsonObject
    }

private fun JWK.asJsonObject(): JsonObject = Json.parseToJsonElement(toJSONString()).jsonObject

//
// DSL additions
//

/**
 * Adds the confirmation claim (cnf) as a plain (always disclosable) which
 * contains the [jwk]
 *
 * @param jwk the key to put in confirmation claim
 */
fun SdObjectBuilder.cnf(jwk: JWK) = cnf(jwk.asJsonObject())

/**
 * A variation of [sdJwt] which produces signed SD-JWT
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 *
 * @return signed SD-JWT
 *
 * @see SdJwtIssuer.Companion.nimbus which in addition allows customization of JWS Header
 */
inline fun signedSdJwt(
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    builderAction: SdObjectBuilder.() -> Unit,
): SdJwt.Issuance<NimbusSignedJWT> {
    val issuer = SdJwtIssuer.nimbus(sdJwtFactory, signer, signAlgorithm)
    val sdJwtElements = sdJwt(builderAction)
    return issuer.issue(sdJwtElements).getOrThrow()
}

/**
 * Factory method for creating a [SdJwtIssuer] that uses Nimbus
 *
 * @param sdJwtFactory factory for creating the unsigned SD-JWT
 * @param signer the signer that will sign the SD-JWT
 * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
 * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
 *
 * @return [SdJwtIssuer] that uses Nimbus
 *
 * @see SdJwtFactory.Default
 */
fun SdJwtIssuer.Companion.nimbus(
    sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
    signer: NimbusJWSSigner,
    signAlgorithm: NimbusJWSAlgorithm,
    jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
): SdJwtIssuer<NimbusSignedJWT> =
    NimbusSdJwtIssuerFactory.createIssuer(sdJwtFactory, signer, signAlgorithm, jwsHeaderCustomization)

private object NimbusSdJwtIssuerFactory {

    private const val allowSymmetric = true

    /**
     * Factory method for creating a [SdJwtIssuer] that uses Nimbus
     *
     * @param sdJwtFactory factory for creating the unsigned SD-JWT
     * @param signer the signer that will sign the SD-JWT
     * @param signAlgorithm It MUST use a JWS asymmetric digital signature algorithm.
     * @param jwsHeaderCustomization optional customization of JWS header using [NimbusJWSHeader.Builder]
     *
     * @return [SdJwtIssuer] that uses Nimbus
     *
     * @see SdJwtFactory.Default
     */
    fun createIssuer(
        sdJwtFactory: SdJwtFactory = SdJwtFactory.Default,
        signer: NimbusJWSSigner,
        signAlgorithm: NimbusJWSAlgorithm,
        jwsHeaderCustomization: NimbusJWSHeader.Builder.() -> Unit = {},
    ): SdJwtIssuer<NimbusSignedJWT> = SdJwtIssuer(sdJwtFactory) { unsignedSdJwt ->

        val (claims, disclosures) = unsignedSdJwt
        require(allowSymmetric || signAlgorithm.isAsymmetric()) { "Only asymmetric algorithm can be used" }
        val header = with(NimbusJWSHeader.Builder(signAlgorithm)) {
            jwsHeaderCustomization()
            build()
        }
        val jwt = NimbusSignedJWT(header, NimbusJWTClaimsSet.parse(claims.toString())).apply { sign(signer) }
        SdJwt.Issuance(jwt, disclosures)
    }

    /**
     * Indicates whether an [NimbusJWSAlgorithm] is asymmetric
     * @receiver the algorithm to check
     * @return true if algorithm is asymmetric.
     */
    private fun NimbusJWSAlgorithm.isAsymmetric(): Boolean = NimbusJWSAlgorithm.Family.SIGNATURE.contains(this)
}

/**
 * Serializes a [SdJwt] into either Combined Issuance or Combined Presentation format
 * depending on the case
 *
 * @param JWT the type representing the JWT part of the SD-JWT
 * @param HB_JWT the type representing the Holder Binding part of the SD
 * @receiver the SD-JWT to be serialized
 * @return the SD-JWT in either  Combined Issuance or Combined Presentation format depending on the case
 */
fun <JWT : NimbusJWT, HB_JWT : NimbusJWT> SdJwt<JWT, HB_JWT>.serialize(): String = when (this) {
    is SdJwt.Issuance<JWT> -> toCombinedIssuanceFormat(NimbusJWT::serialize)
    is SdJwt.Presentation<JWT, HB_JWT> -> toCombinedPresentationFormat(NimbusJWT::serialize, NimbusJWT::serialize)
}
