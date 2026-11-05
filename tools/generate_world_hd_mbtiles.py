#!/usr/bin/env python3
"""
Gerador de Pacote MBTiles do Mapa Mundi Completo + Rondônia e Grid Elétrico em Alta Resolução (HD)
Produz um arquivo .mbtiles SQLite 100% offline para o sGis contendo:
- Cobertura Global Completa (Zooms 0 a 5): 100% do globo terrestre.
- Cobertura Macro América do Sul e Brasil (Zooms 6 a 7).
- Cobertura Regional de Rondônia Completa (Zooms 8 a 11).
- Alta Resolução (Zooms 12 a 18) para o polo operacional de Cacoal (onde os ativos ativos e postes estão focados).
- Alta Resolução (Zooms 12 a 18) para Ji-Paraná e Porto Velho.
- Média Resolução (Zooms 12 a 15) para Vilhena, Ariquemes, Rolim de Moura, Jaru, Ouro Preto e Pimenta Bueno.
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

def download_tile(z, x, y, retries=4):
    server = (x + y) % 4
    url = f"https://mt{server}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
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
            time.sleep(0.15 * (attempt + 1))
    return None

def add_box(tiles, min_lat, min_lon, max_lat, max_lon, zooms):
    for z in zooms:
        x1, y2 = deg2num(min_lat, min_lon, z)
        x2, y1 = deg2num(max_lat, max_lon, z)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                tiles.add((z, x, y))

def build_tile_set():
    tiles = set()

    # 1. Mapa Mundi Completo (Zooms 0 a 5) -> 100% dos quadrantes do planeta Terra
    print("Mapeando quadrantes globais (Zooms 0 a 5)...")
    for z in range(0, 6):
        n = 1 << z
        for x in range(n):
            for y in range(n):
                tiles.add((z, x, y))
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 2. América do Sul & Brasil Macro (Zooms 6 e 7)
    print("Mapeando América do Sul & Brasil Macro (Zooms 6 e 7)...")
    add_box(tiles, -35.0, -82.0, 13.0, -34.0, [6, 7])
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 3. Estado de Rondônia Completo (Zooms 8 a 11)
    print("Mapeando Estado de Rondônia Integral (Zooms 8 a 11)...")
    add_box(tiles, -13.70, -67.00, -7.95, -59.80, range(8, 12))
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 4. Hub Principal: Cacoal (Ativo em inspeção, Poste -11.435697, -61.437460)
    print("Mapeando Cacoal Urbano + Subestação + Linhas (Zooms 12 a 18)...")
    # Zooms 12 a 17 cobrindo toda a área municipal expandida
    add_box(tiles, -11.470, -61.480, -11.400, -61.400, range(12, 18))
    # Zoom 18 cobrindo toda a malha urbana principal e a linha de subestação
    add_box(tiles, -11.465, -61.465, -11.415, -61.415, [18])
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 5. Hub Ji-Paraná (Zooms 12 a 18)
    print("Mapeando Ji-Paraná (Zooms 12 a 18)...")
    add_box(tiles, -10.930, -61.990, -10.850, -61.910, range(12, 17))
    add_box(tiles, -10.900, -61.970, -10.865, -61.930, [17, 18])
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 6. Hub Porto Velho (Zooms 12 a 17)
    print("Mapeando Porto Velho (Zooms 12 a 17)...")
    add_box(tiles, -8.820, -63.940, -8.720, -63.840, range(12, 17))
    add_box(tiles, -8.780, -63.920, -8.740, -63.870, [17])
    print(f"  -> Total acumulado: {len(tiles)} tiles.")

    # 7. Vilhena e Ariquemes (Zooms 12 a 15)
    print("Mapeando Vilhena e Ariquemes (Zooms 12 a 15)...")
    add_box(tiles, -12.780, -60.180, -12.710, -60.100, range(12, 16))
    add_box(tiles, -9.950, -63.080, -9.880, -63.000, range(12, 16))

    # 8. Jaru, Rolim de Moura, Ouro Preto, Pimenta Bueno (Zooms 12 a 14)
    print("Mapeando outros polos elétricos de Rondônia (Zooms 12 a 14)...")
    add_box(tiles, -10.46, -62.48, -10.42, -62.44, range(12, 15)) # Jaru
    add_box(tiles, -11.75, -61.80, -11.71, -61.75, range(12, 15)) # Rolim de Moura
    add_box(tiles, -10.74, -62.28, -10.70, -62.24, range(12, 15)) # Ouro Preto
    add_box(tiles, -11.70, -61.22, -11.66, -61.16, range(12, 15)) # Pimenta Bueno

    return tiles

def main():
    output_path = os.path.abspath(r"C:\dev\s_Hub_App\satelite_mundi.mbtiles")
    temp_path = output_path + ".tmp"
    
    if os.path.exists(temp_path):
        try:
            os.remove(temp_path)
        except Exception:
            pass

    print(f"=== GERADOR DE MAPA MUNDI + RONDÔNIA HD MBTILES ===")
    print(f"Destino: {output_path}")

    tiles_to_download = build_tile_set()
    total_tiles = len(tiles_to_download)
    print(f"\nTotal consolidado de quadrantes únicos: {total_tiles} tiles (Zooms 0 a 18)")

    # Criação do banco SQLite MBTiles
    conn = sqlite3.connect(temp_path)
    cur = conn.cursor()
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA journal_mode = MEMORY")
    cur.execute("PRAGMA cache_size = 50000")

    cur.execute("CREATE TABLE metadata (name text, value text);")
    cur.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);")

    metadata = [
        ("name", "Google Satelite Mundi HD"),
        ("type", "baselayer"),
        ("version", "1.3"),
        ("description", "Mapa Mundi Completo e Grid Eletrico Rondonia Alta Resolucao 100% Offline"),
        ("format", "jpg"),
        ("minzoom", "0"),
        ("maxzoom", "18"),
        ("bounds", "-180.0,-85.0511,180.0,85.0511"),
        ("center", "-61.437460,-11.435697,17")
    ]
    cur.executemany("INSERT INTO metadata VALUES (?, ?)", metadata)
    conn.commit()

    # Download multithread
    workers = 32
    print(f"Iniciando download multithread com {workers} conexões concorrentes...")
    start_time = time.time()
    downloaded = 0
    failed = 0
    batch = []

    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {executor.submit(download_tile, z, x, y): (z, x, y) for (z, x, y) in tiles_to_download}
        for future in as_completed(futures):
            res = future.result()
            if res:
                batch.append(res)
                downloaded += 1
                if len(batch) >= 200:
                    cur.executemany("INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", batch)
                    conn.commit()
                    batch = []
                    pct = (downloaded * 100) / total_tiles
                    elapsed = time.time() - start_time
                    speed = downloaded / elapsed if elapsed > 0 else 0
                    print(f"Progresso: {downloaded}/{total_tiles} ({pct:.1f}%) | Velocidade: {speed:.1f} tiles/s | Restante: {int((total_tiles - downloaded)/speed) if speed > 0 else 0}s", end="\r")
            else:
                failed += 1

    if batch:
        cur.executemany("INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", batch)
        conn.commit()

    print(f"\nFinalizando: {downloaded} tiles gravados ({failed} falhas). Criando índice espacial...")
    cur.execute("CREATE UNIQUE INDEX tile_index on tiles (zoom_level, tile_column, tile_row);")
    conn.commit()
    conn.close()

    # Troca atômica do arquivo final
    if os.path.exists(output_path):
        try:
            os.remove(output_path)
        except Exception as e:
            print(f"Aviso ao remover antigo: {e}")
    os.replace(temp_path, output_path)

    elapsed = time.time() - start_time
    file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n=== PACOTE MBTILES GERADO COM SUCESSO EM {elapsed:.1f}s! ===")
    print(f"Arquivo: {output_path}")
    print(f"Tamanho total: {file_size_mb:.2f} MB")

    # Verificação pós-geração
    conn_check = sqlite3.connect(output_path)
    cur_check = conn_check.cursor()
    print("\n--- Verificação de Integridade para Cacoal (-11.435697, -61.437460) ---")
    for z in [0, 5, 8, 10, 12, 14, 16, 17, 18]:
        x, y = deg2num(-11.435697, -61.437460, z)
        tms_y = (1 << z) - 1 - y
        cur_check.execute("SELECT length(tile_data) FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?", (z, x, tms_y))
        row = cur_check.fetchone()
        status = f"OK ({row[0]} bytes)" if row else "AUSENTE"
        print(f"Zoom {z:2d} (x={x}, y={tms_y}): {status}")
    conn_check.close()

if __name__ == "__main__":
    main()

