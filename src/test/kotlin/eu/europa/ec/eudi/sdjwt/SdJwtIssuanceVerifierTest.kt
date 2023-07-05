package eu.europa.ec.eudi.sdjwt

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SdJwtIssuanceVerifierTest  {


    @Test
    fun simple() {
        SdJwtIssuanceVerifier().verifySuccess(sdJwt = "$jwt~$d1")
    }



    private fun SdJwtIssuanceVerifier.verifySuccess(sdJwt: String) {
        val verification = verify(sdJwt = sdJwt)
        assertTrue { verification.isSuccess }
    }


    private val jwt = """
            eyJhbGciOiAiRVMyNTYifQ.eyJfc2QiOiBbIkZwaEZGcGoxdnRyMHJwWUstMTRmaWNrR
            0tNZzN6ZjFmSXBKWHhUSzhQQUUiXSwgImlzcyI6ICJodHRwczovL2V4YW1wbGUuY29tL
            2lzc3VlciIsICJpYXQiOiAxNTE2MjM5MDIyLCAiZXhwIjogMTczNTY4OTY2MSwgInN1Y
            iI6ICI2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiLCAiX3NkX2FsZ
            yI6ICJzaGEtMjU2In0.tqqCvNdrZ8ILN82t3g-T8LQJp3ykVf8tVPfAr8ijqhG9uc0Kl
            wYeE4ISu3DQkOk7VeaMMYB73Hsdyjal6e9FS
    """.trimIndent().removeNewLine()

    private val d1 = """
            WyJpbVFmR2oxX00wRWw3NmtkdmY3RG
            F3IiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIlNjaHVsc3RyLiAxMiIsIC
            Jsb2NhbGl0eSI6ICJTY2h1bHBmb3J0YSIsICJyZWdpb24iOiAiU2FjaHNlbi1BbmhhbH
            QiLCAiY291bnRyeSI6ICJERSJ9XQ
    """.trimIndent().removeNewLine()

}