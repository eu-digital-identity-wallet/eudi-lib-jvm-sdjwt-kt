/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.sdjwt.dsl.values

import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.def.DisclosableDef
import eu.europa.ec.eudi.sdjwt.dsl.def.SdJwtDefinition
import eu.europa.ec.eudi.sdjwt.dsl.def.SdJwtElementDefinition
import eu.europa.ec.eudi.sdjwt.runCatchingCancellable
import kotlinx.serialization.json.*

/**
 * A builder that given a [sdJwtDefinition] can
 * transform a JSON Object representing the raw data of the credential
 * into a [SdJwtObject], that can be processed further to produce an SD-JWT-VC.
 *
 * Actually, this builder uses the [sdJwtDefinition] as a template
 */
class DefinitionBasedSdJwtObjectBuilder(private val sdJwtDefinition: SdJwtDefinition) {

    /**
     * Builds the [SdJwtObject] that adheres to the [sdJwtDefinition] given the [data]
     *
     * @param data The raw data of the credential. It is expected that adheres to the [sdJwtDefinition]
     * @return the [SdJwtObject] of the SD-JWT-VC credential and a list of warnings
     */
    fun build(data: JsonObject): Pair<SdJwtObject, List<String>> {
        val definitionToUse = sdJwtDefinition.plusSdJwtVcNeverSelectivelyDisclosableClaims()
        val dataToUse = data - SdJwtVcSpec.VCT
        val warnings = mutableListOf<String>()
        val sdJwtObject =
            buildSdJwtObject {
                claim(SdJwtVcSpec.VCT, definitionToUse.metadata.vct.value)
                addAll(definitionToUse.content, dataToUse, warnings)
            }
        return sdJwtObject to warnings.toList()
    }

    private fun SdJwtObjectBuilder.addAll(
        definitionContent: Map<String, SdJwtElementDefinition>,
        dataObject: Map<String, JsonElement>,
        warnings: MutableList<String>,
    ) {
        definitionContent.forEach { (claimName, elementDef) ->
            // Determine if the element is present in the data
            dataObject[claimName]?.let { dataValue ->
                val isSd = elementDef is Disclosable.AlwaysSelectively

                if (JsonNull == dataValue) {
                    // null provided for claim
                    if (isSd) sdClaim(claimName, dataValue)
                    else claim(claimName, dataValue)
                    return@forEach
                }

                when (elementDef.value) {
                    is DisclosableDef.Id -> {
                        // Primitive claim
                        if (isSd) sdClaim(claimName, dataValue)
                        else claim(claimName, dataValue)
                    }

                    is DisclosableDef.Obj -> {
                        // Nested object
                        if (dataValue !is JsonObject) {
                            warnings.add("Type mismatch: data is not an object but definition expects one for '$claimName'")
                            return@forEach
                        }

                        val nestedDef = elementDef.value as DisclosableDef.Obj // Cast needed for content
                        if (isSd) {
                            sdObjClaim(claimName) { // Pass minDigests if available
                                addAll(nestedDef.value.content, dataValue, warnings)
                            }
                        } else {
                            objClaim(claimName) { // Pass minDigests if available
                                addAll(nestedDef.value.content, dataValue, warnings)
                            }
                        }
                    }

                    is DisclosableDef.Arr -> {
                        // Nested array
                        if (dataValue !is JsonArray) {
                            // Type mismatch: data is not an array but definition expects one
                            warnings.add("Type mismatch: data is not an array but definition expects one for '$claimName'")
                            return@forEach
                        }

                        val nestedDef = elementDef.value as DisclosableDef.Arr
                        if (isSd) {
                            sdArrClaim(claimName) { // Pass minDigests if available
                                addElements(nestedDef.value.content, dataValue, warnings)
                            }
                        } else {
                            arrClaim(claimName) { // Pass minDigests if available
                                addElements(nestedDef.value.content, dataValue, warnings)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun SdJwtArrayBuilder.addElements(
        elementDefinition: SdJwtElementDefinition,
        dataArray: JsonArray,
        warnings: MutableList<String>,
    ) {
        val isSd = elementDefinition is Disclosable.AlwaysSelectively

        dataArray.forEach { arrayElement ->
            if (JsonNull == arrayElement) {
                // null provided for claim
                if (isSd) sdClaim(arrayElement)
                else claim(arrayElement)
                return@forEach
            }

            when (elementDefinition.value) {
                is DisclosableDef.Id -> {
                    // Primitive array element
                    if (isSd) sdClaim(arrayElement)
                    else claim(arrayElement)
                }

                is DisclosableDef.Obj -> {
                    // Nested object within array
                    if (arrayElement !is JsonObject) {
                        warnings.add("Type mismatch: data is not an object but definition expects one for '$arrayElement'")
                        return@forEach
                    }

                    val nestedObjDef = elementDefinition.value as DisclosableDef.Obj
                    if (isSd) {
                        sdObjClaim {
                            addAll(nestedObjDef.value.content, arrayElement, warnings)
                        }
                    } else {
                        objClaim {
                            addAll(nestedObjDef.value.content, arrayElement, warnings)
                        }
                    }
                }

                is DisclosableDef.Arr -> {
                    // Nested array within array (rare, but possible)
                    if (arrayElement !is JsonArray) {
                        warnings.add("Type mismatch: data is not an array but definition expects one for '$arrayElement'")
                        return@forEach
                    }

                    val nestedArrDef = elementDefinition.value as DisclosableDef.Arr
                    if (isSd) {
                        sdArrClaim {
                            addElements(nestedArrDef.value.content, arrayElement, warnings)
                        }
                    } else {
                        arrClaim {
                            addElements(nestedArrDef.value.content, arrayElement, warnings)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds an [SdJwtObject], for a SD-JWT-VC,  using [sdJwtDefinition] as a template applied on the data of the credential
 *
 * @param strict if true, the function fails in case where the raw data do not adhere to the given [sdJwtDefinition]
 * @param action provides the raw data of the credential. There is no need to provide the `vst` claim. It will be
 * added automatically from [sdJwtDefinition]
 *
 * @return the [SdJwtObject] of the SD-JWT-VC credential
 */
fun sdJwtVc(
    sdJwtDefinition: SdJwtDefinition,
    strict: Boolean = true,
    action: JsonObjectBuilder.() -> Unit,
): Result<SdJwtObject> = runCatchingCancellable {
    val data = buildJsonObject(action)
    val builder = DefinitionBasedSdJwtObjectBuilder(sdJwtDefinition)
    val (sdJwtObject, warnings) = builder.build(data)
    if (strict && warnings.isNotEmpty()) {
        error("Errors : " + warnings.joinToString(", "))
    } else {
        sdJwtObject
    }
}
