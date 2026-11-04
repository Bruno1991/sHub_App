#!/usr/bin/env python3
"""
Gerador de Pacotes MBTiles 100% Offline para o sGis
Baixa pirâmides de quadrantes de satélite do Google em alta velocidade
e grava em um banco SQLite .mbtiles padrão OGC / Mapbox MBTiles 1.3.
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
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = resp.read()
                if len(data) > 100:
                    tms_y = (1 << z) - 1 - y
                    return (z, x, tms_y, data)
        except Exception:
            time.sleep(0.3 * (attempt + 1))
    return None

def main():
    output_path = r"C:\dev\s_Hub_App\satelite_rondonia.mbtiles"
    if os.path.exists(output_path):
        os.remove(output_path)

    print(f"=== Iniciando Geração de Pacote MBTiles: {output_path} ===")
    
    # 1. Definir áreas de cobertura
    # Rondônia macro: -66.8 a -59.8 lon, -13.7 a -8.0 lat (zooms 0..10)
    # Região Operacional Ji-Paraná/SEs: -63.45 a -63.10 lon, -10.95 a -10.70 lat (zooms 11..14)
    # Área Urbana / Linhas: -63.35 a -63.20 lon, -10.90 a -10.80 lat (zooms 15..16)
    
    tiles_to_download = set()

    # Macro Rondônia (Zooms 0 a 10)
    min_lon_ro, max_lon_ro = -66.8, -59.8
    min_lat_ro, max_lat_ro = -13.7, -8.0
    for z in range(0, 11):
        x1, y2 = deg2num(min_lat_ro, min_lon_ro, z)
        x2, y1 = deg2num(max_lat_ro, max_lon_ro, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles_to_download.add((z, x, y))

    # Região Operacional (Zooms 11 a 14)
    min_lon_op, max_lon_op = -63.45, -63.10
    min_lat_op, max_lat_op = -10.95, -10.70
    for z in range(11, 15):
        x1, y2 = deg2num(min_lat_op, min_lon_op, z)
        x2, y1 = deg2num(max_lat_op, max_lon_op, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles_to_download.add((z, x, y))

    # Detalhe Urbano de Rede (Zooms 15 a 16)
    min_lon_ur, max_lon_ur = -63.35, -63.20
    min_lat_ur, max_lat_ur = -10.90, -10.80
    for z in range(15, 17):
        x1, y2 = deg2num(min_lat_ur, min_lon_ur, z)
        x2, y1 = deg2num(max_lat_ur, max_lon_ur, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles_to_download.add((z, x, y))

    total_tiles = len(tiles_to_download)
    print(f"Total de quadrantes a baixar: {total_tiles} tiles (Zooms 0 a 16)")

    # 2. Criar banco SQLite MBTiles
    conn = sqlite3.connect(output_path)
    cur = conn.cursor()
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA journal_mode = MEMORY")
    
    cur.execute("CREATE TABLE metadata (name text, value text);")
    cur.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);")
    
    metadata = [
        ("name", "Google Satélite Rondônia"),
        ("type", "baselayer"),
        ("version", "1.3"),
        ("description", "Satélite Híbrido 100% Offline de Alta Resolução"),
        ("format", "jpg"),
        ("minzoom", "0"),
        ("maxzoom", "16"),
        ("bounds", f"{min_lon_ro},{min_lat_ro},{max_lon_ro},{max_lat_ro}"),
        ("center", f"-63.284,-10.828,12")
    ]
    cur.executemany("INSERT INTO metadata VALUES (?, ?)", metadata)
    conn.commit()

    # 3. Download Concorrente
    print("Iniciando download multithread (16 conexões simultâneas)...")
    start_time = time.time()
    downloaded = 0
    batch = []

    with ThreadPoolExecutor(max_workers=16) as executor:
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
    print(f"=== Concluído em {elapsed:.1f}s! ===")
    print(f"Arquivo gerado: {output_path} ({file_size_mb:.2f} MB)")

if __name__ == "__main__":
    main()
