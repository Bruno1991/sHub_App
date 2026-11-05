#!/usr/bin/env python3
"""
tools/test_slippy_math.py
Suíte de testes unitários para a biblioteca matemática de Slippy Map e interseção espacial.
"""

import unittest
import math
from shapely.geometry import Point, LineString, Polygon, box
from slippy_map_math import (
    lonlat_to_tile,
    tile_to_lonlat_nw,
    tile_bounds_lonlat,
    tile_bounds_3857,
    tile_polygon_3857,
    tile_polygon_lonlat,
    xyz_to_tms,
    tms_to_xyz,
    envelope_to_tiles,
    geometry_to_tiles,
    clamp_latitude,
    MAX_LATITUDE,
    MIN_LATITUDE
)

class TestSlippyMapMath(unittest.TestCase):

    def test_zoom_0_world_coverage(self):
        """No zoom 0, o mundo inteiro é exatamente um único tile (0, 0, 0)."""
        x, y = lonlat_to_tile(0.0, 0.0, 0)
        self.assertEqual((x, y), (0, 0))
        
        # Teste nos quatro cantos do mundo
        self.assertEqual(lonlat_to_tile(-179.9, 85.0, 0), (0, 0))
        self.assertEqual(lonlat_to_tile(179.9, -85.0, 0), (0, 0))

    def test_zoom_limits_and_negatives(self):
        """Testa zooms de 0 a 22 e tratamento correto de coordenadas no hemisfério Sul e Oeste."""
        # Coordenada em Cacoal - RO (América do Sul / Rondônia)
        lat = -11.435697
        lon = -61.437460

        for z in range(0, 23):
            x, y = lonlat_to_tile(lon, lat, z)
            max_index = (1 << z) - 1
            self.assertTrue(0 <= x <= max_index, f"x={x} fora dos limites em z={z}")
            self.assertTrue(0 <= y <= max_index, f"y={y} fora dos limites em z={z}")

    def test_latitude_clamping(self):
        """Garante que latitudes polares fora de ±85.051129° são tratadas sem lançar exceções."""
        self.assertAlmostEqual(clamp_latitude(90.0), MAX_LATITUDE)
        self.assertAlmostEqual(clamp_latitude(-90.0), MIN_LATITUDE)
        
        x_pole, y_pole = lonlat_to_tile(0.0, 89.9, 5)
        self.assertEqual(y_pole, 0)

    def test_tms_xyz_roundtrip(self):
        """Valida que a conversão bidirecional XYZ <-> TMS é estritamente reversível."""
        for z in [0, 5, 10, 15, 18]:
            max_val = (1 << z) - 1
            for y in [0, 1, max_val // 2, max_val]:
                tms_y = xyz_to_tms(y, z)
                back_y = tms_to_xyz(tms_y, z)
                self.assertEqual(y, back_y, f"Falha de roundtrip TMS em z={z}, y={y}")

    def test_tile_bounds_and_polygons(self):
        """Testa coerência geométrica dos limites e polígonos dos tiles."""
        min_lon, min_lat, max_lon, max_lat = tile_bounds_lonlat(10, 333, 542)
        self.assertTrue(min_lon < max_lon)
        self.assertTrue(min_lat < max_lat)
        
        poly = tile_polygon_lonlat(10, 333, 542)
        self.assertTrue(poly.is_valid)
        self.assertAlmostEqual(poly.area, (max_lon - min_lon) * (max_lat - min_lat), places=6)

    def test_geometry_to_tiles_corridor(self):
        """
        Testa linha de transmissão (corredor).
        Garante que apenas os quadrantes interceptados pela linha são retornados,
        e não a bounding box completa que desperdiçaria espaço.
        """
        # Linha diagonal cruzando Cacoal a Pimenta Bueno
        line = LineString([(-61.44, -11.43), (-61.30, -11.55), (-61.20, -11.67)])
        
        # Bounding box conteria centenas de tiles; geometry_to_tiles deve retornar apenas os que tocam a linha
        env_tiles = list(envelope_to_tiles(*line.bounds, zoom=14))
        geom_tiles = geometry_to_tiles(line, zoom=14)
        
        self.assertTrue(len(geom_tiles) > 0)
        self.assertTrue(len(geom_tiles) < len(env_tiles), "A filtragem geométrica deve ser mais estrita que o envelope retangular")
        
        # Todo tile retornado deve interceptar a linha
        for z, x, y in geom_tiles:
            self.assertTrue(tile_polygon_lonlat(z, x, y).intersects(line))

    def test_geometry_to_tiles_empty(self):
        """Testa que geometrias vazias ou None retornam conjunto vazio."""
        self.assertEqual(geometry_to_tiles(None, 14), set())
        self.assertEqual(geometry_to_tiles(Polygon(), 14), set())

    def test_geometry_to_tiles_polygon(self):
        """Testa subestação modelada como polígono."""
        substation = box(-61.438, -11.436, -61.436, -11.434)
        tiles_z18 = geometry_to_tiles(substation, zoom=18)
        self.assertTrue(len(tiles_z18) >= 1)
        for z, x, y in tiles_z18:
            self.assertTrue(tile_polygon_lonlat(z, x, y).intersects(substation))

if __name__ == "__main__":
    unittest.main()

