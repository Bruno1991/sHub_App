package com.saile.shub.gis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * OfflinePackageManager.kt
 *
 * Responsabilidade Única:
 * Gerenciador de ciclo de vida, validação criptográfica (SHA-256), integridade SQLite
 * e instalação atômica de pacotes de satélite MBTiles para o modo 100% offline do sGis.
 */
object OfflinePackageManager {

    private const val TAG = "OfflinePackageManager"

    data class ValidationResult(
        val isValid: Boolean,
        val sha256: String,
        val tileCount: Long,
        val minZoom: Int,
        val maxZoom: Int,
        val format: String,
        val errors: List<String>
    )

    data class InstallResult(
        val success: Boolean,
        val installedFile: File?,
        val metadata: MbtilesMetadata?,
        val errorMessage: String? = null
    )

    /**
     * Calcula o hash criptográfico SHA-256 do arquivo.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Valida integralmente a estrutura do arquivo MBTiles (SQLite, metadados, SHA-256 e tiles).
     */
    fun validatePackage(file: File, expectedSha256: String? = null): ValidationResult {
        val errors = mutableListOf<String>()
        if (!file.exists() || !file.canRead()) {
            return ValidationResult(false, "", 0, 0, 0, "", listOf("Arquivo não existe ou não legível"))
        }

        val sha256 = try {
            computeSha256(file)
        } catch (e: Exception) {
            errors.add("Falha no cálculo de SHA-256: ${e.message}")
            ""
        }

        if (!expectedSha256.isNullOrBlank() && !sha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            errors.add("Divergência de Checksum SHA-256. Esperado: $expectedSha256, Obtido: $sha256")
        }

        var db: SQLiteDatabase? = null
        var tileCount = 0L
        var minZoom = 0
        var maxZoom = 0
        var format = "jpg"

        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            // 1. Verificação de integridade estrutural
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getString(0)
                    if (!status.equals("ok", ignoreCase = true)) {
                        errors.add("Falha no PRAGMA quick_check: $status")
                    }
                } else {
                    errors.add("PRAGMA quick_check não retornou resposta")
                }
            }

            // 2. Contagem e limites de zoom
            db.rawQuery("SELECT COUNT(*), MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    tileCount = cursor.getLong(0)
                    minZoom = cursor.getInt(1)
                    maxZoom = cursor.getInt(2)
                }
            }

            if (tileCount <= 0L) {
                errors.add("O pacote MBTiles não contém nenhum ladrilho na tabela 'tiles'")
            }

            // 3. Formato nos metadados
            runCatching {
                db.rawQuery("SELECT value FROM metadata WHERE name = 'format' LIMIT 1", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        format = cursor.getString(0) ?: "jpg"
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("Erro na análise SQLite: ${e.message}")
        } finally {
            runCatching { db?.close() }
        }

        val isValid = errors.isEmpty()
        return ValidationResult(isValid, sha256, tileCount, minZoom, maxZoom, format, errors)
    }

    /**
     * Executa instalação atômica e segura de um novo pacote MBTiles a partir de uma URI.
     */
    suspend fun installPackageFromUri(
        context: Context,
        uri: Uri,
        targetFileName: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {}
    ): InstallResult = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "mbtiles").apply { mkdirs() }
        val targetFile = File(targetDir, targetFileName)
        val tempFile = File(targetDir, "$targetFileName.downloading_${System.currentTimeMillis()}.tmp")

        try {
            // 1. Copiar bytes para arquivo temporário
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    output.flush()
                }
            } ?: return@withContext InstallResult(false, null, null, "Não foi possível abrir URI de entrada")

            // 2. Validação estrita do arquivo temporário
            val validation = validatePackage(tempFile, expectedSha256)
            if (!validation.isValid) {
                tempFile.delete()
                val err = validation.errors.joinToString("; ")
                Log.e(TAG, "Instalação rejeitada por falha de validação: $err")
                return@withContext InstallResult(false, null, null, err)
            }

            // 3. Substituição atômica: preserva pacote anterior se substituição falhar
            val backupFile = File(targetDir, "$targetFileName.backup_${System.currentTimeMillis()}")
            if (targetFile.exists()) {
                if (!targetFile.renameTo(backupFile)) {
                    tempFile.delete()
                    return@withContext InstallResult(false, null, null, "Não foi possível criar backup do pacote existente")
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                // Restaura o backup
                backupFile.renameTo(targetFile)
                tempFile.delete()
                return@withContext InstallResult(false, null, null, "Falha na transição atômica do arquivo temporário")
            }

            // Sucesso: descarta backup antigo
            backupFile.delete()

            val meta = GisMbtilesHelper.readMetadata(targetFile)
            Log.i(TAG, "Pacote instalado com sucesso: ${targetFile.name} (${targetFile.length()} bytes, SHA-256=${validation.sha256})")
            return@withContext InstallResult(true, targetFile, meta)

        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Erro na instalação do pacote: ${e.message}", e)
            return@withContext InstallResult(false, null, null, e.message)
        }
    }
}

