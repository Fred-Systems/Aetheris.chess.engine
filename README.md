# Aetheris Chess

A polished, open-source chess app and engine for Android — with a matching web board.

[![Download APK](https://img.shields.io/badge/Download-APK-7C4DFF?style=for-the-badge)](https://github.com/Fred-Systems/Aetheris.chess.engine/releases/latest/download/Aetheris.apk)
[![Play in browser](https://img.shields.io/badge/Play-Web%20App-B9A8FF?style=for-the-badge)](https://fred-systems.github.io/Aetheris.chess.engine/)

## Features

- Lichess cburnett pieces plus 42 additional Lichess piece styles
- 25 board themes and selectable app density / board size
- Play vs engine or free analysis board
- Click-to-move and drag-and-drop pieces
- Legal move hints, check highlight, last-move highlight and smooth sliding animation
- White / Black / Random color selection on every new game
- Optional time controls (1, 3, 5, 10, 15, 30 minutes)
- Flip board, evaluation bar toggle, undo and force-engine-move
- Engine personality sliders, presets, depth and reset-to-default
- Native JNI engine for fast, reliable on-device play

## Web version

The website lives in [`docs/`](docs/) and is deployed with GitHub Pages. It uses the same board themes and Lichess piece sets, and its **Download APK** button always points at the newest release.

## Build

```bash
./gradlew assembleRelease
```

## Credits

Piece artwork comes from [Lichess](https://lichess.org) and its contributors (cburnett and friends), licensed under CC BY-SA 3.0 / GPL. See `app/src/main/assets/pieces/ATTRIBUTION.txt`.

Developed by [Fred-Systems](https://github.com/Fred-Systems).


## Aetheris 1.0.9 analysis and engine updates

The 1.0.9 production release includes support for 128- and 512-hidden-unit AETHNNUE v1 weight files, engine principal-variation analysis, selectable lines, move-by-move navigation and timed analysis playback. The website build compiles the same C++ engine to WebAssembly so browser moves and evaluations use Aetheris rather than a random-move placeholder. The release workflow also packages Android UCI executables for arm64-v8a and armeabi-v7a.
