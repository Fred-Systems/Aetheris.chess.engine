#include "bitboard.h"
#include <vector>
#include <algorithm>
#include <chrono>
#include <iostream>
#include <sstream>
#include <string>
#include <cstring>
#ifdef __ANDROID__
#include <android/log.h>
#else
#define ANDROID_LOG_INFO 0
#define __android_log_print(...) ((void)0)
#endif

namespace aetheris {

struct MoveList { Move moves[256]; int count=0; };
static void addMove(MoveList& ml, int from, int to, int promo=0){ if (ml.count >= 256 || from < 0 || from >= 64 || to < 0 || to >= 64) return; ml.moves[ml.count++] = (from) | (to<<6) | (promo<<12); }

static Bitboard knightAttacks(int sq){ Bitboard b=0; int f=sq&7,r=sq>>3; int df[8]={-2,-1,1,2,2,1,-1,-2}; int dr[8]={1,2,2,1,-1,-2,-2,-1}; for(int i=0;i<8;i++){int nf=f+df[i],nr=r+dr[i]; if(nf>=0&&nf<8&&nr>=0&&nr<8) b|=(1ULL<<(nr*8+nf));} return b;}
static Bitboard kingAttacks(int sq){ Bitboard b=0; int f=sq&7,r=sq>>3; for(int df=-1;df<=1;df++) for(int dr=-1;dr<=1;dr++){ if(df==0&&dr==0) continue; int nf=f+df,nr=r+dr; if(nf>=0&&nf<8&&nr>=0&&nr<8) b|=(1ULL<<(nr*8+nf));} return b;}
static Bitboard bishopAttacks(int sq, Bitboard occ){ Bitboard b=0; int f=sq&7,r=sq>>3; int dirs[4][2]={{1,1},{1,-1},{-1,1},{-1,-1}}; for(auto&d:dirs){ int cf=f+d[0],cr=r+d[1]; while(cf>=0&&cf<8&&cr>=0&&cr<8){ int s=cr*8+cf; b|=(1ULL<<s); if(occ&(1ULL<<s)) break; cf+=d[0]; cr+=d[1]; } } return b;}
static Bitboard rookAttacks(int sq, Bitboard occ){ Bitboard b=0; int f=sq&7,r=sq>>3; int dirs[4][2]={{1,0},{-1,0},{0,1},{0,-1}}; for(auto&d:dirs){ int cf=f+d[0],cr=r+d[1]; while(cf>=0&&cf<8&&cr>=0&&cr<8){ int s=cr*8+cf; b|=(1ULL<<s); if(occ&(1ULL<<s)) break; cf+=d[0]; cr+=d[1]; } } return b;}

static const int INF = 1000000;
static const int MATE = 90000;
static const int ORDER_VALUE[6] = {100, 320, 335, 500, 950, 20000};

struct Undo {
    int captured;
    int epSquare;
    int castleRights;
    int halfmove;
    int movedPiece;
    int promo;
};

static int pieceAt(const Board& b, int sq, int side) {
    for (int p = 0; p < 6; p++) if (b.pieces[side][p] & (1ULL << sq)) return p;
    return -1;
}

static void rebuildOccupied(Board& b) {
    b.occupied[0] = b.occupied[1] = 0;
    for (int p = 0; p < 6; p++) { b.occupied[0] |= b.pieces[0][p]; b.occupied[1] |= b.pieces[1][p]; }
    b.all = b.occupied[0] | b.occupied[1];
}

static void makeMove(Board& b, Move m, Undo& u) {
    int from = m & 63;
    int to = (m >> 6) & 63;
    int promo = (m >> 12) & 7;
    int us = b.side;
    int pc = pieceAt(b, from, us);
    u.movedPiece = pc;
    u.promo = promo;
    u.epSquare = b.epSquare;
    u.castleRights = b.castleRights;
    u.halfmove = b.halfmove;
    u.captured = -1;
    int them = us ^ 1;
    for (int p = 0; p < 6; p++) {
        if (b.pieces[them][p] & (1ULL << to)) { u.captured = p; b.pieces[them][p] &= ~(1ULL << to); break; }
    }
    if (pc == PAWN && to == b.epSquare && u.captured == -1) {
        int capSq = (us == 0) ? to - 8 : to + 8;
        for (int p = 0; p < 6; p++) {
            if (b.pieces[them][p] & (1ULL << capSq)) { b.pieces[them][p] &= ~(1ULL << capSq); u.captured = PAWN; break; }
        }
    }
    b.pieces[us][pc] &= ~(1ULL << from);
    int placed = pc;
    if (promo) placed = promo;
    b.pieces[us][placed] |= (1ULL << to);
    b.epSquare = -1;
    if (pc == PAWN && (from / 8 == 1 || from / 8 == 6) && (to / 8 == 3 || to / 8 == 4)) {
        b.epSquare = (from + to) / 2;
    }
    if (pc == KING && (to - from == 2 || from - to == 2)) {
        if (us == 0) {
            if (to == 6) { b.pieces[0][ROOK] &= ~(1ULL << 7); b.pieces[0][ROOK] |= (1ULL << 5); }
            if (to == 2) { b.pieces[0][ROOK] &= ~(1ULL << 0); b.pieces[0][ROOK] |= (1ULL << 3); }
        } else {
            if (to == 62) { b.pieces[1][ROOK] &= ~(1ULL << 63); b.pieces[1][ROOK] |= (1ULL << 61); }
            if (to == 58) { b.pieces[1][ROOK] &= ~(1ULL << 56); b.pieces[1][ROOK] |= (1ULL << 59); }
        }
    }
    if (pc == KING) {
        if (us == 0) b.castleRights &= ~3;
        else b.castleRights &= ~12;
    }
    if (pc == ROOK) {
        if (us == 0) {
            if (from == 0) b.castleRights &= ~2;
            if (from == 7) b.castleRights &= ~1;
        } else {
            if (from == 56) b.castleRights &= ~8;
            if (from == 63) b.castleRights &= ~4;
        }
    }
    if (u.captured == ROOK) {
        if (us == 0) {
            if (to == 56) b.castleRights &= ~8;
            if (to == 63) b.castleRights &= ~4;
        } else {
            if (to == 0) b.castleRights &= ~2;
            if (to == 7) b.castleRights &= ~1;
        }
    }
    b.halfmove++;
    if (pc == PAWN || u.captured != -1) b.halfmove = 0;
    b.side ^= 1;
    rebuildOccupied(b);
}

static void unmakeMove(Board& b, Move m, const Undo& u) {
    b.side ^= 1;
    int us = b.side;
    int from = m & 63;
    int to = (m >> 6) & 63;
    int promo = (m >> 12) & 7;
    int placed = promo ? promo : u.movedPiece;
    b.pieces[us][placed] &= ~(1ULL << to);
    b.pieces[us][u.movedPiece] |= (1ULL << from);
    int them = us ^ 1;
    if (u.captured != -1) {
        if (u.movedPiece == PAWN && to == u.epSquare && u.captured == PAWN) {
            int capSq = (us == 0) ? to - 8 : to + 8;
            b.pieces[them][PAWN] |= (1ULL << capSq);
        } else {
            b.pieces[them][u.captured] |= (1ULL << to);
        }
    }
    if (u.movedPiece == KING && (to - from == 2 || from - to == 2)) {
        if (us == 0) {
            if (to == 6) { b.pieces[0][ROOK] &= ~(1ULL << 5); b.pieces[0][ROOK] |= (1ULL << 7); }
            if (to == 2) { b.pieces[0][ROOK] &= ~(1ULL << 3); b.pieces[0][ROOK] |= (1ULL << 0); }
        } else {
            if (to == 62) { b.pieces[1][ROOK] &= ~(1ULL << 61); b.pieces[1][ROOK] |= (1ULL << 63); }
            if (to == 58) { b.pieces[1][ROOK] &= ~(1ULL << 59); b.pieces[1][ROOK] |= (1ULL << 56); }
        }
    }
    b.epSquare = u.epSquare;
    b.castleRights = u.castleRights;
    b.halfmove = u.halfmove;
    rebuildOccupied(b);
}

static bool squareAttacked(const Board& b, int sq, int bySide) {
    const int f = sq & 7, r = sq >> 3;
    // Pawn attacks are directional and independent of occupancy.
    const Bitboard pawns = b.pieces[bySide][PAWN];
    const int pawnSourceRank = r + (bySide == WHITE ? -1 : 1);
    if (pawnSourceRank >= 0 && pawnSourceRank < 8) {
        for (int df : {-1, 1}) {
            const int sf = f + df;
            if (sf >= 0 && sf < 8 && (pawns & (1ULL << (pawnSourceRank * 8 + sf)))) return true;
        }
    }
    if (knightAttacks(sq) & b.pieces[bySide][KNIGHT]) return true;
    if (kingAttacks(sq) & b.pieces[bySide][KING]) return true;
    if (bishopAttacks(sq, b.all) & (b.pieces[bySide][BISHOP] | b.pieces[bySide][QUEEN])) return true;
    if (rookAttacks(sq, b.all) & (b.pieces[bySide][ROOK] | b.pieces[bySide][QUEEN])) return true;
    return false;
}

static bool inCheck(const Board& b, int side) {
    Bitboard king = b.pieces[side][KING];
    if (!king) return true; // malformed positions are treated as unsafe
    return squareAttacked(b, __builtin_ctzll(king), side ^ 1);
}

static void genMoves(Board& b, std::vector<Move>& ml, bool capturesOnly) {
    MoveList tmp; tmp.count = 0;
    int us = b.side, them = us ^ 1;
    Bitboard friends = b.occupied[us];
    Bitboard enemies = b.occupied[them] & ~b.pieces[them][KING];
    Bitboard pawns = b.pieces[us][PAWN];
    int pawnDir = (us == 0) ? 8 : -8;
    int startRank = (us == 0) ? 1 : 6;
    Bitboard bb = pawns;
    while (bb) {
        int sq = __builtin_ctzll(bb); bb &= bb - 1;
        int to = sq + pawnDir;
        if (to >= 0 && to < 64 && !(b.all & (1ULL << to))) {
            if (!capturesOnly) {
                int r = to >> 3;
                if ((us == 0 && r == 7) || (us == 1 && r == 0)) {
                    for (int pr = KNIGHT; pr <= QUEEN; pr++) addMove(tmp, sq, to, pr);
                } else {
                    addMove(tmp, sq, to);
                    if ((sq >> 3) == startRank) {
                        int to2 = sq + 2 * pawnDir;
                        if (!(b.all & (1ULL << to2))) addMove(tmp, sq, to2);
                    }
                }
            }
        }
    }
    bb = pawns;
    while (bb) {
        int sq = __builtin_ctzll(bb); bb &= bb - 1;
        int f = sq & 7, r = sq >> 3;
        int dr = (us == 0) ? 1 : -1;
        for (int df = -1; df <= 1; df += 2) {
            int nf = f + df, nr = r + dr;
            if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
            int to = nr * 8 + nf;
            bool isEnemy = (enemies & (1ULL << to)) != 0;
            bool isEp = (b.epSquare == to);
            if (capturesOnly && !isEnemy && !isEp) continue;
            if (!isEnemy && !isEp) continue;
            int prRank = (us == 0) ? 7 : 0;
            if (nr == prRank) {
                for (int pr = KNIGHT; pr <= QUEEN; pr++) addMove(tmp, sq, to, pr);
            } else {
                addMove(tmp, sq, to);
            }
        }
    }
    // knights
    bb = b.pieces[us][KNIGHT];
    while (bb) {
        int sq = __builtin_ctzll(bb); bb &= bb - 1;
        Bitboard att = knightAttacks(sq);
        Bitboard targets = capturesOnly ? (att & enemies) : (att & ~friends & ~b.pieces[them][KING]);
        Bitboard t = targets;
        while (t) { int to = __builtin_ctzll(t); t &= t - 1; addMove(tmp, sq, to); }
    }
    // king
    {
        Bitboard king = b.pieces[us][KING];
        if (king) {
            int sq = __builtin_ctzll(king);
            Bitboard att = kingAttacks(sq);
            Bitboard targets = capturesOnly ? (att & enemies) : (att & ~friends & ~b.pieces[them][KING]);
            Bitboard t = targets;
            while (t) { int to = __builtin_ctzll(t); t &= t - 1; addMove(tmp, sq, to); }
        }
    }
    auto slide = [&](Piece pc, bool bishop, bool rook) {
        Bitboard pcs = b.pieces[us][pc];
        Bitboard bb2 = pcs;
        while (bb2) {
            int sq = __builtin_ctzll(bb2); bb2 &= bb2 - 1;
            Bitboard att = 0;
            if (bishop) att |= bishopAttacks(sq, b.all);
            if (rook) att |= rookAttacks(sq, b.all);
            Bitboard targets = capturesOnly ? (att & enemies) : (att & ~friends & ~b.pieces[them][KING]);
            Bitboard t = targets;
            while (t) { int to = __builtin_ctzll(t); t &= t - 1; addMove(tmp, sq, to); }
        }
    };
    slide(BISHOP, true, false);
    slide(ROOK, false, true);
    slide(QUEEN, true, true);
    if (!capturesOnly) {
        const int rights = b.castleRights;
        // Castling is legal only when the king is not in check, crosses no attacked
        // square, the path is empty, and the corresponding rook is still present.
        if (us == WHITE) {
            if ((rights & 1) && (b.pieces[WHITE][KING] & (1ULL << 4)) &&
                (b.pieces[WHITE][ROOK] & (1ULL << 7)) && !(b.all & 0x60ULL) &&
                !squareAttacked(b, 4, BLACK) && !squareAttacked(b, 5, BLACK) && !squareAttacked(b, 6, BLACK))
                addMove(tmp, 4, 6);
            if ((rights & 2) && (b.pieces[WHITE][KING] & (1ULL << 4)) &&
                (b.pieces[WHITE][ROOK] & (1ULL << 0)) && !(b.all & 0x0EULL) &&
                !squareAttacked(b, 4, BLACK) && !squareAttacked(b, 3, BLACK) && !squareAttacked(b, 2, BLACK))
                addMove(tmp, 4, 2);
        } else {
            if ((rights & 4) && (b.pieces[BLACK][KING] & (1ULL << 60)) &&
                (b.pieces[BLACK][ROOK] & (1ULL << 63)) && !(b.all & 0x6000000000000000ULL) &&
                !squareAttacked(b, 60, WHITE) && !squareAttacked(b, 61, WHITE) && !squareAttacked(b, 62, WHITE))
                addMove(tmp, 60, 62);
            if ((rights & 8) && (b.pieces[BLACK][KING] & (1ULL << 60)) &&
                (b.pieces[BLACK][ROOK] & (1ULL << 56)) && !(b.all & 0x0E00000000000000ULL) &&
                !squareAttacked(b, 60, WHITE) && !squareAttacked(b, 59, WHITE) && !squareAttacked(b, 58, WHITE))
                addMove(tmp, 60, 58);
        }
    }
    // Generate pseudo-legal moves above, then discard moves that leave our king attacked.
    // This is essential: searching illegal king moves or ignoring check evasions can make
    // the engine choose completely invalid moves and badly mis-evaluate tactical positions.
    for (int i = 0; i < tmp.count; i++) {
        const Move m = tmp.moves[i];
        Undo u;
        makeMove(b, m, u);
        const bool legal = !inCheck(b, us);
        unmakeMove(b, m, u);
        if (legal) ml.push_back(m);
    }
}

struct TTEntry2 { std::uint64_t key; int depth; int score; int flag; Move best; };
// Keep the transposition table modest on memory-constrained Android devices.
static std::vector<TTEntry2> tt2(1 << 18);

void clearSearchTables() {
    std::fill(tt2.begin(), tt2.end(), TTEntry2{});
    std::memset(historyHeuristic, 0, sizeof(historyHeuristic));
    std::memset(killerMoves, 0, sizeof(killerMoves));
}

static int maxDepth = 6;
static long long nodeCount = 0;
static std::chrono::steady_clock::time_point startTime;
static int timeLimitMs = 0;
static bool stopSearch = false;

// History and killer heuristics learn which quiet moves tend to cause cutoffs.
// They are intentionally bounded and reset with the transposition table.
static int historyHeuristic[2][64][64] = {};
static Move killerMoves[128][2] = {};

static bool timeUp() {
    if (timeLimitMs <= 0) return false;
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now - startTime).count() >= timeLimitMs;
}

