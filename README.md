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


Release verification note: this commit also includes custom trained-weight import on Android and the browser, multi-PV analysis controls, corrected evaluation perspective, and the downloadable standalone UCI engine package.


Release note: the final build also optimizes 512-unit NNUE inference and balances analysis time across all candidate lines; the release includes a stable Aetheris.apk download alias and the standalone Android UCI package.


Final release workflow hardening: runtime dependency discovery now tolerates NDK installations outside ANDROID_HOME/ndk.


The NNUE import workflow now reports a warning if the imported network's starting-position evaluation is implausibly large, rather than equating file-load success with playing strength.
