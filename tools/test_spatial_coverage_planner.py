#!/usr/bin/env python3
"""
tools/test_spatial_coverage_planner.py
Testes unitários para o planejador de cobertura métrica espacial.
"""

import unittest
import math
from shapely.geometry import Point, LineString
from spatial_coverage_planner import (
    classify_layer_asset,
    SpatialCoveragePlanner,
    DEFAULT_BUFFER_CONFIG
)

class TestSpatialCoveragePlanner(unittest.TestCase):

    def test_layer_classification(self):
        """Testa mapeamento semântico de nomes de camadas para categorias de ativos."""
        self.assertEqual(classify_layer_asset("BASE ERO - Poste de Distribuição"), "pole")
        self.assertEqual(classify_layer_asset("BASE ERO - Transformador"), "transformer")
        self.assertEqual(classify_layer_asset("BASE ERO - SUBESTAÇÃO"), "substation")
        self.assertEqual(classify_layer_asset("BASE ERO - Vão MT"), "span_mt")
        self.assertEqual(classify_layer_asset("BASE ERO - Vão BT"), "span_bt")
        self.assertEqual(classify_layer_asset("BASE ERO - Chaves"), "switch")
        self.assertEqual(classify_layer_asset("BASE ERO - TORRE"), "tower")

    def test_metric_buffer_utm20_vs_degrees(self):
        """
        Valida que um buffer de 100m aplicado sobre um ponto em Rondônia
        resulta em uma extensão em graus de aproximadamente ~0.0009° (100m / 111.000m),
        e NÃO em 100 graus (o que cobriria o planeta inteiro se calculado em EPSG:4326).
        """
        planner = SpatialCoveragePlanner.__new__(SpatialCoveragePlanner)
        planner.buffer_config = DEFAULT_BUFFER_CONFIG.copy()

        # Ponto em Cacoal - RO em WGS84
        pt = Point(-61.437460, -11.435697)
        buffered_wgs84 = planner._buffer_geometry_metric(pt, srs_id=4326, buffer_meters=100.0)

        self.assertIsNotNone(buffered_wgs84)
        min_lon, min_lat, max_lon, max_lat = buffered_wgs84.bounds

        delta_lat = max_lat - min_lat
        delta_lon = max_lon - min_lon

        # 100 metros de raio = ~200 metros de diâmetro
        # 1 grau de latitude ~ 111 km = 111.000 m
        # 200m / 111.000m ~ 0.0018 graus
        self.assertTrue(0.0010 < delta_lat < 0.0025, f"delta_lat={delta_lat} fora do esperado para buffer de 100m")
        self.assertTrue(0.0010 < delta_lon < 0.0025, f"delta_lon={delta_lon} fora do esperado para buffer de 100m")

    def test_metric_buffer_already_utm(self):
        """Valida aplicação direta de buffer quando os dados já estão em UTM 20S (EPSG:31980)."""
        planner = SpatialCoveragePlanner.__new__(SpatialCoveragePlanner)
        planner.buffer_config = DEFAULT_BUFFER_CONFIG.copy()

        # Ponto em UTM 20S (coordenadas em metros)
        pt_utm = Point(569402.6, 8767709.64)
        buffered = planner._buffer_geometry_metric(pt_utm, srs_id=31980, buffer_meters=150.0)

        self.assertIsNotNone(buffered)
        # O resultado reprojetado para WGS84 deve estar em Rondônia (lat ~ -11.14, lon ~ -62.36)
        min_lon, min_lat, max_lon, max_lat = buffered.bounds
        self.assertTrue(-68.0 < min_lon < -59.0)
        self.assertTrue(-14.0 < min_lat < -8.0)

if __name__ == "__main__":
    unittest.main()

