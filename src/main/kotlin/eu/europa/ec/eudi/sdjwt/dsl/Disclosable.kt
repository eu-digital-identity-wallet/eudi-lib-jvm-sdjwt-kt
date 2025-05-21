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

import kotlin.contracts.contract

/**
 * Embellishment of a value [A] in regard to selectively disclosing it.
 *
 * The possible states are:
 * - [NeverSelectively] means that the value is never disclosed selectively. That is, value is always disclosed.
 * - [AlwaysSelectively] means that the value is always disclosed selectively. That is, it is disclosed
 *
 * @param [A] the type for which the embellishment is defined.
 */
sealed interface Disclosable<out A> {
    val value: A

    @JvmInline
    value class NeverSelectively<out A>(override val value: A) : Disclosable<A>

    @JvmInline
    value class AlwaysSelectively<out A>(override val value: A) : Disclosable<A>
}

/**
 * Lifts a value [A] to a [Disclosable.NeverSelectively] context.
 */
inline operator fun <reified A> A.not(): Disclosable.NeverSelectively<A> = Disclosable.NeverSelectively(this)

/**
 * Lists a value [A] to a [Disclosable.AlwaysSelectively] context.
 */
inline operator fun <reified A> A.unaryPlus(): Disclosable.AlwaysSelectively<A> = Disclosable.AlwaysSelectively(this)

