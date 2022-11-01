const DIRECTION = {
    UP: 'UP',
    DOWN: 'DOWN',
    LEFT: 'LEFT',
    RIGHT: 'RIGHT'
}

function pong() {

    const gameState = {}
    const webSocket = new WebSocket(`ws://${SERVER}/game`);

    function sendCommand(message) {
        console.log(`Sending Command ${message}`);
        webSocket.send(JSON.stringify(message));
    }

    function showMessage(message) {
        const element = document.getElementById('message');
        element.textContent = message
    }

    function receiveCommand(message) {
        const command = JSON.parse(message)
        console.log(`Message ${message}`)

        switch (command.type) {
            case "connected":
                Object.assign(gameState, command.state, {playerSide: command.playerSide})
                showMessage(`Connected as Player ${command.playerSide}` + (!!gameState.ready ? '' : ' - Waiting For Other Player'))
                break
            case "full":
                showMessage(`Server Full`)
                Object.assign(gameState, command.state)
                break
            case "state":
                Object.assign(gameState, command.state)
                showMessage(`Playing as ${gameState.playerSide}` + (!!gameState.ready ? '' : ' - Waiting For Other Player'))
                draw()
                break
        }
    }

    function move(event) {
        if (event.key === "ArrowUp") {
            sendCommand({type: "move", direction: DIRECTION.UP})
        } else if (event.key === "ArrowDown") {
            sendCommand({type: "move", direction: DIRECTION.DOWN})
        }
    }

    function draw() {
        const canvas = document.getElementById('canvas');
        const ctx = canvas.getContext('2d');

        ctx.clearRect(0, 0, canvas.width, canvas.height); // clear canvas
        ctx.strokeStyle = `black`
        ctx.strokeRect(0, 0, canvas.width, canvas.height);
        const leftPlayer = gameState && gameState.players['LEFT']
        if (leftPlayer) {
            ctx.fillStyle = 'blue';
            ctx.fillRect(0, leftPlayer.y, 10, 60);
            ctx.save();
        }

        const rightPlayer = gameState && gameState.players['RIGHT']
        if (rightPlayer) {
            ctx.fillStyle = 'red';
            ctx.fillRect(500 - 10, rightPlayer.y, 10, 60);
            ctx.save();
        }

        // draw ball
        if (gameState.ball) {
            ctx.fillStyle = 'pink';
            ctx.fillRect(gameState.ball.x, gameState.ball.y, 20, 20);
        }

        ctx.save();
        ctx.restore();

    }

    webSocket.onopen = (event) => console.log("Connecting to server");
    webSocket.onmessage = (event) => receiveCommand(event.data)
    webSocket.onclose = (event) => console.log(`Channel closed ${event}`);
    webSocket.onerror = (event) => console.log(`Channel error ${event}`);
    window.onbeforeunload = (event) => sendCommand({type: "close"})
    window.onunload = (event) => sendCommand({type: "close"})
    window.onkeydown = move

}