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
class DisclosableArraySpecBuilder<K, A, DObj, DArr>(
    val factory: DisclosableContainerFactory<K, A, DObj, DArr>,
    val elements: MutableList<DisclosableElement<K, A>>,
) where DObj : DisclosableObject<K, A>, DArr : DisclosableArray<K, A> {

    private fun addValue(
        option: (DisclosableValue<K, A>) -> DisclosableElement<K, A>,
        element: A,
    ) {
        val dv = DisclosableValue.Id<K, A>(element)
        elements.add(option(dv))
    }

    private fun addObj(
        option: (DisclosableValue<K, A>) -> DisclosableElement<K, A>,
        element: DObj,
    ) {
        val dv = DisclosableValue.Obj(element)
        elements.add(option(dv))
    }

    private fun addArr(
        option: (DisclosableValue<K, A>) -> DisclosableElement<K, A>,
        element: DArr,
    ) {
        val dv = DisclosableValue.Arr(element)
        elements.add(option(dv))
    }

    fun claim(value: A): Unit = addValue({ !it }, value)

    fun sdClaim(value: A): Unit = addValue({ +it }, value)

    fun objClaim(element: DObj): Unit = addObj({ !it }, element)

    fun objClaim(
        action: DisclosableObjectSpecBuilder<K, A, DObj, DArr>.() -> Unit,
    ): Unit = objClaim(buildDisclosableObject(factory, action))

    fun sdObjClaim(element: DObj): Unit = addObj({ +it }, element)

    fun sdObjClaim(
        action: DisclosableObjectSpecBuilder<K, A, DObj, DArr>.() -> Unit,
    ): Unit = sdObjClaim(buildDisclosableObject(factory, action))

    fun arrClaim(element: DArr): Unit = addArr({ !it }, element)

    fun arrClaim(
        action: DisclosableArraySpecBuilder<K, A, DObj, DArr>.() -> Unit,
    ): Unit = arrClaim(buildDisclosableArray(factory, action))

    fun sdArrClaim(element: DArr) = addArr({ +it }, element)

    fun sdArrClaim(
        action: DisclosableArraySpecBuilder<K, A, DObj, DArr>.() -> Unit,
    ): Unit = sdArrClaim(buildDisclosableArray(factory, action))
}

fun <K, A, DObj, DArr> buildDisclosableArray(
    factory: DisclosableContainerFactory<K, A, DObj, DArr>,
    builderAction: DisclosableArraySpecBuilder<K, A, DObj, DArr>.() -> Unit,
): DArr
    where DObj : DisclosableObject<K, A>, DArr : DisclosableArray<K, A> {
    val builder = DisclosableArraySpecBuilder(factory, mutableListOf())
    val content = builder.apply(builderAction)
    return factory.arr(content.elements)
}

fun <K, P> buildDisclosableArray(
    builderAction: DisclosableArraySpecBuilder<K, P, DisclosableObject<K, P>, DisclosableArray<K, P>>.() -> Unit,
) = buildDisclosableArray(DisclosableContainerFactory.default(), builderAction)

@DisclosableElementDsl
class DisclosableObjectSpecBuilder<K, P, DObj, DArr>(
    val factory: DisclosableContainerFactory<K, P, DObj, DArr>,
    val elements: MutableMap<K, DisclosableElement<K, P>> = mutableMapOf(),
) where DObj : DisclosableObject<K, P>, DArr : DisclosableArray<K, P> {

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
        element: DObj,
    ) {
        val dv = DisclosableValue.obj(element)
        elements.put(name, option(dv))
    }

    private fun addArr(
        name: K,
        option: (DisclosableValue<K, P>) -> DisclosableElement<K, P>,
        element: DArr,

    ) {
        val dv = DisclosableValue.Arr(element)
        elements.put(name, option(dv))
    }

    fun claim(name: K, element: P): Unit = addValue(name, { !it }, element)
    fun sdClaim(name: K, element: P): Unit = addValue(name, { +it }, element)

    fun objClaim(
        name: K,
        element: DObj,
    ): Unit =
        addObj(name, { !it }, element)

    fun objClaim(
        name: K,
        action: (DisclosableObjectSpecBuilder<K, P, DObj, DArr>).() -> Unit,
    ): Unit =
        objClaim(name, buildDisclosableObject(factory, action))

    fun sdObjClaim(
        name: K,
        element: DObj,
    ): Unit =
        addObj(name, { +it }, element)

    fun sdObjClaim(
        name: K,
        action: (DisclosableObjectSpecBuilder<K, P, DObj, DArr>).() -> Unit,
    ): Unit =
        sdObjClaim(name, buildDisclosableObject(factory, action))

    fun arrClaim(name: K, element: DArr): Unit =
        addArr(name, { !it }, element)

    fun arrClaim(name: K, action: DisclosableArraySpecBuilder<K, P, DObj, DArr>.() -> Unit): Unit =
        arrClaim(name, buildDisclosableArray(factory, action))

    fun sdArrClaim(
        name: K,
        element: DArr,
    ): Unit =
        addArr(name, { +it }, element)

    fun sdArrClaim(
        name: K,
        action: DisclosableArraySpecBuilder<K, P, DObj, DArr>.() -> Unit,
    ): Unit =
        sdArrClaim(name, buildDisclosableArray(factory, action))

    companion object {
        operator fun <K, P>invoke(
            elements: MutableMap<K, DisclosableElement<K, P>> = mutableMapOf(),
        ): DisclosableObjectSpecBuilder<K, P, DisclosableObject<K, P>, DisclosableArray<K, P>> =
            DisclosableObjectSpecBuilder(DisclosableContainerFactory.default(), elements)
    }
}

fun <K, P, DObj, DArr> buildDisclosableObject(
    factory: DisclosableContainerFactory<K, P, DObj, DArr>,
    action: DisclosableObjectSpecBuilder<K, P, DObj, DArr>.() -> Unit,
): DObj
    where DObj : DisclosableObject<K, P>, DArr : DisclosableArray<K, P> {
    val content = DisclosableObjectSpecBuilder(factory, mutableMapOf()).apply(action)
    return factory.obj(content.elements)
}

fun <K, P> buildDisclosableObject(
    action: DisclosableObjectSpecBuilder<K, P, DisclosableObject<K, P>, DisclosableArray<K, P>>.() -> Unit,
): DisclosableObject<K, P> = buildDisclosableObject(DisclosableContainerFactory.default(), action)
