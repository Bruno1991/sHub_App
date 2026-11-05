#!/usr/bin/env python3
"""
tools/mbtiles_pipeline.py
Pipeline robusto de construção, geração, aquisição e validação de pacotes MBTiles 1.3.

Responsabilidade Única:
Gerenciar a escrita transacional de tiles no banco SQLite MBTiles no padrão estrito TMS,
abstrair fornecedores de tiles com respeito a licenciamento, suportar retomada com checkpoints,
avaliar formatos (JPEG vs WebP) e executar validação de integridade física e geométrica.
"""

import os
import sys
import io
import time
import math
import hashlib
import sqlite3
import argparse
from typing import Dict, List, Tuple, Set, Optional
from abc import ABC, abstractmethod
from PIL import Image, ImageDraw, ImageFont

from slippy_map_math import xyz_to_tms, tms_to_xyz, tile_bounds_lonlat

class TileSourceProvider(ABC):
    """Interface abstrata para fornecedores de tiles compatíveis com regras de licenciamento."""

    @abstractmethod
    def get_tile(self, z: int, x: int, y: int) -> Optional[bytes]:
        """Obtém os bytes da imagem (JPEG, WebP ou PNG) para a coordenada Slippy Map (z, x, y)."""
        pass

    @abstractmethod
    def get_attribution(self) -> str:
        """Retorna a atribuição legal exigida pelo provedor."""
        pass

    @abstractmethod
    def get_format(self) -> str:
        """Retorna a extensão do formato: 'jpg', 'png' ou 'webp'."""
        pass


class MockTileSourceProvider(TileSourceProvider):
    """
    Fornecedor sintético de alta velocidade para testes locais, CI/CD e benchmarks
    100% desconectados de rede (zero requisições externas).
    Gera imagens válidas com gradientes e marcação de grade Slippy Map.
    """

    def __init__(self, tile_format: str = "jpg", quality: int = 78):
        self.tile_format = tile_format.lower().replace(".", "")
        self.quality = quality

    def get_tile(self, z: int, x: int, y: int) -> Optional[bytes]:
        img = Image.new("RGB", (256, 256), color=(
            (x * 37 + z * 13) % 256,
            (y * 59 + z * 29) % 256,
            (x * 17 + y * 23 + 120) % 256
        ))
        draw = ImageDraw.Draw(img)
        # Borda de demarcação do tile
        draw.rectangle([(0, 0), (255, 255)], outline=(200, 200, 200), width=1)
        # Identificação textual
        text = f"z={z}\nx={x}\ny={y}"
        draw.text((12, 12), text, fill=(255, 255, 255))

        out = io.BytesIO()
        if self.tile_format in ("jpg", "jpeg"):
            img.save(out, format="JPEG", quality=self.quality, optimize=True)
        elif self.tile_format == "webp":
            img.save(out, format="WEBP", quality=self.quality, method=4)
        elif self.tile_format == "png":
            img.save(out, format="PNG", optimize=True)
        else:
            img.save(out, format="JPEG", quality=self.quality)
        return out.getvalue()

    def get_attribution(self) -> str:
        return "Synthetic Offline Test Fixture - sGis Engine"

    def get_format(self) -> str:
        return self.tile_format


