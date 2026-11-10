import struct
import tempfile
import unittest
from pathlib import Path

import torch

from train_mikai import (
    HEADER, MAGIC, MikaelTransformer, VOCAB_SIZE,
    expected_float_count, export_mkai, validate_export,
)


class TrainerTests(unittest.TestCase):
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
