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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DisclosableTest {

    @Test
    fun `test Disclosable NeverSelectively creation and properties`() {
        val value = "test"
        val disclosable = Disclosable.NeverSelectively(value)

        assertEquals(value, disclosable.value)
        assertIs<Disclosable.NeverSelectively<String>>(disclosable)
    }

    @Test
    fun `test Disclosable AlwaysSelectively creation and properties`() {
        val value = "test"
        val disclosable = Disclosable.AlwaysSelectively(value)

        assertEquals(value, disclosable.value)
        assertIs<Disclosable.AlwaysSelectively<String>>(disclosable)
    }

    @Test
    fun `test not operator on value`() {
        val value = "test"
        val disclosable = !value

        assertEquals(value, disclosable.value)
        assertIs<Disclosable.NeverSelectively<String>>(disclosable)
    }

    @Test
    fun `test unaryPlus operator on value`() {
        val value = "test"
        val disclosable = +value

        assertEquals(value, disclosable.value)
        assertIs<Disclosable.AlwaysSelectively<String>>(disclosable)
    }

    @Test
    fun `test map function on Disclosable NeverSelectively`() {
        val value = 5
        val disclosable = Disclosable.NeverSelectively(value)
        val mapped = disclosable.map { it * 2 }

        assertEquals(10, mapped.value)
        assertIs<Disclosable.NeverSelectively<Int>>(mapped)
    }

    @Test
    fun `test map function on Disclosable AlwaysSelectively`() {
        val value = 5
        val disclosable = Disclosable.AlwaysSelectively(value)
        val mapped = disclosable.map { it * 2 }

        assertEquals(10, mapped.value)
        assertIs<Disclosable.AlwaysSelectively<Int>>(mapped)
    }

    @Test
    fun `test DisclosableValue Id creation and properties`() {
        val value = "test"
        val id = DisclosableValue.Id<String, String>(value)

        assertEquals(value, id.value)
        assertIs<DisclosableValue.Id<String, String>>(id)
    }

    @Test
    fun `test DisclosableValue Obj creation and properties`() {
        val factory = DisclosableContainerFactory.default<String, String>()
        val objContent = mapOf<String, DisclosableElement<String, String>>(
            "key" to Disclosable.AlwaysSelectively(DisclosableValue.Id("value")),
        )
        val obj = factory.obj(objContent)
        val disclosableObj = DisclosableValue.Obj(obj)

        assertEquals(obj, disclosableObj.value)
        assertIs<DisclosableValue.Obj<String, String>>(disclosableObj)
        assertEquals(objContent, obj.content)
    }

    @Test
    fun `test DisclosableValue Arr creation and properties`() {
        val factory = DisclosableContainerFactory.default<String, String>()
        val arrContent = listOf<DisclosableElement<String, String>>(
            Disclosable.AlwaysSelectively(DisclosableValue.Id("value")),
        )
        val arr = factory.arr(arrContent)
        val disclosableArr = DisclosableValue.Arr(arr)

        assertEquals(arr, disclosableArr.value)
        assertIs<DisclosableValue.Arr<String, String>>(disclosableArr)
        assertEquals(arrContent, arr.content)
    }

    @Test
    fun `test DisclosableValue companion object factory methods`() {
        val value = "test"
        val id = DisclosableValue.invoke<String, String>(value)

        assertIs<DisclosableValue.Id<String, String>>(id)
        assertEquals(value, id.value)

        val factory = DisclosableContainerFactory.default<String, String>()
        val objContent = mapOf<String, DisclosableElement<String, String>>(
            "key" to Disclosable.AlwaysSelectively(DisclosableValue.Id("value")),
        )
        val obj = factory.obj(objContent)
        val disclosableObj = DisclosableValue.obj(obj)

        assertIs<DisclosableValue.Obj<String, String>>(disclosableObj)
        assertEquals(obj, disclosableObj.value)

        val arrContent = listOf<DisclosableElement<String, String>>(
            Disclosable.AlwaysSelectively(DisclosableValue.Id("value")),
        )
        val arr = factory.arr(arrContent)
        val disclosableArr = DisclosableValue.arr(arr)

        assertIs<DisclosableValue.Arr<String, String>>(disclosableArr)
        assertEquals(arr, disclosableArr.value)
    }

    @Test
    fun `test mapElements function for DisclosableObject`() {
        val factory = DisclosableContainerFactory.default<String, Int>()
        val objContent = mapOf<String, DisclosableElement<String, Int>>(
            "key" to Disclosable.AlwaysSelectively(DisclosableValue.Id(5)),
        )
        val obj = factory.obj(objContent)
        val mapped = obj.map(fK = { it }) { it * 2 }

        val mappedElement = mapped.content["key"]
        assertTrue(mappedElement != null)
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, Int>>>(mappedElement)
        val mappedValue = (mappedElement.value as DisclosableValue.Id<String, Int>).value
        assertEquals(10, mappedValue)
    }
}
