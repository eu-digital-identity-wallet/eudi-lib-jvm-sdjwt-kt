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

import eu.europa.ec.eudi.sdjwt.RFC9901
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SdJwtObjectTest {

    @Test
    fun `fails on usage of reserved claims for never selectively or always selectively disclosed claims`() {
        fun test(builder: SdJwtObjectBuilder.() -> Unit) {
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    sdJwt {
                        builder()
                    }
                }
            assertEquals(
                "Given claim should not contain an attribute named _sd_alg, or _sd, or ...",
                exception.message,
            )
        }

        val reserved = setOf(RFC9901.CLAIM_SD_ALG, RFC9901.CLAIM_SD, RFC9901.CLAIM_ARRAY_ELEMENT_DIGEST)

        // top-level
        reserved.forEach {
            test {
                claim(it, "test")
            }

            test {
                sdClaim(it, "test")
            }
        }

        // nested in object
        reserved.forEach {
            test {
                objClaim("object") {
                    claim(it, "test")
                }
            }

            test {
                objClaim("object") {
                    sdClaim(it, "test")
                }
            }

            test {
                sdObjClaim("object") {
                    claim(it, "test")
                }
            }

            test {
                sdObjClaim("object") {
                    sdClaim(it, "test")
                }
            }
        }

        // nested in array
        reserved.forEach {
            test {
                arrClaim("array") {
                    objClaim {
                        claim(it, "test")
                    }
                }
            }

            test {
                arrClaim("array") {
                    objClaim {
                        sdClaim(it, "test")
                    }
                }
            }

            test {
                arrClaim("array") {
                    sdObjClaim {
                        claim(it, "test")
                    }
                }
            }

            test {
                arrClaim("array") {
                    sdObjClaim {
                        sdClaim(it, "test")
                    }
                }
            }

            test {
                sdArrClaim("array") {
                    objClaim {
                        claim(it, "test")
                    }
                }
            }

            test {
                sdArrClaim("array") {
                    objClaim {
                        sdClaim(it, "test")
                    }
                }
            }

            test {
                sdArrClaim("array") {
                    sdObjClaim {
                        claim(it, "test")
                    }
                }
            }

            test {
                sdArrClaim("array") {
                    sdObjClaim {
                        sdClaim(it, "test")
                    }
                }
            }
        }
    }
}
