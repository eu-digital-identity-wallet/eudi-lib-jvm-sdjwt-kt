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
package eu.europa.ec.eudi.sdjwt.dsl.def

import eu.europa.ec.eudi.sdjwt.dsl.Disclosable

/**
 * Represents the **definition (schema)** of a map-like structure for selectively disclosable data.
 *
 * This interface defines the expected claims and their nested structures
 * at the schema level, focusing on how data types and disclosure properties are defined.
 *
 * @param K The type of the keys used in the object
 * @param A The type of **metadata** associated with individual claims or their definition.
 * This allows attaching additional schema-level information (e.g., display hints, validation rules).
 */
interface DisclosableDefObject<K, out A> {
    val content: Map<K, DisclosableElementDefinition<K, A>>
}

/**
 * Represents the **definition (schema)** of a array-like structure for selectively disclosable data.
 *
 * This interface defines the expected structure and disclosure properties for elements within an array.
 * It is assumed that the array is homogeneous with respect to its element. That is, all elements
 * share the same [disclosure property][Disclosable] and the same [shape][DisclosableDef]
 *
 * @param K The type of the keys used in the array (typically unused for array elements, but kept for consistency).
 * @param A The type of **metadata** associated with individual array elements or their definition.
 * This allows attaching additional schema-level information (e.g., display hints, validation rules).
 */
interface DisclosableDefArray<K, out A> {
    val content: DisclosableElementDefinition<K, A>
}

typealias DisclosableElementDefinition<K, A> = Disclosable<DisclosableDef<K, A>>

/**
 * A sealed interface representing the **type** of a defined element within a selectively disclosable schema.
 *
 * This distinguishes between primitive values, nested objects, nested arrays,
 * or a set of alternative definitions that a data element could conform to.
 *
 * @param K The type of keys used in nested objects
 * @param A The type of **metadata** associated with the element's definition.
 */
sealed interface DisclosableDef<K, out A> {

    /**
     * Represents a **primitive value** in the schema definition.
     *
     * This indicates that the element is expected to have no further disclosure properties
     * (e.g., string, number, boolean, objects, arrays).
     * [A] typically represents metadata about this primitive type,
     * such as format or display hints.
     *
     * @param value The metadata associated with this primitive definition.
     */
    @JvmInline
    value class Id<K, out A>(
        val value: A,
    ) : DisclosableDef<K, A>

    /**
     * Represents a **nested object** in the schema definition.
     *
     * This indicates that the element is expected to be a JSON object,
     * defined by its corresponding [DisclosableDefObject].
     *
     * @param value The [DisclosableDefObject] that defines the structure and properties of this nested object.
     */
    @JvmInline
    value class Obj<K, out A>(
        val value: DisclosableDefObject<K, A>,
    ) : DisclosableDef<K, A>

    /**
     * Represents a **nested array** in the schema definition.
     *
     * This indicates that the element is expected to be an array,
     * defined by its corresponding [DisclosableDefArray].
     *
     * @param value The [DisclosableDefArray] that defines the structure and elements of this nested array.
     */
    @JvmInline
    value class Arr<K, out A>(
        val value: DisclosableDefArray<K, A>,
    ) : DisclosableDef<K, A>
}
