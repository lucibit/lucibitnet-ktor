package net.lucibit.plugins.pong

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.html.P
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.lucibit.plugins.pong.PlayerSide.LEFT
import net.lucibit.plugins.pong.PlayerSide.RIGHT
import java.lang.Exception

@Serializable
data class Command(
    val type: String,
    var playerSide: PlayerSide? = null,
    var direction: Direction? = null,
    var state: GameState? = null
)

fun Command.serialize(): String = Json.encodeToString(this)

sealed class GameStatus {
    class Connected(val player: Player): GameStatus()
    object Full : GameStatus()
    object Running : GameStatus()
    object Waiting : GameStatus()
}

class Game {
    private val gameState: GameState = GameState()
    private val gameStateLock: Mutex = Mutex()
    private var runLoop: Job? = null

    init {
        println(gameState)
    }

    suspend fun addPlayer(serverSession: DefaultWebSocketServerSession) {
        updateState {
            // check which player is connected already
            if (gameState.players.containsKey(LEFT) && gameState.players.containsKey(RIGHT)) {
                GameStatus.Full
            }
            val playerSide = if (it.players[LEFT] == null) LEFT else RIGHT
            val player = Player(
                serverSession = serverSession,
                side = playerSide
            )
            gameState.players[playerSide] = player
            if (gameState.players.containsKey(LEFT) && gameState.players.containsKey(RIGHT)) {
                gameState.ready = true
            }
            GameStatus.Connected(player)
        }.also {
            when(it) {
                is GameStatus.Connected -> {
                    if (gameState.ready) {
                        runLoop = loop().launchIn(CoroutineScope(SupervisorJob()))
                    }
                    sendCommand(it.player, Command(type = "connected", playerSide = it.player.side).serialize())
                    listen(it.player)
                }
                is GameStatus.Full -> serverSession.send(Command(type = "full").serialize())
                else -> {}
            }
        }
    }

    private suspend fun loop() = flow {
        delay(100)
        while (gameState.ready) {
            updateState {
                it.moveBall()
                GameStatus.Running
            }.also {
                delay(100)
                emit(Unit)
            }
        }
    }

    private suspend fun movePlayer(player: Player, direction: Direction) = updateState {
        player.move(direction)
        GameStatus.Running
    }

    private suspend fun removePlayer(player: Player) = updateState {
        println("Removing Player ${player.side}")
        it.players.remove(player.side)
        it.ready = false
        GameStatus.Waiting
    }.also {
        player.webSocket.close()
        runLoop?.let { if (it.isActive) it.cancel() }
    }

    private suspend fun listen(player: Player) {
        println("Listening to player ${player.side}")
        try {
            for (frame in player.webSocket.incoming) {
                frame as? Frame.Text ?: continue
                receiveCommand(player, frame.readText())
            }
        } catch (e: ClosedReceiveChannelException) {
            removePlayer(player)
        } catch (e: Exception) {
            println("Exception when listening")
            e.printStackTrace()
        }
    }


    /**
     * Updates the state by performing an action under state lock, then notifies players online of the changes.
     * @param action an action to perform that can mutate the state and returns [GameStatus] after action.
     *
     * @return [GameStatus] after update.
     */
    private suspend fun updateState(action: (gameState: GameState) -> GameStatus): GameStatus {
        val onlinePlayers = mutableListOf<Player>()
        val gameStatus: GameStatus
        // capture serialized state after modification
        val updateCommand = gameStateLock.withLock {
            gameStatus = action(gameState)
            onlinePlayers.addAll(gameState.players.values)
            Command(type = "state", state = gameState).serialize()
        }
        onlinePlayers.forEach { player -> sendCommand(player, updateCommand) }
        return gameStatus
    }


    private suspend fun receiveCommand(player: Player, serializedCommand: String) {
        println("Command Received $serializedCommand")
        val command = Json.decodeFromString<Command>(serializedCommand)
        when (command.type) {
            "move" -> movePlayer(player, direction = command.direction!!)
            "close" -> removePlayer(player)
                .also { player.webSocket.close(CloseReason(CloseReason.Codes.NORMAL, "client said bye")) }
        }
    }

    private suspend fun sendCommand(player: Player, serializedCommand: String) {
        println("Sending Command $serializedCommand to player ${player.side}")
        try {
            player.webSocket.send(serializedCommand)
        } catch (e: ClosedReceiveChannelException) {
            removePlayer(player)
        } catch (e: Exception) {
            println("Exception when sending to player ${player.side} reason: ${player.webSocket.closeReason.await()}" )
            e.printStackTrace()
        }
    }
}





