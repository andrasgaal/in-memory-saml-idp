package com.andrasgaal.inmemoryidp

import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationTest {

    val client = ApacheClient()

    @Test
    fun `idp is listening on given port`() {
        val idpPort = 8000
        InmemoryIdp(idpPort).start()

        val response = client(Request(Method.GET, "http://localhost:$idpPort"))

        assertTrue(response.status.successful)
    }


}