let webSocket;
const gameState = {}

const DIRECTION = {
    UP: 'UP',
    DOWN: 'DOWN',
    LEFT: 'LEFT',
    RIGHT: 'RIGHT'
}
const SIDE = {
    LEFT: 'LEFT',
    RIGHT: 'RIGHT'
}
const PADDING = 20;

class PlayerPaddle {
    speed = 0;
    width = 15;
    height = 70;
    x;
    y;
    side;
    constructor(canvas, side) {
        this.x = canvas.width;
        this.side = side;
    }
}

function draw() {
    const canvas = document.getElementById('canvas');
    const ctx = canvas.getContext('2d');

    ctx.clearRect(0, 0, canvas.width, canvas.height); // clear canvas
    const leftPlayer = gameState && gameState.players['LEFT']
    if (leftPlayer) {
        ctx.fillStyle = 'blue';
        ctx.fillRect(0, leftPlayer.y, 10,  60);
        ctx.save();
    }

    const rightPlayer = gameState && gameState.players['RIGHT']
    if (rightPlayer) {
        ctx.fillStyle = 'red';
        ctx.fillRect(500 - 10, rightPlayer.y, 10,  60);
        ctx.save();
    }

    console.log(`Draw `)
    console.log(JSON.stringify(gameState))
    ctx.save();
    ctx.restore();

}

function sendCommand(message) {
    console.log(`Sending Command ${message}`);
    webSocket.send(JSON.stringify(message));
}

function receiveCommand(message) {
    const command = JSON.parse(message)
    console.log(`Message ${message}`)

    switch (command.type) {
        case "connected":
            showMessage(`Connected as Player ${command.playerSide}`)
            Object.assign(gameState, command.state)
            break
        case "full":
            showMessage(`Server Full`)
            Object.assign(gameState, command.state)
            break
        case "state":
            console.log(command)
            Object.assign(gameState, command.state)
            draw()
            break
    }
}

function showMessage(message) {
    const element = document.getElementById('message');
    element.textContent = message
}

function init() {
    webSocket = new WebSocket(`ws://${SERVER}/game`);

    webSocket.onopen = (event) => console.log("Connecting to server");
    webSocket.onmessage = (event) => receiveCommand(event.data)
    window.onbeforeunload = (event) => sendCommand({type: "close"})
    window.onunload = (event) => sendCommand({type: "close"})
    window.onkeydown = (event) => {
        if (event.key === "ArrowUp") {
            sendCommand({type: "move", direction: DIRECTION.UP})
        }
        else if (event.key === "ArrowDown") {
            sendCommand({type: "move", direction: DIRECTION.DOWN})
        }
    }
    //window.requestAnimationFrame(() => draw(canvasContext));
}