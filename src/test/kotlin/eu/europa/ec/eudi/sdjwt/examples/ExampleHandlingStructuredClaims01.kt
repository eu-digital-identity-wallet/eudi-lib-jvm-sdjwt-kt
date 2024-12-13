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
package eu.europa.ec.eudi.sdjwt.examples

import eu.europa.ec.eudi.sdjwt.*

val handlingStructuredClaims =
    sdJwt {
        claim("iss", "https://issuer.example.com")
        claim("iat", 1683000000)
        claim("exp", 1883000000)

        sdClaim("sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c")
        sdClaim("given_name", "太郎")
        sdClaim("family_name", "山田")
        sdClaim("email", "\"unusual email address\"@example.jp")
        sdClaim("phone_number", "+81-80-1234-5678")
        sdClaim("birthdate", "1940-01-01")

        objClaim("address") {
            sdClaim("street_address", "東京都港区芝公園４丁目２−８")
            sdClaim("locality", "東京都")
            sdClaim("region", "港区")
            sdClaim("country", "JP")
        }
    }
