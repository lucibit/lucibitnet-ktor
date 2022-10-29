package net.lucibit.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import net.lucibit.plugins.pong.Game

fun Application.configureWebSockets() {
    install(WebSockets)
    val game = Game()
    routing {
        webSocket("/game") {
            println("Connecting")
            game.addPlayer(this)
        }
    }
}