package net.lucibit.plugins.pong

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.lucibit.plugins.pong.PlayerSide.LEFT
import net.lucibit.plugins.pong.PlayerSide.RIGHT

const val BOARD_PADDING = 10
const val PADDLE_HEIGHT = 60

enum class Direction(val factor: Int) {

    RIGHT(1),
    LEFT(-1),
    UP(-1),
    DOWN(1);

}

enum class PlayerSide {
    RIGHT, LEFT
}

const val PLAYER_MOVE_RESOLUTION = 20
const val PLAYER_PADDLE_HEIGHT = 60
const val BOARD_HEIGHT = 500
const val BOARD_WIDTH = 500


@Serializable
data class Ball(val x: Int, val y: Int, var motionDirectionX: Direction, var motionDirectionY: Direction) {
    companion object {
        const val RADIUS = 10 // px
    }
}

// We do not need to store the state of the board
// - that would be too many messages to update state from server to client for when ball moves
// - only thing that n
@Serializable
data class Player(
    var y: Int,
    val side: PlayerSide,
) {
    @Transient
    lateinit var webSocket: DefaultWebSocketServerSession

    constructor(side: PlayerSide, serverSession: DefaultWebSocketServerSession) : this(
        side = side,
        y = (BOARD_HEIGHT - 2 * BOARD_PADDING) / 2 - PLAYER_PADDLE_HEIGHT / 2
    ) {
        this.webSocket = serverSession
    }

    fun move(direction: Direction) {
        val newY = y + direction.factor * PLAYER_MOVE_RESOLUTION
        y = if (newY < 0) 0
        else if (newY + PADDLE_HEIGHT > BOARD_HEIGHT) BOARD_HEIGHT - PADDLE_HEIGHT
        else newY
        println("Updated ${this.side} $y")
    }
}

@Serializable
data class GameState(
    val ball: Ball,
    var ready: Boolean = false,
    val players: MutableMap<PlayerSide, Player> = mutableMapOf()
)

@Serializable
data class Command(
    val type: String,
    var playerSide: PlayerSide? = null,
    var direction: Direction? = null,
    var state: GameState? = null
)

class Game {
    private val gameState: GameState
    private val gameStateLock: Mutex = Mutex()


    init {
        val ball = Ball( // TODO randomize position and direction
            x = (BOARD_WIDTH - 2 * BOARD_PADDING) / 2,
            y = (BOARD_WIDTH - 2 * BOARD_PADDING) / 2,
            motionDirectionX = Direction.LEFT,
            motionDirectionY = Direction.DOWN
        )
        this.gameState = GameState(ball)
    }

    suspend fun addPlayer(serverSession: DefaultWebSocketServerSession) {
        val player: Player
        val playerSide: PlayerSide
        gameStateLock.withLock {
            playerSide = if (gameState.players[LEFT] == null) {
                LEFT
            } else if (gameState.players[RIGHT] == null) {
                RIGHT
            } else {
                serverSession.send(Json.encodeToString(Command(type = "full")))
                return
            }
            player = Player(
                serverSession = serverSession,
                side = playerSide
            )
            gameState.players[playerSide] = player
            println("Players ${gameState.players}")
            if (!gameState.ready && gameState.players.containsKey(LEFT) && gameState.players.containsKey(RIGHT)) {
                gameState.ready = true
                start()
            }
        }
        //send initial start command
        serverSession.send(Json.encodeToString(Command(type = "connected", playerSide = player.side)))
        listen(player)

    }

    private suspend fun start() {
        gameState.players.values.forEach { updatePlayerClientState(it) }
        // TODO start ball updates
    }

    private suspend fun listen(player: Player) {
        try {
            for (frame in player.webSocket.incoming) {
                frame as? Frame.Text ?: continue
                executeCommand(player, frame.readText())
            }
        } catch (e: ClosedReceiveChannelException) {
            removePlayer(player)
        }

    }

    suspend fun updateBall() {
        gameStateLock.withLock {
            // TODO move ball
            gameState.players.values.forEach { updatePlayerClientState(it) }
        }
    }

    private suspend fun movePlayer(player: Player, direction: Direction) {
        gameStateLock.withLock {
            println("Player ${player.side} moved $direction")
            player.move(direction)

            // notify players
            gameState.players.values.forEach { updatePlayerClientState(it) }
        }
    }

    private suspend fun updatePlayerClientState(player: Player) {

        val command = Json.encodeToString(Command(type = "state", state = gameState))
        println("Sending command $command")
        player.webSocket.send(command)
    }

    private suspend fun removePlayer(player: Player) {
        gameStateLock.withLock {
            println("Removing player ${player.side}")
            gameState.players.remove(player.side)
            gameState.ready = false
        }
    }

    private suspend fun executeCommand(player: Player, message: String) {
        println("Received command $message")
        val command = Json.decodeFromString<Command>(message)

        when (command.type) {
            "move" -> movePlayer(player, direction = command.direction!!)
            "close" -> removePlayer(player)
        }
    }
}