class MbtilesPipeline:
    """Gerenciador de criação, gravação e checkpoints de pacotes MBTiles."""

    def __init__(self, mbtiles_path: str):
        self.mbtiles_path = os.path.abspath(mbtiles_path)

    def init_database(self, name: str, format_ext: str, min_zoom: int, max_zoom: int, bounds: str, attribution: str, version: str = "1.3.0"):
        """Inicializa as tabelas padrão MBTiles e insere os metadados obrigatórios."""
        conn = sqlite3.connect(self.mbtiles_path)
        cur = conn.cursor()
        cur.execute("PRAGMA synchronous = NORMAL")
        cur.execute("PRAGMA journal_mode = WAL")

        cur.execute("CREATE TABLE IF NOT EXISTS metadata (name text, value text);")
        cur.execute("CREATE TABLE IF NOT EXISTS tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);")
        cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS tile_index on tiles (zoom_level, tile_column, tile_row);")

        meta = [
            ("name", name),
            ("type", "baselayer"),
            ("version", version),
            ("description", f"Pacote MBTiles Offline sGis - {name}"),
            ("format", format_ext.lower().replace(".", "")),
            ("minzoom", str(min_zoom)),
            ("maxzoom", str(max_zoom)),
            ("bounds", bounds),
            ("attribution", attribution)
        ]
        cur.execute("DELETE FROM metadata")
        cur.executemany("INSERT INTO metadata VALUES (?, ?)", meta)
        conn.commit()
        conn.close()

    def get_existing_tile_keys(self) -> Set[Tuple[int, int, int]]:
        """Lê os ladrilhos já gravados para suporte a retomada (resume)."""
        if not os.path.exists(self.mbtiles_path):
            return set()
        conn = sqlite3.connect(self.mbtiles_path)
        cur = conn.cursor()
        cur.execute("SELECT zoom_level, tile_column, tile_row FROM tiles")
        rows = cur.fetchall()
        conn.close()
        # Retorna em coordenadas Slippy Map (z, x, y)
        return {(z, x, tms_to_xyz(tms_y, z)) for z, x, tms_y in rows}

    def build_package(
        self,
        provider: TileSourceProvider,
        tiles_to_pack: Set[Tuple[int, int, int]],
        batch_size: int = 500
    ) -> Dict:
        """
        Executa a geração com transações em lotes e proteção de integridade.
        Converte estritamente cada coordenada Y para TMS (bottom-up) no banco.
        """
        existing = self.get_existing_tile_keys()
        pending = [t for t in tiles_to_pack if t not in existing]

        total = len(tiles_to_pack)
        resumed = len(existing)
        print(f"Construindo {self.mbtiles_path}: {len(pending)} pendentes, {resumed} já existentes de {total} total.")

        conn = sqlite3.connect(self.mbtiles_path)
        cur = conn.cursor()
        cur.execute("PRAGMA synchronous = OFF")

        batch = []
        written = 0
        failed = 0
        start_time = time.time()

        for z, x, y in pending:
            data = provider.get_tile(z, x, y)
            if data and len(data) > 0:
                tms_y = xyz_to_tms(y, z)
                batch.append((z, x, tms_y, data))
                written += 1
                if len(batch) >= batch_size:
                    cur.executemany("INSERT OR REPLACE INTO tiles VALUES (?, ?, ?, ?)", batch)
                    conn.commit()
                    batch = []
            else:
                failed += 1

        if batch:
            cur.executemany("INSERT OR REPLACE INTO tiles VALUES (?, ?, ?, ?)", batch)
            conn.commit()

        cur.execute("PRAGMA optimize")
        conn.close()

        elapsed = time.time() - start_time
        return {
            "total_requested": total,
            "already_present": resumed,
            "written": written,
            "failed": failed,
            "elapsed_seconds": elapsed
        }


