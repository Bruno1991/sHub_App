#!/usr/bin/env python3
"""
tools/generate_world_hd_mbtiles.py
Pipeline integrado de geração de pacotes MBTiles com pirâmides escalonadas (Mundo, Rondônia e Polos Elétricos).

Responsabilidade Única:
Fornecer CLI estruturada para gerar pacotes MBTiles utilizando Slippy Map Math,
pipeline com checkpoints e interface de provedores compatível com regras de licenciamento.
"""

import sys
import os
import argparse
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from slippy_map_math import lonlat_to_tile, envelope_to_tiles, xyz_to_tms
from mbtiles_pipeline import (
    TileSourceProvider,
    MockTileSourceProvider,
    MbtilesPipeline,
    MbtilesValidator
)

def add_box(tiles, min_lat, min_lon, max_lat, max_lon, zooms):
    for z in zooms:
        for _, x, y in envelope_to_tiles(min_lon, min_lat, max_lon, max_lat, z):
            tiles.add((z, x, y))

def build_tile_set(polo_cacoal: bool = True):
    tiles = set()

    # 1. Mapa Mundi Completo (Zooms 0 a 5)
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

    # 4. Hub Cacoal (Ativo em inspeção)
    if polo_cacoal:
        print("Mapeando Hub Operacional Cacoal (Zooms 12 a 18)...")
        add_box(tiles, -11.470, -61.480, -11.400, -61.400, range(12, 18))
        add_box(tiles, -11.465, -61.465, -11.415, -61.415, [18])
        print(f"  -> Total acumulado: {len(tiles)} tiles.")

    return tiles

def main():
    parser = argparse.ArgumentParser(description="Gerador de Pacotes MBTiles 100% Offline para o sGis")
    parser.add_argument("--output", default="satelite_mundi.mbtiles", help="Caminho do arquivo .mbtiles")
    parser.add_argument("--provider", choices=["mock", "custom"], default="mock", help="Provedor de tiles")
    parser.add_argument("--format", default="jpg", choices=["jpg", "webp", "png"], help="Formato de imagem dos tiles")
    parser.add_argument("--quality", type=int, default=78, help="Qualidade de compressão da imagem")
    args = parser.parse_args()

    output_path = os.path.abspath(args.output)
    print(f"=== GERADOR DE MBTILES sGis ===")
    print(f"Destino: {output_path}")
    print(f"Provedor: {args.provider} | Formato: {args.format} (q={args.quality})")

    tiles_to_pack = build_tile_set()
    pipeline = MbtilesPipeline(output_path)
    pipeline.init_database(
        name="Google Satelite Mundi HD",
        format_ext=args.format,
        min_zoom=0,
        max_zoom=18,
        bounds="-180.0,-85.0511,180.0,85.0511",
        attribution="Fonte Autorizada / sGis Engine"
    )

    if args.provider == "mock":
        provider = MockTileSourceProvider(tile_format=args.format, quality=args.quality)
    else:
        provider = MockTileSourceProvider(tile_format=args.format, quality=args.quality)

    result = pipeline.build_package(provider, tiles_to_pack, batch_size=200)
    print(f"\nGravação concluída: {result['written']} tiles gravados em {result['elapsed_seconds']:.1f}s.")

    validator = MbtilesValidator(output_path)
    val = validator.validate(sample_size=30)
    print("\n--- Relatório de Validação ---")
    print(f"Tamanho: {val['size_bytes'] / (1024*1024):.2f} MB")
    print(f"Total de Tiles: {val['tile_count']}")
    print(f"Integridade SQLite: {val['sqlite_integrity']}")
    print(f"SHA-256: {val['sha256']}")
    print(f"Tiles Corrompidos na Amostra: {val['corrupt_tiles']}")

if __name__ == "__main__":
    main()
