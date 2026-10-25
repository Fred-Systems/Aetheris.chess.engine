import chess
import chess.engine
import random
import json
import os

STOCKFISH = os.environ.get("STOCKFISH", "/usr/bin/stockfish")
OUT = "positions.jsonl"
N = 20000

def random_position():
    board = chess.Board()
    for _ in range(random.randint(0, 40)):
        moves = list(board.legal_moves)
        if not moves or board.is_game_over():
            break
        board.push(random.choice(moves))
    return board

def main():
    engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    engine.configure({"Threads": 2, "Hash": 64})
    with open(OUT, "w") as f:
        for i in range(N):
            board = random_position()
            if board.is_game_over():
                continue
            info = engine.analyse(board, chess.engine.Limit(depth=12))
            score = info["score"].white().score(mate_score=100000)
            if score is None:
                continue
            f.write(json.dumps({"fen": board.fen(), "score": score}) + "\n")
            if i % 1000 == 0:
                print(i, score)
    engine.quit()
    print("done", OUT)

if __name__ == "__main__":
    main()