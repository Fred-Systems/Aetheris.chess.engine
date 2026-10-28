#!/usr/bin/env python3
"""Run the exact AETHNNUE v1 inference math on three static FEN sanity positions.

This format is a 768-feature absolute-square MLP, NOT Stockfish HalfKP/HalfKA.
Training and runtime both use ((color * 6 + piece_type) * 64 + square), with
white=0, black=1, piece order P/N/B/R/Q/K, a1=0 and h8=63. No perspective
rank flip is applied at inference: training's augmentation mirrors the board
and swaps colors before extracting these same absolute-square features.
"""
import math
import struct
import sys
from pathlib import Path

MAGIC = b"AETHNNUE"
FEATURES = 768
CASES = [
    ("start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 0),
    ("white_queen_missing", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", -1),
    ("black_queen_missing", "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 1),
]
PIECE_INDEX = {"p": 0, "n": 1, "b": 2, "r": 3, "q": 4, "k": 5}


def active_features(fen):
    placement = fen.split()[0]
    active = []
    ranks = placement.split("/")
    if len(ranks) != 8:
        raise ValueError("FEN must contain eight ranks")
    for fen_rank, row in enumerate(ranks):
        rank = 7 - fen_rank
        file = 0
        for char in row:
            if char.isdigit():
                file += int(char)
                continue
            lower = char.lower()
            if lower not in PIECE_INDEX or file > 7:
                raise ValueError(f"Invalid FEN piece/rank: {char}")
            color = 0 if char.isupper() else 1
            square = rank * 8 + file
            active.append((color * 6 + PIECE_INDEX[lower]) * 64 + square)
            file += 1
        if file != 8:
            raise ValueError(f"FEN rank has {file} squares, expected 8")
    return active


def load_network(path):
    blob = Path(path).read_bytes()
    if len(blob) < 20 or blob[:8] != MAGIC:
        raise ValueError("Invalid AETHNNUE magic/header")
    version, features, hidden = struct.unpack_from("<iii", blob, 8)
    if version != 1 or features != FEATURES or hidden not in (128, 512):
        raise ValueError(f"Unsupported model header: version={version}, features={features}, hidden={hidden}")
    expected = 20 + (hidden * features + hidden * 2 + 1) * 4
    if len(blob) != expected:
        raise ValueError(f"Binary size mismatch: actual={len(blob)}, expected={expected}")
    offset = 20
    count = hidden * features
    w1 = struct.unpack_from(f"<{count}f", blob, offset)
    offset += count * 4
    b1 = struct.unpack_from(f"<{hidden}f", blob, offset)
    offset += hidden * 4
    w2 = struct.unpack_from(f"<{hidden}f", blob, offset)
    offset += hidden * 4
    (b2,) = struct.unpack_from("<f", blob, offset)
    all_values = (*b1, *w1, *w2, b2)
    if not all(math.isfinite(value) for value in all_values):
        raise ValueError("Network contains NaN or infinite values")
    return features, hidden, w1, b1, w2, b2


def evaluate(fen, network):
    features, hidden, w1, b1, w2, b2 = network
    active = active_features(fen)
    out = b2
    for h in range(hidden):
        base = h * features
        acc = b1[h]
        for index in active:
            acc += w1[base + index]
        out += w2[h] * max(0.0, acc)
    if not math.isfinite(out):
        raise ValueError("Inference returned a non-finite score")
    # The trainer target is white-perspective pawns. C++ multiplies by 100 and
    # flips sign only when Black is to move; all sanity FENs have White to move.
    return out, int(out * 100.0)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/weights.bin"
    network = load_network(path)
    print(f"NNUE_FORMAT=absolute-square-768 hidden={network[1]} bytes={Path(path).stat().st_size}")
    print("FEATURE_MAPPING=TRAINING_AND_CPP_MATCH; piece order=P,N,B,R,Q,K; square=a1..h8")
    results = {}
    for name, fen, _ in CASES:
        pawns, cp = evaluate(fen, network)
        results[name] = (pawns, cp)
        print(f"NNUE_SANITY {name}: raw_pawns={pawns:+.4f} raw_cp={cp:+d}")
    start_ok = abs(results["start"][1]) <= 40
    white_missing_ok = -1200 <= results["white_queen_missing"][1] <= -800
    black_missing_ok = 800 <= results["black_queen_missing"][1] <= 1200
    passed = start_ok and white_missing_ok and black_missing_ok
    print("NNUE_SANITY_RESULT=" + ("PASS" if passed else "WARNING"))
    if not passed:
        print("Expected start within +/-40cp, white queen missing -800..-1200cp, black queen missing +800..+1200cp.")
        print("A warning means the weights are poorly trained/miscalibrated; do not fix this by arbitrarily changing output scale.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