static std::uint64_t positionKey(const Board& b) {
    std::uint64_t key = 0x9e3779b97f4a7c15ULL;
    for (int c = 0; c < 2; c++) {
        for (int p = 0; p < 6; p++) {
            key ^= b.pieces[c][p] + 0x9e3779b97f4a7c15ULL + (key << 6) + (key >> 2);
        }
    }
    key ^= static_cast<std::uint64_t>(b.side) * 0xbf58476d1ce4e5b9ULL;
    key ^= static_cast<std::uint64_t>(b.castleRights) * 0x94d049bb133111ebULL;
    key ^= static_cast<std::uint64_t>(b.epSquare + 1) * 0xd6e8feb86659fd93ULL;
    return key;
}

static int quiescence(Board& b, int alpha, int beta, int ply, int qply) {
    if (timeUp()) { stopSearch = true; return 0; }
    ++nodeCount;
    const bool checked = inCheck(b, b.side);
    if (qply >= 16) {
        if (checked) {
            std::vector<Move> evasions;
            genMoves(b, evasions, false);
            if (evasions.empty()) return -MATE + ply;
        }
        return evaluate(b);
    }

    int stand = checked ? -INF : evaluate(b);
    if (!checked) {
        if (stand >= beta) return stand;
        if (stand > alpha) alpha = stand;
    }

    std::vector<Move> moves;
    genMoves(b, moves, !checked);
    if (checked && moves.empty()) return -MATE + ply;
    for (Move m : moves) {
        Undo u;
        makeMove(b, m, u);
        const int score = -quiescence(b, -beta, -alpha, ply + 1, qply + 1);
        unmakeMove(b, m, u);
        if (stopSearch) return 0;
        if (score >= beta) return score;
        if (score > alpha) alpha = score;
    }
    return alpha;
}

