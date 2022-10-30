package net.lucibit.plugins

import io.ktor.server.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.lucibit.plugins.pong.BOARD_HEIGHT
import net.lucibit.plugins.pong.BOARD_WIDTH

fun Application.configureTemplating() {
    val port = environment.config.port
    val host = if (environment.developmentMode) "0.0.0.0" else "lucibit.net"
    val server = "$host:$port"
    routing {
        get("/index.html") {
            call.respondHtml {
                head {
                    script(type = "text/javascript") {
                        + "const SERVER = '$server'"
                    }
                    script(type = "text/javascript", src = "/static/app.js") {}
                }
                body {
                    onLoad = "init();"
                    h1 { +"Pong" }
                    h2 { id = "message" }
                    canvas {
                        id = "canvas"
                        width = "$BOARD_WIDTH"
                        height = "$BOARD_HEIGHT"
                    }
                }
            }
        }
        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.darkBlue
                    margin(0.px)
                }
                rule("h1.page-title") {
                    color = Color.white
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

