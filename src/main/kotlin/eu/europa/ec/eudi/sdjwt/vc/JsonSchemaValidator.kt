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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
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
    data class Invalid(val errors: Map<Int, List<JsonSchemaViolation>>) : JsonSchemaValidationResult {
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
     * Validates [unvalidated] against [schema].
     */
    suspend fun validate(unvalidated: JsonObject, schema: JsonSchema): List<JsonSchemaViolation>

    /**
     * Validates [unvalidated] against the non-empty list of [schemas].
     *
     * @throws IllegalArgumentException if [schemas] is empty
     */
    suspend fun validate(unvalidated: JsonObject, schemas: List<JsonSchema>): JsonSchemaValidationResult = coroutineScope {
        require(schemas.isNotEmpty()) { "schemas must not be empty" }
        val results = schemas.withIndex()
            .map { (index, schema) ->
                val violations = validate(unvalidated, schema)
                val result = if (violations.isNotEmpty()) {
                    JsonSchemaValidationResult.Invalid(mapOf(index to violations))
                } else {
                    JsonSchemaValidationResult.Valid
                }
                yield()
                result
            }
        results.fold(JsonSchemaValidationResult.Valid, JsonSchemaValidationResult::plus)
    }
}

private operator fun JsonSchemaValidationResult.plus(other: JsonSchemaValidationResult): JsonSchemaValidationResult {
    val allErrors = buildMap {
        if (this@plus is JsonSchemaValidationResult.Invalid) putAll(errors)
        if (other is JsonSchemaValidationResult.Invalid) putAll(other.errors)
    }

    return if (allErrors.isNotEmpty()) JsonSchemaValidationResult.Invalid(allErrors)
    else JsonSchemaValidationResult.Valid
}