static int alphaBeta(Board& b, int depth, int alpha, int beta, bool pvNode, int ply = 0) {
    if (timeUp()) { stopSearch = true; return 0; }
    nodeCount++;
    // Quiescence can encounter a quiet check-evasion. Bound that extension to avoid
    // an unbounded chain of checks while still detecting immediate mate at the horizon.
    if (depth < 0) {
        if (inCheck(b, b.side)) {
            std::vector<Move> evasions;
            genMoves(b, evasions, false);
            if (evasions.empty()) return -MATE;
        }
        return evaluate(b);
    }
    std::uint64_t key = positionKey(b);
    int idx = key & (tt2.size() - 1);
    if (tt2[idx].key == key && tt2[idx].depth >= depth) {
        int s = tt2[idx].score;
        if (tt2[idx].flag == 0) return s;
        if (tt2[idx].flag == 1 && s > alpha) alpha = s;
        if (tt2[idx].flag == 2 && s < beta) beta = s;
        if (alpha >= beta) return s;
    }
    // Null-move pruning: skip a turn in sufficiently rich, non-check positions
    // to quickly prove many branches cannot beat beta. Disable it in low material
    // positions where zugzwang is common.
    if (depth <= 0) return quiescence(b, alpha, beta, 0, 0);

    // Guarded null-move pruning: require a plausible static fail-high and
    // sufficient non-pawn material, avoiding the worst zugzwang cases.
    if (depth >= 3 && !pvNode && !inCheck(b, b.side) && evaluate(b) >= beta) {
        int nonPawnMaterial = 0;
        for (int p = KNIGHT; p <= QUEEN; ++p)
            nonPawnMaterial += __builtin_popcountll(b.pieces[b.side][p]);
        if (nonPawnMaterial >= 2) {
            const int savedEp = b.epSquare;
            const int savedHalfmove = b.halfmove;
            b.side ^= 1;
            b.epSquare = -1;
            ++b.halfmove;
            const int nullScore = -alphaBeta(b, depth - 3, -beta, -beta + 1, false);
            b.side ^= 1;
            b.epSquare = savedEp;
            b.halfmove = savedHalfmove;
            if (stopSearch) return 0;
            if (nullScore >= beta) return nullScore;
        }
    }
    std::vector<Move> moves;
    genMoves(b, moves, false);
    if (moves.empty()) {
        return inCheck(b, b.side) ? -MATE : 0;
    }
    // Put the transposition-table move first, then winning captures/promotions.
    const Move ttMove = (tt2[idx].key == key) ? tt2[idx].best : 0;
    auto orderingScore = [&](Move m) {
        if (m == ttMove && m != 0) return 2000000;
        const int from = m & 63, to = (m >> 6) & 63, promo = (m >> 12) & 7;
        const int attacker = pieceAt(b, from, b.side);
        int victim = pieceAt(b, to, b.side ^ 1);
        if (victim < 0 && attacker == PAWN && to == b.epSquare) victim = PAWN;
        if (promo) return 900000 + ORDER_VALUE[std::max(0, std::min(5, promo))];
        if (victim >= 0) return 800000 + ORDER_VALUE[victim] * 16 - ORDER_VALUE[std::max(0, std::min(5, attacker))];
        if (ply < 128 && killerMoves[ply][0] == m) return 700000;
        if (ply < 128 && killerMoves[ply][1] == m) return 690000;
        return historyHeuristic[b.side][from][to];
    };
    std::stable_sort(moves.begin(), moves.end(), [&](Move a, Move bmove) {
        return orderingScore(a) > orderingScore(bmove);
    });
    Move bestMove = 0;
    int bestScore = -INF;
    int origAlpha = alpha;
    for (size_t i = 0; i < moves.size(); i++) {
        const Move move = moves[i];
        const int from = move & 63, to = (move >> 6) & 63;
        const int attacker = pieceAt(b, from, b.side);
        int victim = pieceAt(b, to, b.side ^ 1);
        if (victim < 0 && attacker == PAWN && to == b.epSquare) victim = PAWN;
        const bool quiet = victim < 0 && ((move >> 12) & 7) == 0;
        Undo u;
        makeMove(b, move, u);
        int score;
        const bool reduce = i >= 4 && depth >= 3 && quiet && !inCheck(b, b.side ^ 1) && !inCheck(b, b.side);
        if (i == 0 || !pvNode) {
            score = -alphaBeta(b, depth - 1, -beta, -alpha, pvNode && i == 0, ply + 1);
        } else {
            // Late-move reductions save time on quiet moves that history says are unlikely.
            int reducedDepth = depth - 1;
            if (reduce && historyHeuristic[b.side ^ 1][from][to] < 4000) reducedDepth = std::max(0, reducedDepth - 1);
            score = -alphaBeta(b, reducedDepth, -alpha - 1, -alpha, false, ply + 1);
            if (score > alpha && reducedDepth < depth - 1)
                score = -alphaBeta(b, depth - 1, -alpha - 1, -alpha, false, ply + 1);
            if (score > alpha && score < beta)
                score = -alphaBeta(b, depth - 1, -beta, -alpha, true, ply + 1);
        }
        unmakeMove(b, move, u);
        if (stopSearch) return 0;
        if (score > bestScore) { bestScore = score; bestMove = move; }
        if (score > alpha) alpha = score;
        if (alpha >= beta) {
            if (quiet && ply < 128) {
                if (killerMoves[ply][0] != move) {
                    killerMoves[ply][1] = killerMoves[ply][0];
                    killerMoves[ply][0] = move;
                }
                int& h = historyHeuristic[b.side][from][to];
                h = std::min(20000, h + depth * depth * 16);
            }
            break;
        }
        if (quiet) {
            int& h = historyHeuristic[b.side][from][to];
            h = std::max(-20000, h - depth);
        }
    }
    TTEntry2 e;
    e.key = key; e.depth = depth; e.score = bestScore; e.best = bestMove;
    if (bestScore <= origAlpha) e.flag = 2;
    else if (bestScore >= beta) e.flag = 1;
    else e.flag = 0;
    tt2[idx] = e;
    return bestScore;
}

