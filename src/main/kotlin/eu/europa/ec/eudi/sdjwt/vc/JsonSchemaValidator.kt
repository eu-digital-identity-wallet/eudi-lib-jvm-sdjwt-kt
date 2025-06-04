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
package eu.europa.ec.eudi.sdjwt.vc

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * A Json Schema violation.
 */
data class JsonSchemaViolation(val description: String, val cause: Throwable? = null) {
    constructor(description: String) : this(description, null)

    init {
        require(description.isNotBlank()) { "description must not be blank" }
    }
}

/**
 * Json Schema validation result.
 */
sealed interface JsonSchemaValidationResult {

    /**
     * Validation succeeded
     */
    data object Valid : JsonSchemaValidationResult

    /**
     * Validation failed.
     *
     * @property errors the violations that were detected
     */
    data class Invalid(val errors: List<JsonSchemaViolation>) : JsonSchemaValidationResult {
        constructor(first: JsonSchemaViolation, vararg rest: JsonSchemaViolation) : this(listOf(first, *rest))

        init {
            require(errors.isNotEmpty()) { "errors must not be empty" }
        }
    }
}

/**
 * Validates a Json payload against a Json Schema.
 */
fun interface JsonSchemaValidator {

    /**
     * Validates [payload] against [schema].
     */
    suspend fun validate(payload: JsonObject, schema: JsonSchema): JsonSchemaValidationResult

    /**
     * Validates [payload] against the non-empty list of [schemas].
     *
     * @throws IllegalArgumentException if [schemas] is empty
     */
    suspend fun validate(payload: JsonObject, schemas: List<JsonSchema>): JsonSchemaValidationResult = coroutineScope {
        require(schemas.isNotEmpty()) { "schemas must not be empty" }
        schemas.map { async { validate(payload, it) } }
            .awaitAll()
            .fold(JsonSchemaValidationResult.Valid, JsonSchemaValidationResult::plus)
    }
}

private operator fun JsonSchemaValidationResult.plus(other: JsonSchemaValidationResult): JsonSchemaValidationResult {
    val allErrors = buildList {
        if (this@plus is JsonSchemaValidationResult.Invalid) addAll(errors)
        if (other is JsonSchemaValidationResult.Invalid) addAll(other.errors)
    }

    return if (allErrors.isNotEmpty()) JsonSchemaValidationResult.Invalid(allErrors)
    else JsonSchemaValidationResult.Valid
}
