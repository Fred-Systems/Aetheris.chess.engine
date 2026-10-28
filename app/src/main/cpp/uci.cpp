#include "bitboard.h"
#include <iostream>
#include <string>
#include <sstream>
#include <thread>

namespace aetheris {
extern Move searchBestMove(Board& b, int depth, int timeMs);
extern void setAggression(int a);
extern void setPositional(int v);
extern void setComplexity(int v);
extern void setSolidity(int v);
extern void setMaterialWeight(int m);
extern void setEngineMode(int m);
extern void setNNUEPath(const std::string& p);
}

int main() {
    using namespace aetheris;
    initBitboards();
    resetBoard();
#ifdef AETHERIS_DEFAULT_CLASSICAL
    setEngineMode(ENGINE_PURE_CLASSICAL);
#else
    setEngineMode(ENGINE_DUAL_CORE);
#endif
    setNNUEPath("weights.bin");
    std::string line;
    int depthLimit = 6;
    int nodesLimit = 0;
    while (std::getline(std::cin, line)) {
        std::istringstream iss(line);
        std::string cmd;
        iss >> cmd;
        if (cmd == "uci") {
#ifdef AETHERIS_DEFAULT_CLASSICAL
            std::cout << "id name Aetheris Classical" << std::endl;
#else
            std::cout << "id name Aetheris Hybrid" << std::endl;
#endif
            std::cout << "id author Fred-Systems" << std::endl;
            std::cout << "option name Aggression type spin default 50 min 0 max 100" << std::endl;
            std::cout << "option name Positional type spin default 50 min 0 max 100" << std::endl;
            std::cout << "option name Complexity type spin default 50 min 0 max 100" << std::endl;
            std::cout << "option name Solidity type spin default 50 min 0 max 100" << std::endl;
            std::cout << "option name MaterialWeight type spin default 50 min 0 max 100" << std::endl;
            std::cout << "option name UseOpeningBook type check default false" << std::endl;
            std::cout << "option name Depth type spin default 6 min 1 max 64" << std::endl;
#ifdef AETHERIS_DEFAULT_CLASSICAL
            std::cout << "option name EngineMode type combo default PureClassical var DualCore var PureNNUE var PureClassical" << std::endl;
#else
            std::cout << "option name EngineMode type combo default DualCore var DualCore var PureNNUE var PureClassical" << std::endl;
#endif
            std::cout << "option name NNUEFile type string default weights.bin" << std::endl;
            std::cout << "uciok" << std::endl;
        } else if (cmd == "isready") {
            std::cout << "readyok" << std::endl;
        } else if (cmd == "ucinewgame") {
            resetBoard();
        } else if (cmd == "setoption") {
            std::string name, value, token;
            // parse: setoption name X value Y
            iss >> token; // name
            std::string optName;
            iss >> optName;
            // read until value
            std::string rest;
            std::getline(iss, rest);
            // find value
            size_t pos = rest.find("value");
            if (pos != std::string::npos) {
                std::string val = rest.substr(pos + 6);
                // trim
                while (!val.empty() && (val.front() == ' ')) val.erase(val.begin());
                if (optName == "Aggression") setAggression(std::stoi(val));
                else if (optName == "Positional") setPositional(std::stoi(val));
                else if (optName == "Complexity") setComplexity(std::stoi(val));
                else if (optName == "Solidity") setSolidity(std::stoi(val));
                else if (optName == "MaterialWeight") setMaterialWeight(std::stoi(val));
                else if (optName == "Depth") depthLimit = std::stoi(val);
                else if (optName == "EngineMode") {
                    if (val == "PureClassical") setEngineMode(ENGINE_PURE_CLASSICAL);
                    else if (val == "PureNNUE") setEngineMode(ENGINE_PURE_NNUE);
                    else setEngineMode(ENGINE_DUAL_CORE);
                }
                else if (optName == "NNUEFile") setNNUEPath(val);
            }
        } else if (cmd == "position") {
            std::string token;
            iss >> token;
            if (token == "startpos") {
                resetBoard();
                iss >> token; // moves
                if (token == "moves") {
                    std::string mv;
                    while (iss >> mv) {
                        // apply move not fully; skip for now
                    }
                }
            } else if (token == "fen") {
                std::string fen;
                std::getline(iss, fen);
                setPosition(fen);
            }
        } else if (cmd == "go") {
            std::string token;
            int depth = depthLimit;
            int timeMs = 0;
            while (iss >> token) {
                if (token == "depth") { iss >> depth; }
                else if (token == "movetime") { iss >> timeMs; }
                else if (token == "wtime" || token == "btime") { int t; iss >> t; if (timeMs == 0) timeMs = t / 20; }
            }
            Board& b = getBoard();
            Move m = searchBestMove(b, depth, timeMs);
            int from = m & 63;
            int to = (m >> 6) & 63;
            int promo = (m >> 12) & 7;
            char promoChar = ' ';
            if (promo == KNIGHT) promoChar = 'n';
            if (promo == BISHOP) promoChar = 'b';
            if (promo == ROOK) promoChar = 'r';
            if (promo == QUEEN) promoChar = 'q';
            std::string uciMove = std::string(1, 'a' + (from % 8)) + std::to_string(from / 8 + 1)
                + std::string(1, 'a' + (to % 8)) + std::to_string(to / 8 + 1);
            if (promo) uciMove += promoChar;
            std::cout << "bestmove " << uciMove << std::endl;
        } else if (cmd == "quit") {
            break;
        }
    }
    return 0;
}