Move searchBestMove(Board& b, int depth, int timeMs) {
    // Keep externally supplied values within safe, tested bounds.
    depth = std::max(1, std::min(depth, 20));
    timeMs = std::max(100, std::min(timeMs, 30000));
    maxDepth = depth;
    timeLimitMs = timeMs;
    __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", "SEARCH_BEGIN depth=%d timeMs=%d", depth, timeMs);
    startTime = std::chrono::steady_clock::now();
    stopSearch = false;
    nodeCount = 0;
    Move best = 0;
    for (int d = 1; d <= depth; d++) {
        int s = alphaBeta(b, d, -INF, INF, true);
        std::uint64_t key = positionKey(b);
        int idx = key & (tt2.size() - 1);
        if (tt2[idx].key == key && tt2[idx].best) best = tt2[idx].best;
        if (stopSearch) break;
        std::cout << "info depth " << d << " score cp " << s << " nodes " << nodeCount << std::endl;
        __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", "SEARCH_DEPTH_DONE depth=%d score=%d nodes=%lld", d, s, nodeCount);
    }
    if (best == 0) {
        std::vector<Move> legal;
        genMoves(b, legal, false);
        if (!legal.empty()) best = legal[0];
    }
    return best;
}


int searchEvaluation(Board& b, int depth, int timeMs) {
    depth = std::max(1, std::min(depth, 10));
    timeMs = std::max(100, std::min(timeMs, 5000));
    timeLimitMs = timeMs;
    startTime = std::chrono::steady_clock::now();
    stopSearch = false;
    nodeCount = 0;
    int score = evaluate(b);
    for (int d = 1; d <= depth; ++d) {
        const int current = alphaBeta(b, d, -INF, INF, true);
        if (stopSearch) break;
        score = current;
    }
    return score;
}


