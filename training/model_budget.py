#!/usr/bin/env python3
"""Estimador conservador de tamanho para a futura arquitetura autoral MKAI v2.

Este utilitário estima o tamanho do arquivo de pesos. Ele NÃO treina, quantiza
ou executa um modelo e não afirma que o runtime Android já suporta esses formatos.
"""
from __future__ import annotations

import argparse
import math
from dataclasses import dataclass

MAX_STORAGE_BYTES = 5_000_000_000
HEADER_BYTES = 4096


@dataclass(frozen=True)
class ModelConfig:
    dim: int
    layers: int
    heads: int
    kv_heads: int
    ffn: int
    vocab: int
    context: int
    group_size: int = 64

    def validate(self) -> None:
        if not 16 <= self.dim <= 8192:
            raise ValueError("dim precisa ficar entre 16 e 8192")
        if not 1 <= self.layers <= 128:
            raise ValueError("layers precisa ficar entre 1 e 128")
        if not 1 <= self.heads <= 128 or self.dim % self.heads:
            raise ValueError("heads precisa dividir dim exatamente")
        if not 1 <= self.kv_heads <= self.heads or self.heads % self.kv_heads:
            raise ValueError("kv_heads precisa dividir heads exatamente")
        if not self.dim <= self.ffn <= 32768:
            raise ValueError("ffn precisa ficar entre dim e 32768")
        if not 256 <= self.vocab <= 131072:
            raise ValueError("vocab precisa ficar entre 256 e 131072")
        if not 16 <= self.context <= 131072:
            raise ValueError("context precisa ficar entre 16 e 131072")
        if not 16 <= self.group_size <= 1024:
            raise ValueError("group_size precisa ficar entre 16 e 1024")

    @property
    def parameter_count(self) -> int:
        self.validate()
        head_dim = self.dim // self.heads
        kv_dim = self.kv_heads * head_dim
        # Embedding + LM head sem compartilhamento; RoPE não adiciona pesos.
        embeddings = 2 * self.vocab * self.dim
        per_layer = (
            2 * self.dim
            + 2 * self.dim * self.dim  # query + output
            + 2 * kv_dim * self.dim    # key + value (GQA/MQA)
            + 3 * self.ffn * self.dim  # SwiGLU gate + up + down
        )
        return embeddings + self.layers * per_layer + self.dim

    def weight_file_bytes(self, dtype: str) -> int:
        params = self.parameter_count
        groups = math.ceil(params / self.group_size)
        if dtype == "q4":
            payload = math.ceil(params / 2) + groups * 2  # int4 + escala FP16 por grupo
        elif dtype == "q8":
            payload = params + groups * 2  # int8 + escala FP16 por grupo
        elif dtype == "f16":
            payload = params * 2
        elif dtype == "f32":
            payload = params * 4
        else:
            raise ValueError("dtype deve ser q4, q8, f16 ou f32")
        return HEADER_BYTES + payload

    def kv_cache_bytes(self, context: int, batch: int = 1, bytes_per_value: int = 2) -> int:
        self.validate()
        if not 1 <= context <= self.context:
            raise ValueError("contexto solicitado precisa estar entre 1 e o contexto do modelo")
        if not 1 <= batch <= 64:
            raise ValueError("batch precisa ficar entre 1 e 64")
        if bytes_per_value not in (2, 4):
            raise ValueError("cache KV aceita estimativa FP16 (2) ou FP32 (4)")
        head_dim = self.dim // self.heads
        return 2 * self.layers * context * self.kv_heads * head_dim * batch * bytes_per_value


def human_bytes(value: int) -> str:
    units = ("B", "KB", "MB", "GB", "TB")
    amount = float(value)
    for unit in units:
        if amount < 1000 or unit == units[-1]:
            return f"{amount:.2f} {unit}"
        amount /= 1000
    return f"{amount:.2f} TB"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dim", type=int, required=True)
    parser.add_argument("--layers", type=int, required=True)
    parser.add_argument("--heads", type=int, required=True)
    parser.add_argument("--kv-heads", type=int, required=True)
    parser.add_argument("--ffn", type=int, required=True)
    parser.add_argument("--vocab", type=int, required=True)
    parser.add_argument("--context", type=int, required=True)
    parser.add_argument("--dtype", choices=("q4", "q8", "f16", "f32"), default="q4")
    parser.add_argument("--kv-context", type=int, default=None)
    parser.add_argument("--batch", type=int, default=1)
    args = parser.parse_args()

    config = ModelConfig(
        dim=args.dim, layers=args.layers, heads=args.heads,
        kv_heads=args.kv_heads, ffn=args.ffn, vocab=args.vocab,
        context=args.context,
    )
    try:
        config.validate()
        file_bytes = config.weight_file_bytes(args.dtype)
        kv_bytes = config.kv_cache_bytes(args.kv_context or args.context, args.batch)
    except ValueError as error:
        parser.error(str(error))

    print(f"Parâmetros: {config.parameter_count:,}")
    print(f"Formato estimado: {args.dtype}")
    print(f"Arquivo de pesos: {human_bytes(file_bytes)} ({file_bytes:,} bytes)")
    print(f"Cache KV estimado (FP16): {human_bytes(kv_bytes)}")
    print(f"Limite de armazenamento: {human_bytes(MAX_STORAGE_BYTES)}")
    if file_bytes > MAX_STORAGE_BYTES:
        print("RESULTADO: excede o limite de 5 GB.")
        return 2
    print(f"Espaço restante: {human_bytes(MAX_STORAGE_BYTES - file_bytes)}")
    print("RESULTADO: cabe no limite estimado de armazenamento.")
    print("ATENÇÃO: isso não comprova compatibilidade com o runtime nem RAM suficiente.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
