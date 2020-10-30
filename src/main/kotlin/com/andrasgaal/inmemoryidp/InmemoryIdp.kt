package com.andrasgaal.inmemoryidp

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Netty
import org.http4k.server.asServer

class InmemoryIdp(private val port: Int) {

    private val app = { request: Request -> Response(OK).body("Hello") }

    fun start() {
        app.asServer(Netty(port)).start()
    }

}
