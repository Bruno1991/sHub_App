#!/usr/bin/env python3
"""
tools/test_mbtiles_pipeline.py
Testes unitários para o pipeline de geração, validação e comparação de formatos MBTiles.
"""

import unittest
import os
import tempfile
import sqlite3
from mbtiles_pipeline import (
    MockTileSourceProvider,
    MbtilesPipeline,
    MbtilesValidator,
    evaluate_webp_vs_jpeg_compression
)

class TestMbtilesPipeline(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.mbtiles_file = os.path.join(self.temp_dir, "test_pkg.mbtiles")

    def tearDown(self):
        if os.path.exists(self.mbtiles_file):
            try:
                os.remove(self.mbtiles_file)
            except Exception:
                pass
        try:
            os.rmdir(self.temp_dir)
        except Exception:
            pass

    def test_mock_provider_generates_valid_images(self):
        """Valida que o provedor sintético gera imagens válidas em JPG e WebP."""
        for fmt in ["jpg", "webp"]:
            provider = MockTileSourceProvider(fmt, quality=80)
            data = provider.get_tile(10, 333, 542)
            self.assertIsNotNone(data)
            self.assertTrue(len(data) > 500)

    def test_pipeline_build_and_validation(self):
        """Testa o fluxo completo: inicialização, gravação com TMS estrito e validação."""
        pipeline = MbtilesPipeline(self.mbtiles_file)
        pipeline.init_database(
            name="Test Cacoal Unit",
            format_ext="jpg",
            min_zoom=10,
            max_zoom=12,
            bounds="-61.5,-11.5,-61.4,-11.4",
            attribution="sGis Test Suite"
        )

        tiles_to_pack = {
            (10, 333, 542),
            (10, 334, 542),
            (11, 666, 1084),
            (12, 1332, 2168)
        }
        provider = MockTileSourceProvider("jpg", quality=75)
        res = pipeline.build_package(provider, tiles_to_pack, batch_size=2)
        self.assertEqual(res["written"], 4)
        self.assertEqual(res["failed"], 0)

        # Testa retomada: se rodar de novo, 0 tiles devem ser reescritos
        res_resume = pipeline.build_package(provider, tiles_to_pack)
        self.assertEqual(res_resume["already_present"], 4)
        self.assertEqual(res_resume["written"], 0)

        # Validador
        validator = MbtilesValidator(self.mbtiles_file)
        val_report = validator.validate(sample_size=10)
        self.assertTrue(val_report["exists"])
        self.assertTrue(val_report["sqlite_integrity"])
        self.assertEqual(val_report["tile_count"], 4)
        self.assertEqual(val_report["corrupt_tiles"], 0)
        self.assertEqual(val_report["metadata"]["name"], "Test Cacoal Unit")
        self.assertEqual(val_report["metadata"]["format"], "jpg")
        self.assertEqual(len(val_report["errors"]), 0)

    def test_webp_vs_jpeg_evaluation(self):
        """Testa a avaliação comparativa entre WebP e JPEG."""
        eval_result = evaluate_webp_vs_jpeg_compression(quality=78, samples=3)
        self.assertTrue("webp_size_reduction_percent" in eval_result)
        self.assertTrue(eval_result["avg_jpeg_bytes"] > 0)
        self.assertTrue(eval_result["avg_webp_bytes"] > 0)
        self.assertTrue(eval_result["avg_jpeg_decode_ms"] >= 0)
        print(f"\n[Bench WebP vs JPEG]: Redução WebP = {eval_result['webp_size_reduction_percent']}% | Decode JPEG = {eval_result['avg_jpeg_decode_ms']}ms | Decode WebP = {eval_result['avg_webp_decode_ms']}ms")

if __name__ == "__main__":
    unittest.main()

