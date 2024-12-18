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
package eu.europa.ec.eudi.sdjwt

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class SpecExamples {

    @Test
    fun `Example 1 presentation of all claims`() = test(
        unverifiedSdJwt = """
            eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdjNmJkd
            DJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5d
            TVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHb
            EFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3Jae
            mZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS
            0JNTSIsICJYekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFI
            iwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5aFYxY1JEMEFUTjNvUUw5Sk0iLCAia
            nN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNzI
            jogImh0dHBzOi8vZXhhbXBsZS5jb20vaXNzdWVyIiwgImlhdCI6IDE2ODMwMDAwMDAsI
            CJleHAiOiAxODgzMDAwMDAwLCAic3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllc
            yI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15VGE2VWpsWm8zZGgta284YUlLUWM5RGxHe
            mhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlc
            lAwVmtCSnJXWjAifV0sICJfc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siO
            iB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAieCI6ICJUQ0FFUjE5WnZ1M09IR
            jRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIV
            ldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.kmx687kUBiIDvKWgo2Dub
            -TpdCCRLZwtD7TOj4RoLsUbtFBI8sMrtH2BejXtm_P6fOAjKAVc_7LRNJFgm3PJhg~Wy
            IyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd~WyJlb
            HVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyI2SWo3
            dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20i
            XQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251bWJlciIsICIrMS0yM
            DItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bW
            Jlcl92ZXJpZmllZCIsIHRydWVd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZ
            HJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5I
            jogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMif
            V0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxL
            TAxIl0~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgInVwZGF0ZWRfYXQiLCAxNTcwM
            DAwMDAwXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0~WyJuUHVvUW5rUk
            ZxM0JJZUFtN0FuWEZBIiwgIkRFIl0~
        """.trimIndent().removeNewLine(),
    )

    @Test
    fun `Example 1 presentation of given_name, family_name, and address`() = test(
        unverifiedSdJwt = """
            eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdjNmJkd
            DJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5d
            TVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHb
            EFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3Jae
            mZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS
            0JNTSIsICJYekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFI
            iwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5aFYxY1JEMEFUTjNvUUw5Sk0iLCAia
            nN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNzI
            jogImh0dHBzOi8vZXhhbXBsZS5jb20vaXNzdWVyIiwgImlhdCI6IDE2ODMwMDAwMDAsI
            CJleHAiOiAxODgzMDAwMDAwLCAic3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllc
            yI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15VGE2VWpsWm8zZGgta284YUlLUWM5RGxHe
            mhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlc
            lAwVmtCSnJXWjAifV0sICJfc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siO
            iB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAieCI6ICJUQ0FFUjE5WnZ1M09IR
            jRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIV
            ldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.kmx687kUBiIDvKWgo2Dub
            -TpdCCRLZwtD7TOj4RoLsUbtFBI8sMrtH2BejXtm_P6fOAjKAVc_7LRNJFgm3PJhg~Wy
            JlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyJBS
            ngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzI
            jogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogI
            kFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5ST
            jl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNB
            IiwgIlVTIl0~
        """.trimIndent().removeNewLine(),
    )

    @Test
    fun example3() = test(
        unverifiedSdJwt = """
           eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIi1hU3puSWQ5bVdNOG9jdVFvbENsbHN4V
           mdncTEtdkhXNE90bmhVdFZtV3ciLCAiSUticllObjN2QTdXRUZyeXN2YmRCSmpERFVfR
           XZRSXIwVzE4dlRScFVTZyIsICJvdGt4dVQxNG5CaXd6TkozTVBhT2l0T2w5cFZuWE9hR
           UhhbF94a3lOZktJIl0sICJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiL
           CAiaWF0IjogMTY4MzAwMDAwMCwgImV4cCI6IDE4ODMwMDAwMDAsICJ2ZXJpZmllZF9jb
           GFpbXMiOiB7InZlcmlmaWNhdGlvbiI6IHsiX3NkIjogWyI3aDRVRTlxU2N2REtvZFhWQ
           3VvS2ZLQkpwVkJmWE1GX1RtQUdWYVplM1NjIiwgInZUd2UzcmFISUZZZ0ZBM3hhVUQyY
           U14Rno1b0RvOGlCdTA1cUtsT2c5THciXSwgInRydXN0X2ZyYW1ld29yayI6ICJkZV9hb
           WwiLCAiZXZpZGVuY2UiOiBbeyIuLi4iOiAidFlKMFREdWN5WlpDUk1iUk9HNHFSTzV2a
           1BTRlJ4RmhVRUxjMThDU2wzayJ9XX0sICJjbGFpbXMiOiB7Il9zZCI6IFsiUmlPaUNuN
           l93NVpIYWFka1FNcmNRSmYwSnRlNVJ3dXJSczU0MjMxRFRsbyIsICJTXzQ5OGJicEt6Q
           jZFYW5mdHNzMHhjN2NPYW9uZVJyM3BLcjdOZFJtc01vIiwgIldOQS1VTks3Rl96aHNBY
           jlzeVdPNklJUTF1SGxUbU9VOHI4Q3ZKMGNJTWsiLCAiV3hoX3NWM2lSSDliZ3JUQkppL
           WFZSE5DTHQtdmpoWDFzZC1pZ09mXzlsayIsICJfTy13SmlIM2VuU0I0Uk9IbnRUb1FUO
           EptTHR6LW1oTzJmMWM4OVhvZXJRIiwgImh2RFhod21HY0pRc0JDQTJPdGp1TEFjd0FNc
           ERzYVUwbmtvdmNLT3FXTkUiXX19LCAiX3NkX2FsZyI6ICJzaGEtMjU2In0.Xtpp8nvAq
           22k6wNRiYHGRoRnkn3EBaHdjcaa0sf0sYjCiyZnmSRlxv_C72gRwfVQkSA36ID_I46QS
           TZvBrgm3g~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgInRpbWUiLCAiMjAxMi0wNC
           0yM1QxODoyNVoiXQ~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgeyJfc2QiOiBbIjl
           3cGpWUFd1RDdQSzBuc1FETDhCMDZsbWRnVjNMVnliaEh5ZFFwVE55TEkiLCAiRzVFbmh
           PQU9vVTlYXzZRTU52ekZYanBFQV9SYy1BRXRtMWJHX3djYUtJayIsICJJaHdGcldVQjY
           zUmNacTl5dmdaMFhQYzdHb3doM08ya3FYZUJJc3dnMUI0IiwgIldweFE0SFNvRXRjVG1
           DQ0tPZURzbEJfZW11Y1lMejJvTzhvSE5yMWJFVlEiXX1d~WyJlSThaV205UW5LUHBOUG
           VOZW5IZGhRIiwgIm1ldGhvZCIsICJwaXBwIl0~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YW
           pBIiwgImdpdmVuX25hbWUiLCAiTWF4Il0~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIi
           wgImZhbWlseV9uYW1lIiwgIk1cdTAwZmNsbGVyIl0~WyJ5MXNWVTV3ZGZKYWhWZGd3UG
           dTN1JRIiwgImFkZHJlc3MiLCB7ImxvY2FsaXR5IjogIk1heHN0YWR0IiwgInBvc3RhbF
           9jb2RlIjogIjEyMzQ0IiwgImNvdW50cnkiOiAiREUiLCAic3RyZWV0X2FkZHJlc3MiOi
           AiV2VpZGVuc3RyYVx1MDBkZmUgMjIifV0~
        """.trimIndent().replace("\n", ""),
    )

    private fun test(unverifiedSdJwt: String) = runTest {
        DefaultSdJwtOps.verifyPresentation(
            DefaultSdJwtOps.NoSignatureValidation,
            KeyBindingVerifier.MustNotBePresent,
            unverifiedSdJwt,
        ).getOrThrow()
            .also { (sdJwt, _) -> sdJwt.printRecreated() }
    }

    private fun SdJwt<JwtAndClaims>.printRecreated() {
        with(DefaultSdJwtOps) {
            println(json.encodeToString(recreateClaims(visitor = null)))
        }
    }
}
