#!/usr/bin/env python3
"""
tools/slippy_map_math.py
Módulo de cálculo matemático Slippy Map (Web Mercator EPSG:3857) e indexação espacial de tiles.

Responsabilidade Única:
Fornecer conversões matemáticas robustas entre coordenadas geográficas (WGS84) e
índices Slippy Map (XYZ / TMS), limites de tiles em Mercator e filtragem de quadrantes
a partir de geometrias espaciais vetoriais com STRtree.
"""

import math
from typing import Tuple, List, Set, Generator
from shapely.geometry import box, Polygon, MultiPolygon, Point, LineString, MultiLineString
from shapely.strtree import STRtree

# Limite estrito de latitude para a projeção Web Mercator
MAX_LATITUDE = 85.0511287798066
MIN_LATITUDE = -85.0511287798066
R_EARTH = 6378137.0

def clamp_latitude(lat: float) -> float:
    """Restringe a latitude ao limite navegável Web Mercator."""
    return max(min(lat, MAX_LATITUDE), MIN_LATITUDE)

def lonlat_to_tile(lon: float, lat: float, zoom: int) -> Tuple[int, int]:
    """
    Converte longitude e latitude (graus decimais WGS84) para coordenadas Slippy Map (x, y).
    Retorna tupla (x, y) no padrão OSM/Google Slippy Map (Y top-down).
    """
    if zoom < 0:
        raise ValueError("Zoom não pode ser negativo")
    lat_clamped = clamp_latitude(lat)
    lat_rad = math.radians(lat_clamped)
    n = 1 << zoom
    x = int(math.floor((lon + 180.0) / 360.0 * n))
    # Proteção para borda leste 180.0
    x = max(0, min(x, n - 1))

    sin_lat = math.sin(lat_rad)
    y = int(math.floor((1.0 - math.log((1.0 + sin_lat) / (1.0 - sin_lat)) / (2.0 * math.pi)) / 2.0 * n))
    y = max(0, min(y, n - 1))
    return (x, y)

def tile_to_lonlat_nw(x: int, y: int, zoom: int) -> Tuple[float, float]:
    """Retorna o canto Noroeste (minLon, maxLat) de um tile (x, y, zoom) em WGS84."""
    n = 1 << zoom
    lon = x / n * 360.0 - 180.0
    lat_rad = math.atan(math.sinh(math.pi * (1.0 - 2.0 * y / n)))
    lat = math.degrees(lat_rad)
    return (lon, lat)

def tile_bounds_lonlat(z: int, x: int, y: int) -> Tuple[float, float, float, float]:
    """
    Retorna os limites em coordenadas geográficas WGS84 (min_lon, min_lat, max_lon, max_lat)
    de um tile Slippy Map (z, x, y).
    """
    nw_lon, nw_lat = tile_to_lonlat_nw(x, y, z)
    se_lon, se_lat = tile_to_lonlat_nw(x + 1, y + 1, z)
    return (nw_lon, se_lat, se_lon, nw_lat)

def tile_bounds_3857(z: int, x: int, y: int) -> Tuple[float, float, float, float]:
    """
    Retorna os limites em coordenadas métricas Web Mercator EPSG:3857
    (min_x, min_y, max_x, max_y).
    """
    initial_resolution = 2.0 * math.pi * R_EARTH / 256.0
    origin_shift = 2.0 * math.pi * R_EARTH / 2.0
    res = initial_resolution / (1 << z)

    min_x = x * 256.0 * res - origin_shift
    max_x = (x + 1) * 256.0 * res - origin_shift
    max_y = origin_shift - y * 256.0 * res
    min_y = origin_shift - (y + 1) * 256.0 * res
    return (min_x, min_y, max_x, max_y)

def tile_polygon_3857(z: int, x: int, y: int) -> Polygon:
    """Gera um polígono Shapely EPSG:3857 representando a extensão do tile."""
    min_x, min_y, max_x, max_y = tile_bounds_3857(z, x, y)
    return box(min_x, min_y, max_x, max_y)

def tile_polygon_lonlat(z: int, x: int, y: int) -> Polygon:
    """Gera um polígono Shapely WGS84 representando a extensão do tile."""
    min_lon, min_lat, max_lon, max_lat = tile_bounds_lonlat(z, x, y)
    return box(min_lon, min_lat, max_lon, max_lat)

def xyz_to_tms(y: int, zoom: int) -> int:
    """Converte coordenada Y do Slippy Map (top-down) para TMS Y (bottom-up, MBTiles)."""
    return (1 << zoom) - 1 - y

def tms_to_xyz(tms_y: int, zoom: int) -> int:
    """Converte coordenada TMS Y (bottom-up) para Slippy Map Y (top-down)."""
    return (1 << zoom) - 1 - tms_y

def envelope_to_tiles(min_lon: float, min_lat: float, max_lon: float, max_lat: float, zoom: int) -> Generator[Tuple[int, int, int], None, None]:
    """
    Enumera todos os tiles (z, x, y) contidos ou interceptados pelo envelope WGS84.
    """
    x1, y2 = lonlat_to_tile(min_lon, min_lat, zoom)
    x2, y1 = lonlat_to_tile(max_lon, max_lat, zoom)

    min_x = min(x1, x2)
    max_x = max(x1, x2)
    min_y = min(y1, y2)
    max_y = max(y1, y2)

    for x in range(min_x, max_x + 1):
        for y in range(min_y, max_y + 1):
            yield (zoom, x, y)

def geometry_to_tiles(geometry, zoom: int, use_strtree: bool = True) -> Set[Tuple[int, int, int]]:
    """
    Gera apenas os tiles (z, x, y) cuja extensão poligonal realmente intercepta
    a geometria fornecida (em WGS84 EPSG:4326), evitando desperdício de quadrantes.
    """
    if geometry is None or geometry.is_empty:
        return set()

    min_lon, min_lat, max_lon, max_lat = geometry.bounds
    candidate_tiles = list(envelope_to_tiles(min_lon, min_lat, max_lon, max_lat, zoom))

    if len(candidate_tiles) <= 1:
        # Se for um único tile abrangente, verifica diretamente
        z, x, y = candidate_tiles[0]
        if tile_polygon_lonlat(z, x, y).intersects(geometry):
            return {(z, x, y)}
        return set()

    result_tiles = set()
    # Para corredores ou geometrias complexas, testa a interseção real
    for z, x, y in candidate_tiles:
        tile_poly = tile_polygon_lonlat(z, x, y)
        if tile_poly.intersects(geometry):
            result_tiles.add((z, x, y))

    return result_tiles

