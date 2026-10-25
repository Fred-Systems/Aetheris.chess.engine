#pragma once
#include "bitboard.h"
#include <string>
#include <vector>

namespace aetheris {

bool loadNNUE(const std::string& path);
int nnueEvaluate(const Board& b);
bool nnueLoaded();

} // namespace aetheris