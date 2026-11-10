#!/usr/bin/env python3
"""Treina do zero e exporta pesos MKAI v1 compatíveis com o motor C++ Android."""
from __future__ import annotations

import argparse
import random
import struct
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

MAGIC = b"MKAI0001"
HEADER = struct.Struct("<8s8I")
VOCAB_SIZE = 256


class RMSNorm(nn.Module):
    def __init__(self, dim: int):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(dim))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        scale = torch.rsqrt(x.pow(2).mean(dim=-1, keepdim=True) + 1.0e-5)
        return x * scale * self.weight


class MikaelLayer(nn.Module):
    def __init__(self, dim: int, heads: int, ffn: int):
        super().__init__()
        self.norm_att = RMSNorm(dim)
        self.q = nn.Linear(dim, dim, bias=False)
        self.k = nn.Linear(dim, dim, bias=False)
        self.v = nn.Linear(dim, dim, bias=False)
        self.o = nn.Linear(dim, dim, bias=False)
        self.norm_ffn = RMSNorm(dim)
        self.gate = nn.Linear(dim, ffn, bias=False)
        self.up = nn.Linear(dim, ffn, bias=False)
        self.down = nn.Linear(ffn, dim, bias=False)
        self.heads = heads

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        batch, seq, dim = x.shape
        head_dim = dim // self.heads
        h = self.norm_att(x)
        q = self.q(h).view(batch, seq, self.heads, head_dim).transpose(1, 2)
        k = self.k(h).view(batch, seq, self.heads, head_dim).transpose(1, 2)
        v = self.v(h).view(batch, seq, self.heads, head_dim).transpose(1, 2)
        att = F.scaled_dot_product_attention(q, k, v, is_causal=True)
        att = att.transpose(1, 2).contiguous().view(batch, seq, dim)
        x = x + self.o(att)
        h = self.norm_ffn(x)
        return x + self.down(F.silu(self.gate(h)) * self.up(h))


class MikaelTransformer(nn.Module):
    def __init__(self, dim: int, layers: int, heads: int, ffn: int, max_seq: int):
        super().__init__()
        self.token_embedding = nn.Embedding(VOCAB_SIZE, dim)
        self.position_embedding = nn.Embedding(max_seq, dim)
        self.layers = nn.ModuleList([MikaelLayer(dim, heads, ffn) for _ in range(layers)])
        self.final_norm = RMSNorm(dim)
        self.lm_head = nn.Linear(dim, VOCAB_SIZE, bias=False)
        self.max_seq = max_seq
        self.apply(self._init_weights)

    @staticmethod
    def _init_weights(module: nn.Module) -> None:
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def forward(self, tokens: torch.Tensor) -> torch.Tensor:
        _, seq = tokens.shape
        if seq > self.max_seq:
            raise ValueError("Sequência maior que max_seq.")
        positions = torch.arange(seq, device=tokens.device)
        x = self.token_embedding(tokens) + self.position_embedding(positions)[None, :, :]
        for layer in self.layers:
            x = layer(x)
        return self.lm_head(self.final_norm(x))


def tensors_in_export_order(model: MikaelTransformer):
    yield model.token_embedding.weight
    yield model.position_embedding.weight
    for layer in model.layers:
        yield layer.norm_att.weight
        yield layer.q.weight
        yield layer.k.weight
        yield layer.v.weight
        yield layer.o.weight
        yield layer.norm_ffn.weight
        yield layer.gate.weight
        yield layer.up.weight
        yield layer.down.weight
    yield model.final_norm.weight
    yield model.lm_head.weight


def export_mkai(model: MikaelTransformer, output: Path, dim: int, layers: int,
                heads: int, ffn: int, max_seq: int) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    header = HEADER.pack(MAGIC, 1, dim, layers, heads, ffn, VOCAB_SIZE, max_seq, 0)
    temporary = output.with_suffix(output.suffix + ".tmp")
    with temporary.open("wb") as stream:
        stream.write(header)
        for tensor in tensors_in_export_order(model):
            array = tensor.detach().cpu().contiguous().numpy().astype("<f4", copy=False)
            if not np.isfinite(array).all():
                raise ValueError("Peso não finito detectado durante exportação.")
            stream.write(array.tobytes(order="C"))
        stream.flush()
        import os
        os.fsync(stream.fileno())
    temporary.replace(output)
    validate_export(output, dim, layers, heads, ffn, max_seq)


