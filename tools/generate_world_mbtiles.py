#!/usr/bin/env python3
"""
Gerador de Pacote MBTiles do Mapa Mundi Completo + Rondônia e Grid em Alta Resolução
Produz um arquivo .mbtiles SQLite 100% offline para o sGis contendo:
- Cobertura Global Completa (Zooms 0 a 5): 100% de todos os quadrantes do globo terrestre.
- Cobertura Macro América do Sul e Brasil (Zooms 6 a 7).
- Cobertura Regional de Rondônia (Zooms 8 a 10).
- Cobertura Operacional Ji-Paraná & Subestações (Zooms 11 a 14).
- Alta Resolução Urbana e Linhas de Média Tensão (Zooms 15 a 16).
"""

import sys
import os
import math
import sqlite3
import urllib.request
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

def deg2num(lat_deg, lon_deg, zoom):
    lat_rad = math.radians(lat_deg)
    n = 2.0 ** zoom
    xtile = int((lon_deg + 180.0) / 360.0 * n)
    ytile = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return (xtile, ytile)

def download_tile(z, x, y, retries=3):
    server = (x + y) % 4
    url = f"https://mt{server}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=6) as resp:
                data = resp.read()
                if len(data) > 100:
                    tms_y = (1 << z) - 1 - y
                    return (z, x, tms_y, data)
        except Exception:
            time.sleep(0.2 * (attempt + 1))
    return None

def build_tile_set():
    tiles = set()

    # 1. Mapa Mundi Completo (Zooms 0 a 5)
    print("Mapeando quadrantes globais (Zooms 0 a 5)...")
    for z in range(0, 6):
        n = 1 << z
        for x in range(n):
            for y in range(n):
                tiles.add((z, x, y))
    print(f"  -> {len(tiles)} tiles do globo completo adicionados.")

    # 2. América do Sul & Brasil Macro (Zooms 6 e 7)
    sa_lon_min, sa_lat_min = -82.0, -35.0
    sa_lon_max, sa_lat_max = -34.0, 13.0
    for z in (6, 7):
        x1, y2 = deg2num(sa_lat_min, sa_lon_min, z)
        x2, y1 = deg2num(sa_lat_max, sa_lon_max, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles.add((z, x, y))

    # 3. Rondônia Regional (Zooms 8 a 10)
    ro_lon_min, ro_lat_min = -67.0, -13.8
    ro_lon_max, ro_lat_max = -59.5, -7.9
    for z in range(8, 11):
        x1, y2 = deg2num(ro_lat_min, ro_lon_min, z)
        x2, y1 = deg2num(ro_lat_max, ro_lon_max, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles.add((z, x, y))

    # 4. Região Operacional Ji-Paraná & SEs (Zooms 11 a 14)
    op_lon_min, op_lat_min = -63.45, -10.95
    op_lon_max, op_lat_max = -63.10, -10.70
    for z in range(11, 15):
        x1, y2 = deg2num(op_lat_min, op_lon_min, z)
        x2, y1 = deg2num(op_lat_max, op_lon_max, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles.add((z, x, y))

    # 5. Alta Resolução Urbana e Linhas de Média Tensão (Zooms 15 a 16)
    ur_lon_min, ur_lat_min = -63.35, -10.90
    ur_lon_max, ur_lat_max = -63.20, -10.80
    for z in range(15, 17):
        x1, y2 = deg2num(ur_lat_min, ur_lon_min, z)
        x2, y1 = deg2num(ur_lat_max, ur_lon_max, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles.add((z, x, y))

    return tiles

def main():
    output_path = os.path.abspath(r"C:\dev\s_Hub_App\satelite_mundi.mbtiles")
    if os.path.exists(output_path):
        try:
            os.remove(output_path)
        except Exception:
            pass

    print(f"=== Iniciando Geração do Pacote MBTiles Mapa Mundi: {output_path} ===")

    tiles_to_download = build_tile_set()
    total_tiles = len(tiles_to_download)
    print(f"Total de quadrantes a baixar: {total_tiles} tiles (Zooms 0 a 16)")

    # Criar banco SQLite MBTiles
    conn = sqlite3.connect(output_path)
    cur = conn.cursor()
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA journal_mode = MEMORY")

    cur.execute("CREATE TABLE metadata (name text, value text);")
    cur.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);")

    metadata = [
        ("name", "Google Satelite Mundi"),
        ("type", "baselayer"),
        ("version", "1.3"),
        ("description", "Mapa Mundi Completo e Rondônia Alta Resolução 100% Offline"),
        ("format", "jpg"),
        ("minzoom", "0"),
        ("maxzoom", "16"),
        ("bounds", "-180.0,-85.0511,180.0,85.0511"),
        ("center", "-63.284,-10.828,12")
    ]
    cur.executemany("INSERT INTO metadata VALUES (?, ?)", metadata)
    conn.commit()

    # Download multithread
    print("Iniciando download multithread (24 conexões simultâneas)...")
    start_time = time.time()
    downloaded = 0
    batch = []

    with ThreadPoolExecutor(max_workers=24) as executor:
        futures = {executor.submit(download_tile, z, x, y): (z, x, y) for (z, x, y) in tiles_to_download}
        for future in as_completed(futures):
            res = future.result()
            if res:
                batch.append(res)
                downloaded += 1
                if len(batch) >= 100:
                    cur.executemany("INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", batch)
                    conn.commit()
                    batch = []
                    pct = (downloaded * 100) / total_tiles
                    elapsed = time.time() - start_time
                    speed = downloaded / elapsed if elapsed > 0 else 0
                    print(f"Progresso: {downloaded}/{total_tiles} ({pct:.1f}%) - {speed:.1f} tiles/s", end="\r")

    if batch:
        cur.executemany("INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", batch)
        conn.commit()

    print(f"\nFinalizando e indexando MBTiles...")
    cur.execute("CREATE UNIQUE INDEX tile_index on tiles (zoom_level, tile_column, tile_row);")
    conn.commit()
    conn.close()

    elapsed = time.time() - start_time
    file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"=== Pacote Mapa Mundi Concluído em {elapsed:.1f}s! ===")
    print(f"Arquivo gerado: {output_path} ({file_size_mb:.2f} MB)")

if __name__ == "__main__":
    main()
