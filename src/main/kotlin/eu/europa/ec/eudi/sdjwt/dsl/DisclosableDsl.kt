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

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
internal annotation class DisclosableElementDsl

@DisclosableElementDsl
class DisclosableArraySpecBuilder<K, P>(
    val factory: DisclosableContainerFactory<K, P>,
    val elements: MutableList<DisclosableElement<K, P>>,
) {

    private fun addValue(
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: P,
    ) {
        val dv = DisclosableValue.Id<K, P>(element)
        elements.add(option(dv))
    }

    private fun addObj(
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: DisclosableObject<K, P>,
    ) {
        val dv = DisclosableValue.Obj(element)
        elements.add(option(dv))
    }

    fun addArr(
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: DisclosableArray<K, P>,
    ) {
        val dv = DisclosableValue.Arr(element)
        elements.add(option(dv))
    }

    fun claim(value: P): Unit = addValue({ !it }, value)

    fun sdClaim(value: P): Unit = addValue({ +it }, value)

    fun objClaim(
        element: DisclosableObject<K, P>,
    ): Unit = addObj({ !it }, element)

    fun objClaim(
        action: DisclosableObjectSpecBuilder<K, P>.() -> Unit,
    ): Unit = objClaim(buildDisclosableObject(factory, action))

    fun sdObjClaim(
        element: DisclosableObject<K, P>,
    ): Unit = addObj({ !it }, element)

    fun sdObjClaim(
        action: DisclosableObjectSpecBuilder<K, P>.() -> Unit,
    ): Unit = sdObjClaim(buildDisclosableObject(factory, action))

    fun arrClaim(
        element: DisclosableArray<K, P>,
    ): Unit = addArr({ !it }, element)

    fun arrClaim(
        action: DisclosableArraySpecBuilder<K, P>.() -> Unit,
    ): Unit = arrClaim(buildDisclosableArray(factory, action))

    fun sdArrClaim(
        element: DisclosableArray<K, P>,
    ) = addArr({ +it }, element)

    fun sdArrClaim(
        action: DisclosableArraySpecBuilder<K, P>.() -> Unit,
    ): Unit = sdArrClaim(buildDisclosableArray(factory, action))
}

fun <K, P> buildDisclosableArray(
    factory: DisclosableContainerFactory<K, P>,
    builderAction: DisclosableArraySpecBuilder<K, P>.() -> Unit,
): DisclosableArray<K, P> {
    val builder = DisclosableArraySpecBuilder(factory, mutableListOf())
    val content = builder.apply(builderAction)
    return factory.arr(content.elements)
}

@DisclosableElementDsl
class DisclosableObjectSpecBuilder<K, P>(
    val factory: DisclosableContainerFactory<K, P>,
    val elements: MutableMap<K, DisclosableElement<K, P>>,
) {

    private fun addValue(
        name: K,
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: P,
    ) {
        val dv = DisclosableValue.Id<K, P>(element)
        elements.put(name, option(dv))
    }

    private fun addObj(
        name: K,
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: DisclosableObject<K, P>,
    ) {
        val dv = DisclosableValue.obj(element)
        elements.put(name, option(dv))
    }

    fun addArr(
        name: K,
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: DisclosableArray<K, P>,

    ) {
        val dv = DisclosableValue.Arr(element)
        elements.put(name, option(dv))
    }

    fun claim(name: K, element: P): Unit = addValue(name, { !it }, element)
    fun sdClaim(name: K, element: P): Unit = addValue(name, { +it }, element)

    fun objClaim(
        name: K,
        element: DisclosableObject<K, P>,
    ): Unit =
        addObj(name, { !it }, element)
    fun objClaim(
        name: K,
        action: (DisclosableObjectSpecBuilder<K, P>).() -> Unit,
    ): Unit =
        objClaim(name, buildDisclosableObject(factory, action))

    fun sdObjClaim(
        name: K,
        element: DisclosableObject<K, P>,
    ): Unit =
        addObj(name, { +it }, element)

    fun sdObjClaim(
        name: K,
        action: (DisclosableObjectSpecBuilder<K, P>).() -> Unit,
    ): Unit =
        sdObjClaim(name, buildDisclosableObject(factory, action))

    fun arrClaim(name: K, element: DisclosableArray<K, P>): Unit =
        addArr(name, { !it }, element)

    fun arrClaim(name: K, action: DisclosableArraySpecBuilder<K, P>.() -> Unit): Unit =
        arrClaim(name, buildDisclosableArray(factory, action))

    fun sdArrClaim(
        name: K,
        element: DisclosableArray<K, P>,
    ): Unit =
        addArr(name, { +it }, element)

    fun sdArrClaim(
        name: K,
        action: DisclosableArraySpecBuilder<K, P>.() -> Unit,
    ): Unit =
        sdArrClaim(name, buildDisclosableArray(factory, action))
}

fun <K, P> buildDisclosableObject(
    factory: DisclosableContainerFactory<K, P>,
    builderAction: DisclosableObjectSpecBuilder<K, P>.() -> Unit,
): DisclosableObject<K, P> {
    val content = DisclosableObjectSpecBuilder(factory, mutableMapOf()).apply(builderAction)
    return factory.obj(content.elements)
}
