package net.lucibit.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.lucibit.plugins.pong.Game

fun Application.configureWebSockets() {
    install(WebSockets)
    val game = Game()
    routing {
        webSocket("/game") {
            println("Connecting")
            try {
                game.addPlayer(this)
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }
        }
    }
}