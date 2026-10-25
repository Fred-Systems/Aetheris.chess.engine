import json, torch, torch.nn as nn, torch.optim as optim
from torch.utils.data import Dataset, DataLoader

FEATURES = 768
HIDDEN = 128

def features(fen):
    import chess
    board = chess.Board(fen)
    x = [0.0]*FEATURES
    for sq in range(64):
        piece = board.piece_at(sq)
        if piece:
            color = 0 if piece.color == chess.WHITE else 1
            ptype = piece.piece_type - 1  # 0..5
            idx = (color*6 + ptype)*64 + sq
            x[idx] = 1.0
    return x

class PosDataset(Dataset):
    def __init__(self, path):
        self.rows = []
        with open(path) as f:
            for line in f:
                o = json.loads(line)
                self.rows.append((o["fen"], o["score"]))
    def __len__(self): return len(self.rows)
    def __getitem__(self, i):
        fen, score = self.rows[i]
        x = torch.tensor(features(fen), dtype=torch.float32)
        y = torch.tensor(score/100.0, dtype=torch.float32)
        return x, y

class NNUE(nn.Module):
    def __init__(self):
        super().__init__()
        self.fc1 = nn.Linear(FEATURES, HIDDEN)
        self.fc2 = nn.Linear(HIDDEN, 1)
    def forward(self, x):
        h = torch.relu(self.fc1(x))
        return self.fc2(h).squeeze(-1)

def main():
    ds = PosDataset("positions.jsonl")
    dl = DataLoader(ds, batch_size=256, shuffle=True)
    model = NNUE()
    opt = optim.Adam(model.parameters(), lr=1e-3)
    lossfn = nn.MSELoss()
    for epoch in range(5):
        tot = 0
        for x, y in dl:
            opt.zero_grad()
            pred = model(x)
            loss = lossfn(pred, y)
            loss.backward()
            opt.step()
            tot += loss.item()
        print("epoch", epoch, tot/len(dl))
    torch.save(model.state_dict(), "nnue.pt")
    # export
    sd = model.state_dict()
    import struct
    with open("weights.bin", "wb") as f:
        f.write(b"AETHNNUE")
        f.write(struct.pack("iii", 1, FEATURES, HIDDEN))
        for w in sd["fc1.weight"].flatten().tolist():
            f.write(struct.pack("f", w))
        for b in sd["fc1.bias"].tolist():
            f.write(struct.pack("f", b))
        for w in sd["fc2.weight"].flatten().tolist():
            f.write(struct.pack("f", w))
        f.write(struct.pack("f", sd["fc2.bias"].item()))
    print("exported weights.bin")

if __name__ == "__main__":
    main()