def expected_float_count(dim: int, layers: int, ffn: int, max_seq: int) -> int:
    return (VOCAB_SIZE * dim + max_seq * dim +
            layers * (2 * dim + 4 * dim * dim + 3 * ffn * dim) +
            dim + VOCAB_SIZE * dim)


def validate_export(path: Path, dim: int, layers: int, heads: int,
                    ffn: int, max_seq: int) -> None:
    data_size = expected_float_count(dim, layers, ffn, max_seq) * 4
    if path.stat().st_size != HEADER.size + data_size:
        raise ValueError("Tamanho MKAI exportado não corresponde às dimensões.")
    with path.open("rb") as stream:
        fields = HEADER.unpack(stream.read(HEADER.size))
    expected = (MAGIC, 1, dim, layers, heads, ffn, VOCAB_SIZE, max_seq, 0)
    if fields != expected:
        raise ValueError("Cabeçalho MKAI exportado inválido.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--output", default=Path("artifacts/mikael-base.mkai"), type=Path)
    parser.add_argument("--steps", default=2500, type=int)
    parser.add_argument("--batch-size", default=16, type=int)
    parser.add_argument("--seq-len", default=512, type=int)
    parser.add_argument("--dim", default=64, type=int)
    parser.add_argument("--layers", default=2, type=int)
    parser.add_argument("--heads", default=4, type=int)
    parser.add_argument("--ffn", default=128, type=int)
    parser.add_argument("--learning-rate", default=3e-4, type=float)
    parser.add_argument("--seed", default=8367, type=int)
    args = parser.parse_args()

    if not args.corpus.is_file():
        raise SystemExit(f"Corpus não encontrado: {args.corpus}. Adicione um corpus licenciado no repositório.")
    raw = args.corpus.read_bytes()
    if len(raw) < max(4096, args.seq_len * 4):
        raise SystemExit("Corpus muito pequeno: forneça pelo menos 4 KiB de texto UTF-8.")
    if len(raw) > 100 * 1024 * 1024:
        raise SystemExit("Corpus excede o limite de 100 MiB deste treinamento.")
    raw.decode("utf-8", errors="strict")
    if not (16 <= args.dim <= 1024 and 1 <= args.layers <= 16 and
            1 <= args.heads <= 32 and args.dim % args.heads == 0 and
            args.dim <= args.ffn <= 4096 and 16 <= args.seq_len <= 8192 and
            1 <= args.steps <= 100000 and 1 <= args.batch_size <= 128):
        raise SystemExit("Configuração fora dos limites seguros do formato MKAI v1.")

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.set_num_threads(max(1, min(4, torch.get_num_threads())))
    device = torch.device("cpu")
    tokens = torch.from_numpy(np.frombuffer(raw, dtype=np.uint8).astype(np.int64).copy()).to(device)
    split = max(args.seq_len + 1, int(len(tokens) * 0.95))
    split = min(split, len(tokens) - args.seq_len - 2)
    train_data, val_data = tokens[:split], tokens[split:]
    if len(val_data) < args.seq_len + 1:
        raise SystemExit("Corpus insuficiente para separar treino e validação.")

    def batch(source: torch.Tensor):
        starts = torch.randint(0, len(source) - args.seq_len - 1, (args.batch_size,))
        x = torch.stack([source[int(i):int(i) + args.seq_len] for i in starts])
        y = torch.stack([source[int(i) + 1:int(i) + args.seq_len + 1] for i in starts])
        return x, y

    model = MikaelTransformer(args.dim, args.layers, args.heads, args.ffn, args.seq_len).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=0.01)
    model.train()
    for step in range(1, args.steps + 1):
        x, y = batch(train_data)
        optimizer.zero_grad(set_to_none=True)
        loss = F.cross_entropy(model(x).reshape(-1, VOCAB_SIZE), y.reshape(-1))
        if not torch.isfinite(loss):
            raise SystemExit(f"Loss inválida no passo {step}; treinamento interrompido.")
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if step == 1 or step % 100 == 0 or step == args.steps:
            model.eval()
            with torch.no_grad():
                vx, vy = batch(val_data)
                val_loss = F.cross_entropy(model(vx).reshape(-1, VOCAB_SIZE), vy.reshape(-1))
            print(f"step={step}/{args.steps} train_loss={loss.item():.4f} val_loss={val_loss.item():.4f}", flush=True)
            if not torch.isfinite(val_loss):
                raise SystemExit("Loss de validação inválida.")
            model.train()

    model.eval()
    export_mkai(model, args.output, args.dim, args.layers, args.heads, args.ffn, args.seq_len)
    print(f"Modelo MKAI v1 exportado: {args.output} ({args.output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
