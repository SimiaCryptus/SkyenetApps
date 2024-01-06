document.addEventListener('DOMContentLoaded', (event) => {
    // Initialize the chess board
    initChessBoard(document.getElementById('chessBoard'));
});

const chessBoardData = [
    ['rd', 'nd', 'bd', 'qd', 'kd', 'bd', 'nd', 'rd'],
    ['pd', 'pd', 'pd', 'pd', 'pd', 'pd', 'pd', 'pd'],
    ['--', '--', '--', '--', '--', '--', '--', '--'],
    ['--', '--', '--', '--', '--', '--', '--', '--'],
    ['--', '--', '--', '--', '--', '--', '--', '--'],
    ['--', '--', '--', '--', '--', '--', '--', '--'],
    ['pl', 'pl', 'pl', 'pl', 'pl', 'pl', 'pl', 'pl'],
    ['rl', 'nl', 'bl', 'ql', 'kl', 'bl', 'nl', 'rl']
];

function initChessBoard(board) {
    board.innerHTML = ''; // Clear the board

    for (let i = 0; i < 8; i++) {
        for (let j = 0; j < 8; j++) {
            let square = document.createElement('div');
            square.className = 'chessSquare';
            board.appendChild(square);

            let pieceCode = chessBoardData[i][j];
            if (pieceCode !== '--') {
                let piece = document.createElement('div');
                piece.className = 'chessPiece';
                piece.id = pieceCode + '_' + i + '_' + j; // Unique ID for each piece
                piece.style.backgroundImage = getPieceImage(pieceCode);
                square.appendChild(piece);
                makeDraggable(piece);
            }
        }
    }
}

function getPieceImage(pieceCode) {
    return 'url("Chess_'+pieceCode+'t45.svg")';
}

function makeDraggable(element) {
    let selectedPiece = null, x = 0, y = 0;

    element.onmousedown = function(e) {
        // Get the mouse cursor position at startup
        x = e.clientX;
        y = e.clientY;
        selectedPiece = e.target;

        // Call a function whenever the cursor moves
        document.onmousemove = dragPiece;
        document.onmouseup = stopDragPiece;
    };

    function dragPiece(e) {
        e.preventDefault(); // Prevent text selection

        // Calculate the new cursor position
        let dx = e.clientX - x;
        let dy = e.clientY - y;
        x = e.clientX;
        y = e.clientY;

        // Set the element's new position
        selectedPiece.style.left = (selectedPiece.offsetLeft + dx) + "px";
        selectedPiece.style.top = (selectedPiece.offsetTop + dy) + "px";
    }

    function stopDragPiece() {
        // Snap the piece to the nearest grid
        snapToGrid(selectedPiece);

        // Stop moving when mouse button is released
        document.onmouseup = null;
        document.onmousemove = null;
    }
}

function snapToGrid(piece) {
    // Assuming each square is 45px x 45px
    let gridSize = 45;

    // Get the offset of the chess board
    let boardRect = document.getElementById('chessBoard').getBoundingClientRect();

    // Get the piece position relative to the chess board
    let pieceRect = piece.getBoundingClientRect();

    // Calculate the nearest grid position
    let x = Math.round((pieceRect.left - boardRect.left) / gridSize) * gridSize;
    let y = Math.round((pieceRect.top - boardRect.top) / gridSize) * gridSize;

    // Set the piece position to the nearest grid
    piece.style.left = x + 'px';
    piece.style.top = y + 'px';
}
