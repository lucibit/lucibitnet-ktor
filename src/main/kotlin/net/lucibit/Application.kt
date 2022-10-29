package net.lucibit

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.lucibit.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureMonitoring()
        configureTemplating()
        configureRouting()
        configureWebSockets()
    }.start(wait = true)
}
