#!/usr/bin/env python3
"""
tools/spatial_coverage_planner.py
Módulo de planejamento espacial de cobertura HD a partir de GeoPackage elétrico.

Responsabilidade Única:
Inspecionar geometrias vetoriais elétricas no GeoPackage, reprojetar com precisão métrica
(UTM Zonas 20S/21S), gerar buffers configuráveis por categoria de ativo, consolidar a cobertura
(`unary_union`) e mapear os quadrantes exatos Slippy Map para download/empacotamento sem redundâncias.
"""

import os
import sys
import math
import sqlite3
import argparse
from typing import Dict, List, Tuple, Set, Optional
from shapely import wkb, ops
from shapely.geometry import base, Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon
from pyproj import Transformer, CRS

from slippy_map_math import geometry_to_tiles, envelope_to_tiles

# Configurações padrão de buffer por categoria de ativo elétrico (em metros)
DEFAULT_BUFFER_CONFIG = {
    "substation": 400.0,
    "transformer": 150.0,
    "switch": 150.0,
    "regulator": 150.0,
    "capacitor": 150.0,
    "span_at": 200.0,
    "span_mt": 200.0,
    "span_bt": 150.0,
    "tower": 150.0,
    "pole": 100.0,
    "default": 100.0
}

# Transformadores de coordenadas pré-compilados
# EPSG:31980 = SIRGAS 2000 / UTM zone 20S
# EPSG:31981 = SIRGAS 2000 / UTM zone 21S
# EPSG:4326  = WGS 84 (Graus Decimais)
# EPSG:4674  = SIRGAS 2000 (Graus Decimais)
# EPSG:3857  = Web Mercator
TRANSFORMER_4326_TO_UTM20 = Transformer.from_crs("EPSG:4326", "EPSG:31980", always_xy=True)
TRANSFORMER_4326_TO_UTM21 = Transformer.from_crs("EPSG:4326", "EPSG:31981", always_xy=True)
TRANSFORMER_4674_TO_UTM20 = Transformer.from_crs("EPSG:4674", "EPSG:31980", always_xy=True)
TRANSFORMER_4674_TO_UTM21 = Transformer.from_crs("EPSG:4674", "EPSG:31981", always_xy=True)

TRANSFORMER_UTM20_TO_4326 = Transformer.from_crs("EPSG:31980", "EPSG:4326", always_xy=True)
TRANSFORMER_UTM21_TO_4326 = Transformer.from_crs("EPSG:31981", "EPSG:4326", always_xy=True)
TRANSFORMER_4326_TO_3857  = Transformer.from_crs("EPSG:4326", "EPSG:3857", always_xy=True)


def parse_gpkg_geometry(blob: bytes) -> Optional[base.BaseGeometry]:
    """Decodifica um BLOB de geometria GeoPackage padrão para um objeto Shapely."""
    if not blob or len(blob) < 8:
        return None
    # Magic bytes GP (0x47, 0x50)
    if blob[0] != 0x47 or blob[1] != 0x50:
        return None
    flags = blob[3]
    envelope_indicator = (flags >> 1) & 0x07
    env_sizes = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}
    env_size = env_sizes.get(envelope_indicator, 0)
    wkb_offset = 8 + env_size
    try:
        return wkb.loads(blob[wkb_offset:])
    except Exception:
        return None


def classify_layer_asset(layer_name: str) -> str:
    """Classifica semanticamente a camada GeoPackage para atribuição de raio de buffer."""
    normalized = layer_name.lower()
    if "subesta" in normalized:
        return "substation"
    if "transformador" in normalized:
        return "transformer"
    if "regulador" in normalized:
        return "regulator"
    if "capacitor" in normalized:
        return "capacitor"
    if "chave" in normalized:
        return "switch"
    if "v" in normalized and "at" in normalized:
        return "span_at"
    if "v" in normalized and "mt" in normalized:
        return "span_mt"
    if "v" in normalized and "bt" in normalized:
        return "span_bt"
    if "torre" in normalized:
        return "tower"
    if "poste" in normalized:
        return "pole"
    return "default"


