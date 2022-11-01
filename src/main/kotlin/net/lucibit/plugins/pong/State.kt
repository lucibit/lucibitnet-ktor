package net.lucibit.plugins.pong

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.lucibit.plugins.pong.PlayerSide.LEFT
import net.lucibit.plugins.pong.PlayerSide.RIGHT

const val BOARD_HEIGHT = 500
const val BOARD_WIDTH = 500

enum class Direction(val moveFactor: Int) {
    RIGHT(1),
    LEFT(-1),
    UP(-1),
    DOWN(1);

    companion object {
        fun randomX(): Direction {
            return if (Math.random() < 0.5) LEFT else RIGHT
        }

        fun randomY(): Direction {
            return if (Math.random() < 0.5) UP else DOWN
        }
    }

    fun opposite(): Direction {
        return when (this) {
            RIGHT -> LEFT
            LEFT -> RIGHT
            UP -> DOWN
            DOWN -> UP
        }
    }
}

enum class PlayerSide {
    RIGHT, LEFT
}

@Serializable
data class Ball(
    var x: Int, var y: Int,
    var motionDirectionX: Direction, var motionDirectionY: Direction,
    var speed: Int = 10
) {

    constructor(turn: PlayerSide) : this(
        // TODO randomize position
        x = BOARD_WIDTH / 2 - SIZE / 2,
        y = BOARD_HEIGHT / 2 - SIZE / 2,
        motionDirectionX = if (turn == LEFT) Direction.LEFT else Direction.RIGHT,
        motionDirectionY = Direction.randomY()
    )

    companion object {
        const val SIZE = 20 // px
    }
}

@Serializable
data class Player(
    var y: Int,
    val side: PlayerSide,
) {

    companion object {
        const val SPEED = 20
        const val PADDLE_HEIGHT = 60
        const val PADDLE_WIDTH = 10
    }

    @Transient
    lateinit var webSocket: DefaultWebSocketServerSession

    constructor(side: PlayerSide, serverSession: DefaultWebSocketServerSession) : this(
        y = BOARD_HEIGHT / 2 - PADDLE_HEIGHT / 2,
        side = side
    ) {
        this.webSocket = serverSession
    }

    fun move(direction: Direction) {
        println("Moving Player")
        val newY = y + direction.moveFactor * Player.SPEED
        y = if (newY < 0) 0
        else if (newY + PADDLE_HEIGHT > BOARD_HEIGHT) BOARD_HEIGHT - PADDLE_HEIGHT
        else newY
    }
}

@Serializable
class GameState(
    var ready: Boolean = false,
    val players: MutableMap<PlayerSide, Player> = mutableMapOf(),
    var turn: PlayerSide = if (Math.random() < 0.5) LEFT else RIGHT,
) {
    var ball: Ball = Ball(this.turn)

    fun moveBall() {

        val newBallX = ball.x + ball.motionDirectionX.moveFactor * ball.speed
        val newBallY = ball.y + ball.motionDirectionY.moveFactor * ball.speed


        println("Moving ball")
        // check X out of range
        if (turn == LEFT && newBallX <= Player.PADDLE_WIDTH) {
            // check player collision
            if (isBallInPlayerYBounds(newBallY)) {
                // reset ball
                turn = if (turn == LEFT) RIGHT else LEFT
                ball.x = Player.PADDLE_WIDTH
                ball.motionDirectionX = ball.motionDirectionX.opposite()
                return
            } else {
                // game lost
                turn = if (turn == LEFT) RIGHT else LEFT
                ball = Ball(this.turn)
                return
            }
        }

        if (turn == RIGHT && newBallX >= BOARD_WIDTH - Player.PADDLE_WIDTH - Ball.SIZE) {
            if (isBallInPlayerYBounds(newBallY)) {
                // reset ball
                turn = if (turn == LEFT) RIGHT else LEFT
                ball.x = BOARD_WIDTH - Player.PADDLE_WIDTH - Ball.SIZE
                ball.motionDirectionX = ball.motionDirectionX.opposite()
                return
            } else {
                // game lost
                turn = if (turn == LEFT) RIGHT else LEFT
                ball = Ball(this.turn)
                return
            }
        }

        if (newBallY <= 0) {
            ball.motionDirectionY = ball.motionDirectionY.opposite()
            ball.y = 0
            return
        }

        if (newBallY >= BOARD_HEIGHT - Ball.SIZE) {
            ball.y = BOARD_HEIGHT - Ball.SIZE
            ball.motionDirectionY = ball.motionDirectionY.opposite()
            return
        }

        ball.x = newBallX
        ball.y = newBallY
    }

    private fun isBallInPlayerYBounds(newBallY: Int): Boolean {
        return players[turn]?.y?.let { newBallY >= it - Ball.SIZE && newBallY <= it + Player.PADDLE_HEIGHT } ?: false
    }
}