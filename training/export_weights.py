# Re-export a saved PyTorch checkpoint to Aetheris NNUE binary format
import torch, struct
sd = torch.load("nnue.pt", map_location="cpu")
FEATURES, HIDDEN = 768, 128
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
print("done")