class SpatialCoveragePlanner:
    """
    Motor de processamento espacial que extrai ativos vetoriais do GeoPackage,
    aplica buffers em espaço métrico euclidiano e unifica a cobertura de satélite HD.
    """

    def __init__(self, gpkg_path: str, buffer_config: Optional[Dict[str, float]] = None):
        self.gpkg_path = os.path.abspath(gpkg_path)
        if not os.path.exists(self.gpkg_path):
            raise FileNotFoundError(f"GeoPackage não encontrado: {self.gpkg_path}")
        self.buffer_config = DEFAULT_BUFFER_CONFIG.copy()
        if buffer_config:
            self.buffer_config.update(buffer_config)

    def inspect_layers(self) -> List[Dict]:
        """Inspeciona camadas vetoriais e CRS registrados em gpkg_contents."""
        results = []
        conn = sqlite3.connect(self.gpkg_path)
        cur = conn.cursor()
        cur.execute("""
            SELECT c.table_name, c.srs_id, g.column_name, g.geometry_type_name
            FROM gpkg_contents c
            LEFT JOIN gpkg_geometry_columns g ON c.table_name = g.table_name
            WHERE c.data_type = 'features'
        """)
        for tbl, srs_id, geom_col, geom_type in cur.fetchall():
            cur.execute(f'SELECT COUNT(*) FROM "{tbl}"')
            count = cur.fetchone()[0]
            results.append({
                "table": tbl,
                "srs_id": srs_id,
                "geom_col": geom_col or "geom",
                "geom_type": geom_type,
                "feature_count": count,
                "asset_category": classify_layer_asset(tbl)
            })
        conn.close()
        return results

    def _buffer_geometry_metric(self, geom: base.BaseGeometry, srs_id: int, buffer_meters: float) -> Optional[base.BaseGeometry]:
        """
        Aplica buffer métrico com reprojeção adequada para UTM 20S ou UTM 21S
        conforme a longitude da geometria. Retorna a geometria do buffer em WGS84 (EPSG:4326).
        """
        if geom is None or geom.is_empty:
            return None

        # 1. Obter a geometria em coordenadas métricas UTM
        if srs_id == 31980: # Já em UTM 20S (métrico)
            utm_geom = geom
            utm_to_wgs84 = TRANSFORMER_UTM20_TO_4326
        elif srs_id == 31981: # Já em UTM 21S (métrico)
            utm_geom = geom
            utm_to_wgs84 = TRANSFORMER_UTM21_TO_4326
        elif srs_id in (4326, 4674):
            # Coordenadas geográficas lat/lon. Determina a zona UTM pelo centróide
            centroid = geom.centroid
            lon = centroid.x
            if lon < -60.0:
                transformer = TRANSFORMER_4326_TO_UTM20 if srs_id == 4326 else TRANSFORMER_4674_TO_UTM20
                utm_to_wgs84 = TRANSFORMER_UTM20_TO_4326
            else:
                transformer = TRANSFORMER_4326_TO_UTM21 if srs_id == 4326 else TRANSFORMER_4674_TO_UTM21
                utm_to_wgs84 = TRANSFORMER_UTM21_TO_4326
            utm_geom = ops.transform(transformer.transform, geom)
        else:
            # Fallback padrão para UTM 20S (Rondônia oeste/central)
            utm_geom = ops.transform(TRANSFORMER_4326_TO_UTM20.transform, geom)
            utm_to_wgs84 = TRANSFORMER_UTM20_TO_4326

        # 2. Aplica buffer euclidiano métrico em metros
        buffered_utm = utm_geom.buffer(buffer_meters, quad_segs=4)
        if buffered_utm.is_empty:
            return None

        # 3. Reprojeta o polígono resultante para WGS 84 (EPSG:4326)
        buffered_wgs84 = ops.transform(utm_to_wgs84.transform, buffered_utm)
        return buffered_wgs84

    def build_consolidated_coverage(
        self,
        cluster_bbox: Optional[Tuple[float, float, float, float]] = None,
        max_features_per_layer: Optional[int] = None
    ) -> base.BaseGeometry:
        """
        Lê todas as camadas, aplica buffers métricos por categoria de ativo
        e unifica as geometrias em um MultiPolygon consolidado em EPSG:4326.
        """
        layers = self.inspect_layers()
        conn = sqlite3.connect(self.gpkg_path)
        cur = conn.cursor()

        all_buffered_geoms = []

        for layer in layers:
            tbl = layer["table"]
            srs_id = layer["srs_id"]
            geom_col = layer["geom_col"]
            category = layer["asset_category"]
            buffer_dist = self.buffer_config.get(category, self.buffer_config["default"])

            query = f'SELECT {geom_col} FROM "{tbl}" WHERE {geom_col} IS NOT NULL'
            if max_features_per_layer:
                query += f' LIMIT {max_features_per_layer}'

            cur.execute(query)
            batch = []
            while True:
                rows = cur.fetchmany(1000)
                if not rows:
                    break
                for row in rows:
                    raw_blob = row[0]
                    geom = parse_gpkg_geometry(raw_blob)
                    if not geom or geom.is_empty:
                        continue
                    
                    # Se cluster_bbox especificado, filtra previamente
                    if cluster_bbox:
                        min_x, min_y, max_x, max_y = cluster_bbox
                        # Se coordenadas forem UTM e cluster for Lat/Lon, converte bounds
                        gb = geom.bounds
                        if srs_id in (4326, 4674):
                            if gb[2] < min_x or gb[0] > max_x or gb[3] < min_y or gb[1] > max_y:
                                continue

                    buf = self._buffer_geometry_metric(geom, srs_id, buffer_dist)
                    if buf and not buf.is_empty:
                        batch.append(buf)

                if len(batch) >= 500:
                    merged = ops.unary_union(batch)
                    all_buffered_geoms.append(merged)
                    batch = []

            if batch:
                all_buffered_geoms.append(ops.unary_union(batch))

        conn.close()

        if not all_buffered_geoms:
            return Polygon()

        print(f"Consolidando {len(all_buffered_geoms)} blocos espaciais em união única...")
        consolidated = ops.unary_union(all_buffered_geoms)
        return consolidated

    def plan_tiles_for_coverage(
        self,
        coverage_wgs84: base.BaseGeometry,
        zoom_levels: List[int]
    ) -> Dict[int, Set[Tuple[int, int, int]]]:
        """
        Mapeia os quadrantes Slippy Map (z, x, y) estritamente interceptados
        pela cobertura HD consolidada para cada nível de zoom solicitado.
        """
        plan = {}
        for z in zoom_levels:
            tiles = geometry_to_tiles(coverage_wgs84, zoom=z)
            plan[z] = tiles
            print(f"  -> Zoom {z:2d}: {len(tiles):6d} tiles requeridos pela rede")
        return plan
