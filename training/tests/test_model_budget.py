import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model_budget import MAX_STORAGE_BYTES, ModelConfig


class ModelBudgetTests(unittest.TestCase):
    def config(self, **overrides):
        values = dict(dim=512, layers=12, heads=8, kv_heads=4,
                      ffn=1376, vocab=32000, context=4096)
        values.update(overrides)
        return ModelConfig(**values)

    def test_parameter_count_uses_grouped_query_attention(self):
        full_kv = self.config(kv_heads=8)
        grouped_kv = self.config(kv_heads=4)
        self.assertLess(grouped_kv.parameter_count, full_kv.parameter_count)

    def test_q4_file_is_smaller_than_q8_and_float_formats(self):
        cfg = self.config()
        q4 = cfg.weight_file_bytes("q4")
        self.assertLess(q4, cfg.weight_file_bytes("q8"))
        self.assertLess(cfg.weight_file_bytes("q8"), cfg.weight_file_bytes("f16"))
        self.assertLess(cfg.weight_file_bytes("f16"), cfg.weight_file_bytes("f32"))

    def test_estimator_includes_header_and_quantization_scales(self):
        cfg = self.config(dim=16, layers=1, heads=1, kv_heads=1,
                          ffn=16, vocab=256, context=16)
        params = cfg.parameter_count
        groups = (params + cfg.group_size - 1) // cfg.group_size
        self.assertEqual(cfg.weight_file_bytes("q4"), 4096 + (params + 1) // 2 + groups * 2)

    def test_kv_cache_scales_with_context_and_batch(self):
        cfg = self.config()
        one = cfg.kv_cache_bytes(1024, batch=1)
        two = cfg.kv_cache_bytes(2048, batch=1)
        four = cfg.kv_cache_bytes(1024, batch=4)
        self.assertEqual(two, one * 2)
        self.assertEqual(four, one * 4)

    def test_invalid_grouped_query_configuration_is_rejected(self):
        with self.assertRaises(ValueError):
            self.config(heads=8, kv_heads=3).validate()

    def test_storage_limit_is_explicit_five_decimal_gigabytes(self):
        self.assertEqual(MAX_STORAGE_BYTES, 5_000_000_000)

    def test_invalid_format_is_rejected(self):
        with self.assertRaises(ValueError):
            self.config().weight_file_bytes("q3")


if __name__ == "__main__":
    unittest.main()