inline fun <A, B> Disclosable<A>.map(f: (A) -> B): Disclosable<B> {
    contract {
        callsInPlace(f, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return when (this) {
        is Disclosable.NeverSelectively<A> -> Disclosable.NeverSelectively(f(value))
        is Disclosable.AlwaysSelectively<A> -> Disclosable.AlwaysSelectively(f(value))
    }
}

typealias DisclosableElement<K, A> = Disclosable<DisclosableValue<K, A>>

interface DisclosableObject<K, out A> {
    val content: Map<K, DisclosableElement<K, A>>
}

interface DisclosableArray<K, out A> {
    val content: List<DisclosableElement<K, A>>
}

interface DisclosableContainerFactory<K, A> {
    fun obj(elements: Map<K, DisclosableElement<K, A>>): DisclosableObject<K, A>
    fun arr(elements: List<DisclosableElement<K, A>>): DisclosableArray<K, A>

    companion object {
        fun <K, A>default(): DisclosableContainerFactory<K, A> = object : DisclosableContainerFactory<K, A> {
            override fun obj(elements: Map<K, DisclosableElement<K, A>>): DisclosableObject<K, A> = object : DisclosableObject<K, A> {
                override val content: Map<K, DisclosableElement<K, A>>
                    get() = elements
            }

            override fun arr(elements: List<DisclosableElement<K, A>>): DisclosableArray<K, A> = object : DisclosableArray<K, A> {
                override val content: List<DisclosableElement<K, A>>
                    get() = elements
            }
        }
    }
}

/**
 * [A] a type that expressed a value.
 * [K] a type for the keys of an [Obj] container
 */
sealed interface DisclosableValue<K, out A> {

    /**
     * Lifts a value [A] to a [DisclosableValue.Id] context.
     * This means that [A] doesn't contain any information that can be selectively disclosed
     */
    @JvmInline
    value class Id<K, out A>(
        val value: A,
    ) : DisclosableValue<K, A>

    /**
     * A map-like or dictionary-like container of [DisclosableElement]s.
     * @property value the underlying [DisclosableObject]
     *
     * [K] represents the keys of the object
     * @see DisclosableObject for more details about the container and its contents.
     */
    @JvmInline
    value class Obj<K, out A>(
        val value: DisclosableObject<K, A>,
    ) : DisclosableValue<K, A>

    /**
     * An array-like container of [DisclosableElement]s.
     * @property value the underlying [DisclosableArray]
     * @see DisclosableArray for more details about the container and its contents.
     */
    @JvmInline
    value class Arr<K, out A>(
        val value: DisclosableArray<K, A>,
    ) : DisclosableValue<K, A>

    companion object {
        operator fun <K, A> invoke(value: A): Id<K, A> = Id(value)

        fun <K, A> obj(value: DisclosableObject<K, A>): Obj<K, A> = Obj(value)

        fun <K, A> arr(value: DisclosableArray<K, A>): Arr<K, A> = Arr(value)
    }
}

fun <K, A, B> DisclosableElement<K, A>.mapValue(
    factory: DisclosableContainerFactory<K, B> = DisclosableContainerFactory.default(),
    f: (A) -> B,
): DisclosableElement<K, B> {
    val mapDisclosableElement = factory.mapDisclosableElement<K, A, B>()
    return mapDisclosableElement(this to f)
}

fun <K, A, B> DisclosableObject<K, A>.mapElements(
    factory: DisclosableContainerFactory<K, B> = DisclosableContainerFactory.default(),
    f: (A) -> B,
): DisclosableObject<K, B> {
    val mapObjValues = factory.mapObjValues<K, A, B>()
    return mapObjValues(this to f)
}

fun <K, A, B> DisclosableArray<K, A>.mapElements(
    factory: DisclosableContainerFactory<K, B> = DisclosableContainerFactory.default(),
    f: (A) -> B,
): DisclosableArray<K, B> {
    val mapArrValues = factory.mapArrValues<K, A, B>()
    return mapArrValues(this to f)
}

fun <K, A, B> DisclosableValue<K, A>.map(
    factory: DisclosableContainerFactory<K, B> = DisclosableContainerFactory.default(),
    f: (A) -> B,
): DisclosableValue<K, B> {
    val mapDisclosableValue = factory.mapDisclosableValue<K, A, B>()
    return mapDisclosableValue.invoke(this to f)
}

@Suppress("standard:max-line-length")
private fun<K, A, B> DisclosableContainerFactory<K, B>.mapDisclosableElement(): DeepRecursiveFunction<
    Pair<DisclosableElement<K, A>, (A) -> B>,
    DisclosableElement<K, B>,
    > =
    DeepRecursiveFunction { (de, f) ->
        val mapDisclosableValue = mapDisclosableValue<K, A, B>()
        de.map { v ->
            mapDisclosableValue.callRecursive(v to f)
        }
    }
private fun<K, A, B> DisclosableContainerFactory<K, B>.mapObjValues(): DeepRecursiveFunction<
    Pair<DisclosableObject<K, A>, (A) -> B>,
    DisclosableObject<K, B>,
    > =
    DeepRecursiveFunction { (obj, f) ->
        val mapDisclosableElement = mapDisclosableElement<K, A, B>()
        val elements = obj.content.mapValues { (_, de) ->
            mapDisclosableElement.callRecursive(de to f)
        }
        obj(elements)
    }

@Suppress("standard:max-line-length")
private fun<K, A, B> DisclosableContainerFactory<K, B>.mapArrValues(): DeepRecursiveFunction<
    Pair<DisclosableArray<K, A>, (A) -> B>,
    DisclosableArray<K, B>,
    > =
    DeepRecursiveFunction { (arr, f) ->
        val mapDisclosableElement = mapDisclosableElement<K, A, B>()
        val elements = arr.content.map { de ->
            mapDisclosableElement.callRecursive(de to f)
        }
        arr(elements)
    }

@Suppress("standard:max-line-length")
internal fun <K, A, B> DisclosableContainerFactory<K, B>.mapDisclosableValue(): DeepRecursiveFunction<
    Pair<DisclosableValue<K, A>, (A) -> B>,
    DisclosableValue<K, B>,
    > =
    DeepRecursiveFunction { (dv, f) ->
        when (dv) {
            is DisclosableValue.Id<K, A> -> {
                val mappedValue = f(dv.value)
                DisclosableValue.Id(mappedValue)
            }
            is DisclosableValue.Obj<K, A> -> {
                val mapObjValues = mapObjValues<K, A, B>()
                val mappedObj = mapObjValues.callRecursive(dv.value to f)
                DisclosableValue.Obj(mappedObj)
            }
            is DisclosableValue.Arr<K, A> -> {
                val mapArrValues = mapArrValues<K, A, B>()
                val mappedArr = mapArrValues.callRecursive(dv.value to f)
                DisclosableValue.Arr(mappedArr)
            }
        }
    }
