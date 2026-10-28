# Aetheris NNUE format audit and calibration

## What is actually shipped

The app loads `app/src/main/assets/weights.bin` first. It is 1,576,984 bytes and its header describes version 1, 768 input features, and 512 hidden units. The separate legacy `app/src/main/assets/nnue/weights.bin` is 394,264 bytes and describes the older 128-hidden-unit model. The runtime cache now compares SHA-256 digests, so an older cached network is replaced when the bundled asset changes.

This AETHNNUE model is a one-hidden-layer absolute-square MLP. It is **not** a Stockfish HalfKP or HalfKA network. The runtime does not have a king-relative feature transformer, and adding a rank flip at inference would make it disagree with the existing training code.

## Mapping audit

The training and C++ runtime use the same index formula:

`index = (color * 6 + pieceType) * 64 + square`

- Color: White = 0, Black = 1.
- Piece order: pawn, knight, bishop, rook, queen, king.
- Square order: a1 = 0 through h8 = 63.
- The trainer writes the first layer as row-major `[hidden][features]`, then first-layer biases, second-layer weights, and the final bias. The C++ reader consumes that same sequence.
- The trainer's target is `white_score_cp / 100.0`, so its output is in pawn units. C++ multiplies by 100 to convert to centipawns and then negates only when Black is to move. That conversion is consistent.
- Training augmentation mirrors the board and swaps colors before extracting the same absolute-square features. The Android runtime should not apply an extra rank mirror.

## Measured result for the v1.0.16 bundled weights

The release CI ran the standard-library validator against the actual root asset and measured:

- Starting position: **-220 cp** (expected within ±40 cp).
- White missing its queen: **-2402 cp** (expected -800 to -1200 cp).
- Black missing its queen: **+1804 cp** (expected +800 to +1200 cp).

The feature mapping and file dimensions passed the structural checks, but the scores failed calibration badly. In particular, the starting position is materially losing for White and the two queen-loss scores are both oversized and asymmetric. This is evidence against the weights being a well-trained, calibrated evaluator—not evidence that the parser should reverse bytes or apply a random multiplier.

As a safety measure, the native sanity routine sets the NNUE calibration gate to false on this result. Hybrid and NNUE evaluation then fall back to Classical, and the UI blocks selecting Pure NNUE while the diagnostic says WARNING. The correct next step for NNUE strength is to train a new checkpoint on a much larger, representative, accurately labeled position set, export it with this exact format, and rerun these checks plus engine-match testing. The current release does not claim that the bundled network is strong.

## Why it can still play poorly

The format and index mapping are internally consistent, but that does not establish that the weights learned a useful evaluator. The current data generator defaults to 20,000 random-play positions labeled at depth 12. That is a small and noisy training set for a 512-unit model; it is not enough evidence to expect Stockfish-like strength. The model also has no explicit king-relative features, so its positional understanding is limited.

Do not "fix" a bad network by arbitrarily multiplying its output. First inspect the three raw, unsearched checks. The Python validator and native startup diagnostic report:

- Start position: expected within ±40 cp (roughly ±0.4 pawns).
- White missing its queen: expected approximately -800 to -1200 cp.
- Black missing its queen: expected approximately +800 to +1200 cp.

The Python validator runs in the release workflow and prints the actual model outputs. The Android native diagnostic also logs `NNUE_SANITY` entries to Logcat and reports PASS or WARNING in the settings status. A warning is a calibration/training failure, not a reason to silently pretend the network is strong.

## Current mode behavior

Classical is the default because it is the most dependable evaluation currently available. The UI's Hybrid mode blends classical and neural scores; it is not multi-threaded search despite the older internal `DUAL_CORE` enum name. The standalone NNUE mode remains experimental until the bundled weights pass calibration and meaningful engine-match benchmarks.
