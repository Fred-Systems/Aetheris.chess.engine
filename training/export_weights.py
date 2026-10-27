# Re-export a saved PyTorch checkpoint to Aetheris NNUE binary format.
# Dimensions are read from the checkpoint so 128- and 512-unit networks are both supported.
import torch
import struct

sd = torch.load("nnue.pt", map_location="cpu")
features, hidden = sd["fc1.weight"].shape
if features != 768 or sd["fc1.bias"].numel() != hidden:
    raise ValueError(f"Unsupported fc1 shape: {tuple(sd['fc1.weight'].shape)}")
if tuple(sd["fc2.weight"].shape) != (1, hidden) or sd["fc2.bias"].numel() != 1:
    raise ValueError("fc2 must be Linear(hidden, 1)")

with open("weights.bin", "wb") as f:
    f.write(b"AETHNNUE")
    f.write(struct.pack("iii", 1, features, hidden))
    for w in sd["fc1.weight"].contiguous().view(-1).tolist():
        f.write(struct.pack("f", w))
    for b in sd["fc1.bias"].tolist():
        f.write(struct.pack("f", b))
    for w in sd["fc2.weight"].contiguous().view(-1).tolist():
        f.write(struct.pack("f", w))
    f.write(struct.pack("f", sd["fc2.bias"].item()))
print(f"exported weights.bin: {features} features, {hidden} hidden units")
