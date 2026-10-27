#include "bitboard.h"
#include <vector>
#include <algorithm>
#include <chrono>
#include <iostream>
#include <android/log.h>

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
static std::vector<TTEntry2> tt2(1 << 20);

static int maxDepth = 6;
static long long nodeCount = 0;
static std::chrono::steady_clock::time_point startTime;
static int timeLimitMs = 0;
static bool stopSearch = false;

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

static int alphaBeta(Board& b, int depth, int alpha, int beta, bool pvNode) {
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
    if (depth <= 0) return quiescence(b, alpha, beta, 0, 0);
    std::vector<Move> moves;
    genMoves(b, moves, false);
    if (moves.empty()) {
        return inCheck(b, b.side) ? -MATE : 0;
    }
    // Put the transposition-table move first, then winning captures/promotions.
    const Move ttMove = (tt2[idx].key == key) ? tt2[idx].best : 0;
    auto orderingScore = [&](Move m) {
        if (m == ttMove) return 1000000;
        const int from = m & 63, to = (m >> 6) & 63, promo = (m >> 12) & 7;
        const int attacker = pieceAt(b, from, b.side);
        int victim = pieceAt(b, to, b.side ^ 1);
        if (victim < 0 && attacker == PAWN && to == b.epSquare) victim = PAWN;
        int score = promo ? 80000 + ORDER_VALUE[promo] : 0;
        if (victim >= 0) score += 50000 + ORDER_VALUE[victim] * 10 - ORDER_VALUE[std::max(0, std::min(5, attacker))];
        return score;
    };
    std::stable_sort(moves.begin(), moves.end(), [&](Move a, Move bmove) {
        return orderingScore(a) > orderingScore(bmove);
    });
    Move bestMove = 0;
    int bestScore = -INF;
    int origAlpha = alpha;
    for (size_t i = 0; i < moves.size(); i++) {
        Undo u;
        makeMove(b, moves[i], u);
        int score;
        if (i == 0 || !pvNode) {
            score = -alphaBeta(b, depth - 1, -beta, -alpha, pvNode && i == 0);
        } else {
            score = -alphaBeta(b, depth - 1, -alpha - 1, -alpha, false);
            if (score > alpha && score < beta) {
                score = -alphaBeta(b, depth - 1, -beta, -alpha, true);
            }
        }
        unmakeMove(b, moves[i], u);
        if (stopSearch) return 0;
        if (score > bestScore) { bestScore = score; bestMove = moves[i]; }
        if (score > alpha) alpha = score;
        if (alpha >= beta) break;
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

} // namespace aetheris