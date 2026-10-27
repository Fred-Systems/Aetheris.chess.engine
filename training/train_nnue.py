import json, os, random, struct
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader, random_split

FEATURES = 768
HIDDEN = int(os.environ.get("AETHERIS_HIDDEN", "512"))
EPOCHS = int(os.environ.get("AETHERIS_EPOCHS", "20"))
BATCH_SIZE = int(os.environ.get("AETHERIS_BATCH_SIZE", "512"))

def features(fen):
    import chess
    board = chess.Board(fen)
    x = [0.0] * FEATURES
    for sq in range(64):
        piece = board.piece_at(sq)
        if piece:
            color = 0 if piece.color == chess.WHITE else 1
            ptype = piece.piece_type - 1
            x[(color * 6 + ptype) * 64 + sq] = 1.0
    return x

class PosDataset(Dataset):
    def __init__(self, path):
        self.rows = []
        with open(path) as f:
            for line in f:
                o = json.loads(line)
                self.rows.append((o["fen"], int(o["score"])))
    def __len__(self):
        return len(self.rows)
    def __getitem__(self, i):
        fen, score = self.rows[i]
        # Mirror and swap colors half the time; a white-perspective teacher score changes sign.
        if random.random() < 0.5:
            import chess
            fen = chess.Board(fen).mirror().fen()
            score = -score
        x = torch.tensor(features(fen), dtype=torch.float32)
        # Keep forced-mate outliers from overwhelming ordinary centipawn positions.
        y = torch.tensor(max(-1500, min(1500, score)) / 100.0, dtype=torch.float32)
        return x, y

class NNUE(nn.Module):
    def __init__(self):
        super().__init__()
        self.fc1 = nn.Linear(FEATURES, HIDDEN)
        self.fc2 = nn.Linear(HIDDEN, 1)
    def forward(self, x):
        return self.fc2(torch.relu(self.fc1(x))).squeeze(-1)

def export_weights(sd, path="weights.bin"):
    features_count, hidden = sd["fc1.weight"].shape
    with open(path, "wb") as f:
        f.write(b"AETHNNUE")
        f.write(struct.pack("iii", 1, features_count, hidden))
        for w in sd["fc1.weight"].contiguous().view(-1).tolist():
            f.write(struct.pack("f", w))
        for b in sd["fc1.bias"].tolist():
            f.write(struct.pack("f", b))
        for w in sd["fc2.weight"].contiguous().view(-1).tolist():
            f.write(struct.pack("f", w))
        f.write(struct.pack("f", sd["fc2.bias"].item()))

def main():
    torch.manual_seed(42)
    ds = PosDataset("positions.jsonl")
    if len(ds) < 100:
        raise ValueError("Need at least 100 positions for a useful train/validation split")
    n_val = max(1, int(len(ds) * 0.1))
    train_ds, val_ds = random_split(ds, [len(ds) - n_val, n_val])
    train_dl = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)
    val_dl = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False)

    model = NNUE()
    opt = optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-5)
    lossfn = nn.SmoothL1Loss()
    best_val = float("inf")
    best_state = None

    for epoch in range(EPOCHS):
        model.train()
        train_total = 0.0
        for x, y in train_dl:
            opt.zero_grad()
            pred = model(x)
            loss = lossfn(pred, y)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            train_total += loss.item()

        model.eval()
        val_total = 0.0
        with torch.no_grad():
            for x, y in val_dl:
                val_total += lossfn(model(x), y).item()
        train_loss = train_total / max(1, len(train_dl))
        val_loss = val_total / max(1, len(val_dl))
        print(f"epoch {epoch + 1}/{EPOCHS}: train_loss={train_loss:.5f} val_loss={val_loss:.5f}")
        if val_loss < best_val:
            best_val = val_loss
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state is None:
        raise RuntimeError("Training did not produce a valid checkpoint")
    torch.save(best_state, "nnue.pt")
    export_weights(best_state)
    print(f"exported weights.bin with {FEATURES} features and {HIDDEN} hidden units; best validation loss={best_val:.5f}")

if __name__ == "__main__":
    main()
