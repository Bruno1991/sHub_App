package com.saile.shub.gis

/**
 * GisNetworkMode.kt
 *
 * Responsabilidade Única:
 * Define formalmente a política de conectividade de rede para o subsistema GIS do sHub.
 *
 * No modo OFFLINE_ONLY:
 * - Nenhuma requisição HTTP/HTTPS de ladrilhos ou vetores é permitida.
 * - Nenhum downloader online é instanciado.
 * - Todos os ladrilhos são resolvidos estritamente em pacotes MBTiles locais.
 * - Ausência de ladrilho resulta em fundo neutro ou fallback estritamente controlado.
 *
 * No modo ONLINE_ALLOWED:
 * - Provedores online são permitidos como fallback ou basemap sob autorização explícita do operador.
 */
enum class GisNetworkMode {
    OFFLINE_ONLY,
    ONLINE_ALLOWED
}