static std::string moveToUci(Move m) {
    const int from = m & 63, to = (m >> 6) & 63, promo = (m >> 12) & 7;
    std::string s;
    s += static_cast<char>('a' + (from & 7));
    s += static_cast<char>('1' + (from >> 3));
    s += static_cast<char>('a' + (to & 7));
    s += static_cast<char>('1' + (to >> 3));
    if (promo == KNIGHT) s += 'n';
    else if (promo == BISHOP) s += 'b';
    else if (promo == ROOK) s += 'r';
    else if (promo == QUEEN) s += 'q';
    return s;
}

// Multi-PV analysis: search each legal root move, then follow the transposition
// table's best replies to return a genuine principal-variation line per candidate.
std::string searchTopLines(Board& b, int depth, int timeMs, int count) {
    depth = std::max(1, std::min(depth, 12));
    timeMs = std::max(100, std::min(timeMs, 15000));
    count = std::max(1, std::min(count, 5));
    std::vector<Move> legal;
    genMoves(b, legal, false);
    struct Candidate { Move move; int score; std::vector<Move> pv; };
    std::vector<Candidate> candidates;
    const int perMoveMs = std::max(100, timeMs / std::max(1, static_cast<int>(legal.size())));
    for (Move m : legal) {
        // Give every legal root move a bounded share of the analysis budget so
        // later moves are not omitted just because the first candidate was expensive.
        timeLimitMs = perMoveMs;
        startTime = std::chrono::steady_clock::now();
        stopSearch = false;
        nodeCount = 0;
        Undo rootUndo;
        makeMove(b, m, rootUndo);
        int score = 0;
        for (int d = 1; d <= depth; ++d) {
            const int candidateScore = -alphaBeta(b, d - 1, -INF, INF, true);
            if (stopSearch) break;
            score = candidateScore;
        }
        Candidate candidate{m, score, {m}};
        std::vector<Undo> tailUndo;
        for (int ply = 1; ply < depth; ++ply) {
            const std::uint64_t key = positionKey(b);
            const TTEntry2& entry = tt2[key & (tt2.size() - 1)];
            if (entry.key != key || entry.best == 0) break;
            std::vector<Move> continuations;
            genMoves(b, continuations, false);
            if (std::find(continuations.begin(), continuations.end(), entry.best) == continuations.end()) break;
            Undo u;
            makeMove(b, entry.best, u);
            tailUndo.push_back(u);
            candidate.pv.push_back(entry.best);
        }
        for (int j = static_cast<int>(tailUndo.size()) - 1; j >= 0; --j)
            unmakeMove(b, candidate.pv[static_cast<size_t>(j) + 1], tailUndo[static_cast<size_t>(j)]);
        unmakeMove(b, m, rootUndo);
        candidates.push_back(std::move(candidate));
    }
    std::stable_sort(candidates.begin(), candidates.end(), [](const Candidate& a, const Candidate& b2) {
        return a.score > b2.score;
    });
    std::ostringstream out;
    const int limit = std::min(count, static_cast<int>(candidates.size()));
    for (int i = 0; i < limit; ++i) {
        if (i) out << '\n';
        for (size_t j = 0; j < candidates[i].pv.size(); ++j) {
            if (j) out << ' ';
            out << moveToUci(candidates[i].pv[j]);
        }
        out << '|' << candidates[i].score;
    }
    return out.str();
}

} // namespace aetheris