class MbtilesValidator:
    """Validador estrito de conformidade, formato e integridade SQLite de arquivos MBTiles."""

    def __init__(self, mbtiles_path: str):
        self.mbtiles_path = os.path.abspath(mbtiles_path)

    def validate(self, sample_size: int = 50) -> Dict:
        report = {
            "file": self.mbtiles_path,
            "exists": False,
            "size_bytes": 0,
            "sha256": None,
            "sqlite_integrity": False,
            "metadata": {},
            "tile_count": 0,
            "zoom_distribution": {},
            "corrupt_tiles": 0,
            "errors": []
        }

        if not os.path.exists(self.mbtiles_path):
            report["errors"].append("Arquivo não existe")
            return report

        report["exists"] = True
        report["size_bytes"] = os.path.getsize(self.mbtiles_path)

        # Cálculo do Checksum SHA-256
        sha = hashlib.sha256()
        with open(self.mbtiles_path, "rb") as f:
            while chunk := f.read(1024 * 1024):
                sha.update(chunk)
        report["sha256"] = sha.hexdigest()

        # Validação do SQLite
        try:
            conn = sqlite3.connect(self.mbtiles_path)
            cur = conn.cursor()

            # 1. Quick check de integridade
            cur.execute("PRAGMA quick_check")
            check_res = cur.fetchone()[0]
            if check_res.lower() == "ok":
                report["sqlite_integrity"] = True
            else:
                report["errors"].append(f"PRAGMA quick_check falhou: {check_res}")

            # 2. Leitura de metadados
            cur.execute("SELECT name, value FROM metadata")
            for k, v in cur.fetchall():
                report["metadata"][k] = v

            for req in ["name", "format", "minzoom", "maxzoom"]:
                if req not in report["metadata"]:
                    report["errors"].append(f"Metadado obrigatório ausente: {req}")

            # 3. Contagem total e distribuição por zoom
            cur.execute("SELECT zoom_level, COUNT(*) FROM tiles GROUP BY zoom_level ORDER BY zoom_level")
            dist = {}
            total = 0
            for z, cnt in cur.fetchall():
                dist[z] = cnt
                total += cnt
            report["zoom_distribution"] = dist
            report["tile_count"] = total

            # 4. Amostragem de integridade e decodificação de imagem
            cur.execute("SELECT tile_data FROM tiles LIMIT ?", (sample_size,))
            corrupt = 0
            for row in cur.fetchall():
                blob = row[0]
                if not blob or len(blob) < 10:
                    corrupt += 1
                    continue
                try:
                    with Image.open(io.BytesIO(blob)) as img:
                        img.verify()
                except Exception:
                    corrupt += 1
            report["corrupt_tiles"] = corrupt

            conn.close()
        except Exception as e:
            report["errors"].append(f"Exceção SQLite: {str(e)}")

        return report


def evaluate_webp_vs_jpeg_compression(quality: int = 78, samples: int = 10) -> Dict:
    """
    Avalia e compara cientificamente JPEG e WebP para tiles raster de satélite:
    dimensão em bytes, fidelidade de decodificação e latência de leitura.
    """
    mock = MockTileSourceProvider("png", quality=100)
    jpeg_sizes = []
    webp_sizes = []
    jpeg_decode_times = []
    webp_decode_times = []

    for i in range(samples):
        # Gera tile base
        raw_png = mock.get_tile(15, 1000 + i, 2000 + i)
        img = Image.open(io.BytesIO(raw_png))

        # Salva em JPEG
        jpeg_buf = io.BytesIO()
        t0 = time.perf_counter()
        img.save(jpeg_buf, format="JPEG", quality=quality)
        jpeg_bytes = jpeg_buf.getvalue()
        jpeg_sizes.append(len(jpeg_bytes))

        # Decode JPEG
        t_dec0 = time.perf_counter()
        with Image.open(io.BytesIO(jpeg_bytes)) as j_img:
            j_img.load()
        jpeg_decode_times.append(time.perf_counter() - t_dec0)

        # Salva em WebP
        webp_buf = io.BytesIO()
        img.save(webp_buf, format="WEBP", quality=quality)
        webp_bytes = webp_buf.getvalue()
        webp_sizes.append(len(webp_bytes))

        # Decode WebP
        t_dec0 = time.perf_counter()
        with Image.open(io.BytesIO(webp_bytes)) as w_img:
            w_img.load()
        webp_decode_times.append(time.perf_counter() - t_dec0)

    avg_jpeg = sum(jpeg_sizes) / len(jpeg_sizes)
    avg_webp = sum(webp_sizes) / len(webp_sizes)
    savings = (1.0 - (avg_webp / avg_jpeg)) * 100.0

    return {
        "quality": quality,
        "samples": samples,
        "avg_jpeg_bytes": avg_jpeg,
        "avg_webp_bytes": avg_webp,
        "webp_size_reduction_percent": round(savings, 2),
        "avg_jpeg_decode_ms": round((sum(jpeg_decode_times) / len(jpeg_decode_times)) * 1000.0, 3),
        "avg_webp_decode_ms": round((sum(webp_decode_times) / len(webp_decode_times)) * 1000.0, 3)
    }

