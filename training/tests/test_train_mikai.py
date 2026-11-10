import struct
import tempfile
import unittest
import sys
import subprocess
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import torch

from train_mikai import (
    HEADER, MAGIC, MikaelTransformer, VOCAB_SIZE,
    expected_float_count, export_mkai, validate_export, split_train_validation,
)


class TrainerTests(unittest.TestCase):
    def test_minimum_corpus_has_valid_train_and_validation_batches(self):
        tokens = torch.arange(4096, dtype=torch.long)
        train_data, val_data = split_train_validation(tokens, 512)
        self.assertGreater(len(train_data), 513)
        self.assertGreater(len(val_data), 513)

    def test_too_short_corpus_is_rejected(self):
        tokens = torch.arange(1027, dtype=torch.long)
        with self.assertRaises(ValueError):
            split_train_validation(tokens, 512)

    def test_forward_returns_byte_vocabulary_logits(self):
        model = MikaelTransformer(dim=32, layers=1, heads=4, ffn=64, max_seq=32)
        tokens = torch.randint(0, VOCAB_SIZE, (2, 16))
        logits = model(tokens)
        self.assertEqual(tuple(logits.shape), (2, 16, VOCAB_SIZE))
        self.assertTrue(torch.isfinite(logits).all().item())

    def test_export_matches_native_mkai_v1_layout(self):
        config = (32, 1, 4, 64, 32)
        model = MikaelTransformer(
            dim=config[0], layers=config[1], heads=config[2],
            ffn=config[3], max_seq=config[4]
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.mkai"
            export_mkai(model, path, *config)
            validate_export(path, *config)
            self.assertEqual(path.stat().st_size, HEADER.size + expected_float_count(
                config[0], config[1], config[3], config[4]
            ) * 4)
            with path.open("rb") as stream:
                fields = HEADER.unpack(stream.read(HEADER.size))
            self.assertEqual(fields, (MAGIC, 1, 32, 1, 4, 64, 256, 32, 0))

    def test_one_step_training_produces_loadable_mkai_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            corpus = root / "corpus.txt"
            output = root / "trained.mkai"
            corpus.write_text(("programação em português\nfunção kotlin\n" * 200), encoding="utf-8")
            script = Path(__file__).resolve().parents[1] / "train_mikai.py"
            subprocess.run([
                sys.executable, str(script),
                "--corpus", str(corpus),
                "--output", str(output),
                "--steps", "1",
                "--batch-size", "1",
                "--seq-len", "16",
                "--dim", "16",
                "--layers", "1",
                "--heads", "1",
                "--ffn", "16",
            ], check=True, capture_output=True, text=True, timeout=120)
            self.assertTrue(output.is_file())
            validate_export(output, 16, 1, 1, 16, 16)

    def test_export_rejects_non_finite_weights(self):
        model = MikaelTransformer(dim=16, layers=1, heads=1, ffn=16, max_seq=16)
        with torch.no_grad():
            model.lm_head.weight[0, 0] = float("nan")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.mkai"
            with self.assertRaises(ValueError):
                export_mkai(model, path, 16, 1, 1, 16, 16)


if __name__ == "__main__":
    unittest.main()
