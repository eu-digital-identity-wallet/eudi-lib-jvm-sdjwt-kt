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
package eu.europa.ec.eudi.sdjwt.dsl

import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.values.sdJwt
import eu.europa.ec.eudi.sdjwt.vc.ClaimPath

enum class JsonType {
    STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL
}

val structure = buildDisclosableObject {
    claim("iss", JsonType.STRING)
    claim("iat", JsonType.NUMBER)
    claim("exp", JsonType.NUMBER)
    claim("sub", JsonType.STRING)

    sdObjClaim("address") {
        sdClaim("street_address", JsonType.STRING)
        sdClaim("locality", JsonType.STRING)
        sdClaim("region", JsonType.STRING)
        sdClaim("country", JsonType.STRING)
    }
    sdArrClaim("nationalities") {
        sdClaim(JsonType.STRING)
    }
}

typealias NamespaceAndName = Pair<String, String>
enum class CborType { TSTR, TNUM, TBOOL, TMAP, TARRAY, TNULL }
typealias MDocStructure = DisclosableObject<NamespaceAndName, CborType>

class MDocStructureBuilder {
    private val claims = DisclosableObjectSpecBuilder<NamespaceAndName, CborType>()

    fun sdClaim(namespace: String, name: String, cborType: CborType) =
        claims.claim(namespace to name, cborType)

    fun build(): MDocStructure =
        with(claims) { factory.obj(elements) }
}

fun describeMdoc(builder: MDocStructureBuilder.() -> Unit = {}): MDocStructure =
    MDocStructureBuilder().apply(builder).build()

val mdl = describeMdoc {
    sdClaim("org.iso.18013.5.1", "family_name", CborType.TSTR)
    sdClaim("org.iso.18013.5.1", "given_name", CborType.TSTR)
    sdClaim("org.iso.18013.5.1", "birthdate", CborType.TSTR)
    sdClaim("org.iso.18013.5.1", "driving_privileges", CborType.TMAP)
}
val sdJwtSpec = sdJwt {
    claim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
    claim("iss", "https://example.com/issuer")
    claim("iat", 1516239022)
    claim("exp", 1735689661)
    sdObjClaim("address") {
        sdClaim("street_address", "Schulstr. 12")
        sdClaim("locality", "Schulpforta")
        sdClaim("region", "Sachsen-Anhalt")
        sdClaim("country", "DE")
    }
}

fun main() {
    println("\n\n\nmdl Calculating disclosure map for a structure MDOC")
    mdl.calculateSelectiveDisclosureMap(
        claimPathOf = { (n: String, a: String): NamespaceAndName -> ClaimPath.claim(n).claim(a) },
        { it },
    ).forEach { (k, v) -> println("$k: $v") }

    println("\n\n\nsdjwtvc Calculating disclosure map for a structure SD-JWT-VC")
    structure.calculateSelectiveDisclosureMap(
        claimPathOf = { ClaimPath.claim(it) },
        { it },
    ).forEach { (k, v) -> println("$k: $v") }

    println("\n\n\nsdjwtvc data Calculating disclosure map for a data SD-JWT-VC")
    sdJwtSpec.calculateSelectiveDisclosureMap(
        claimPathOf = { ClaimPath.claim(it) },
        { it },
    ).forEach { (k, v) -> println("$k: $v") }
}
