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
