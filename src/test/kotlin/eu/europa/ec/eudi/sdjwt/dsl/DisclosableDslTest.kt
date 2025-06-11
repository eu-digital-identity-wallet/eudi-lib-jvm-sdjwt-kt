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

import eu.europa.ec.eudi.sdjwt.dsl.values.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.values.buildDisclosableArray
import eu.europa.ec.eudi.sdjwt.dsl.values.buildDisclosableObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DisclosableDslTest {

    @Test
    fun `test DisclosableArraySpecBuilder claim method`() {
        val array = buildDisclosableArray<String, String> {
            claim("test")
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val value = (element.value as DisclosableValue.Id<String, String>).value
        assertEquals("test", value)
    }

    @Test
    fun `test DisclosableArraySpecBuilder sdClaim method`() {
        val array = buildDisclosableArray<String, String> {
            sdClaim("test")
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val value = (element.value as DisclosableValue.Id<String, String>).value
        assertEquals("test", value)
    }

    @Test
    fun `test DisclosableArraySpecBuilder objClaim method with object`() {
        val innerObj = buildDisclosableObject {
            claim("key", "value")
        }

        val array = buildDisclosableArray {
            objClaim(innerObj)
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["key"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder objClaim method with action`() {
        val array = buildDisclosableArray {
            objClaim {
                claim("key", "value")
            }
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["key"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder sdObjClaim method with object`() {
        val innerObj = buildDisclosableObject {
            claim("key", "value")
        }

        val array = buildDisclosableArray {
            sdObjClaim(innerObj)
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["key"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder sdObjClaim method with action`() {
        val array = buildDisclosableArray {
            sdObjClaim {
                claim("key", "value")
            }
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["key"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder arrClaim method with array`() {
        val innerArr = buildDisclosableArray<String, String> {
            claim("value")
        }

        val array = buildDisclosableArray {
            arrClaim(innerArr)
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val arrValue = (element.value as DisclosableValue.Arr<String, String>).value
        assertEquals(1, arrValue.content.size)
        val innerElement = arrValue.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder arrClaim method with action`() {
        val array = buildDisclosableArray<String, String> {
            arrClaim {
                claim("value")
            }
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val arrValue = (element.value as DisclosableValue.Arr<String, String>).value
        assertEquals(1, arrValue.content.size)
        val innerElement = arrValue.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder sdArrClaim method with array`() {
        val innerArr = buildDisclosableArray<String, String> {
            claim("value")
        }

        val array = buildDisclosableArray {
            sdArrClaim(innerArr)
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val arrValue = (element.value as DisclosableValue.Arr<String, String>).value
        assertEquals(1, arrValue.content.size)
        val innerElement = arrValue.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableArraySpecBuilder sdArrClaim method with action`() {
        val array = buildDisclosableArray<String, String> {
            sdArrClaim {
                claim("value")
            }
        }

        assertEquals(1, array.content.size)
        val element = array.content[0]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val arrValue = (element.value as DisclosableValue.Arr<String, String>).value
        assertEquals(1, arrValue.content.size)
        val innerElement = arrValue.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", innerValue)
    }

    @Test
    fun `test DisclosableObjectSpecBuilder claim method`() {
        val obj = buildDisclosableObject {
            claim("key", "value")
        }

        assertEquals(1, obj.content.size)
        val element = obj.content["key"]
        assertNotNull(element)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val value = (element.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", value)
    }

    @Test
    fun `test DisclosableObjectSpecBuilder sdClaim method`() {
        val obj = buildDisclosableObject {
            sdClaim("key", "value")
        }

        assertEquals(1, obj.content.size)
        val element = obj.content["key"]
        assertNotNull(element)
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, String>>>(element)
        val value = (element.value as DisclosableValue.Id<String, String>).value
        assertEquals("value", value)
    }

    @Test
    fun `test DisclosableObjectSpecBuilder objClaim method with object`() {
        val innerObj = buildDisclosableObject {
            claim("innerKey", "innerValue")
        }

        val obj = buildDisclosableObject {
            objClaim("key", innerObj)
        }

        assertEquals(1, obj.content.size)
        val element = obj.content["key"]
        assertNotNull(element)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["innerKey"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("innerValue", innerValue)
    }

    @Test
    fun `test DisclosableObjectSpecBuilder objClaim method with action`() {
        val obj = buildDisclosableObject {
            objClaim("key") {
                claim("innerKey", "innerValue")
            }
        }

        assertEquals(1, obj.content.size)
        val element = obj.content["key"]
        assertNotNull(element)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(element)
        val objValue = (element.value as DisclosableValue.Obj<String, String>).value
        assertEquals(1, objValue.content.size)
        val innerElement = objValue.content["innerKey"]
        assertNotNull(innerElement)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, String>>>(innerElement)
        val innerValue = (innerElement.value as DisclosableValue.Id<String, String>).value
        assertEquals("innerValue", innerValue)
    }

    @Test
    fun `test buildDisclosableObject function`() {
        val obj = buildDisclosableObject {
            claim("key1", 1)
            sdClaim("key2", 2)
            objClaim("key3") {
                claim("innerKey", 3)
            }
            sdObjClaim("key4") {
                claim("innerKey", 4)
            }
        }

        assertEquals(4, obj.content.size)

        // Check key1
        val element1 = obj.content["key1"]
        assertNotNull(element1)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, Int>>>(element1)
        val value1 = (element1.value as DisclosableValue.Id<String, Int>).value
        assertEquals(1, value1)

        // Check key2
        val element2 = obj.content["key2"]
        assertNotNull(element2)
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, Int>>>(element2)
        val value2 = (element2.value as DisclosableValue.Id<String, Int>).value
        assertEquals(2, value2)

        // Check key3
        val element3 = obj.content["key3"]
        assertNotNull(element3)
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, Int>>>(element3)
        val objValue3 = (element3.value as DisclosableValue.Obj<String, Int>).value
        val innerElement3 = objValue3.content["innerKey"]
        assertNotNull(innerElement3)
        val innerValue3 = (innerElement3.value as DisclosableValue.Id<String, Int>).value
        assertEquals(3, innerValue3)

        // Check key4
        val element4 = obj.content["key4"]
        assertNotNull(element4)
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, Int>>>(element4)
        val objValue4 = (element4.value as DisclosableValue.Obj<String, Int>).value
        val innerElement4 = objValue4.content["innerKey"]
        assertNotNull(innerElement4)
        val innerValue4 = (innerElement4.value as DisclosableValue.Id<String, Int>).value
        assertEquals(4, innerValue4)
    }

    @Test
    fun `test buildDisclosableArray function`() {
        val array = buildDisclosableArray {
            claim(1)
            sdClaim(2)
            objClaim {
                claim("key", 3)
            }
            sdObjClaim {
                claim("key", 4)
            }
        }

        assertEquals(4, array.content.size)

        // Check element 0
        val element0 = array.content[0]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, Int>>>(element0)
        val value0 = (element0.value as DisclosableValue.Id<String, Int>).value
        assertEquals(1, value0)

        // Check element 1
        val element1 = array.content[1]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, Int>>>(element1)
        val value1 = (element1.value as DisclosableValue.Id<String, Int>).value
        assertEquals(2, value1)

        // Check element 2
        val element2 = array.content[2]
        assertIs<Disclosable.NeverSelectively<DisclosableValue<String, Int>>>(element2)
        val objValue2 = (element2.value as DisclosableValue.Obj<String, Int>).value
        val innerElement2 = objValue2.content["key"]
        assertNotNull(innerElement2)
        val innerValue2 = (innerElement2.value as DisclosableValue.Id<String, Int>).value
        assertEquals(3, innerValue2)

        // Check element 3
        val element3 = array.content[3]
        assertIs<Disclosable.AlwaysSelectively<DisclosableValue<String, Int>>>(element3)
        val objValue3 = (element3.value as DisclosableValue.Obj<String, Int>).value
        val innerElement3 = objValue3.content["key"]
        assertNotNull(innerElement3)
        val innerValue3 = (innerElement3.value as DisclosableValue.Id<String, Int>).value
        assertEquals(4, innerValue3)
    }
}
