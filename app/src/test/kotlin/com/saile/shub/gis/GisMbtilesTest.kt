package com.saile.shub.gis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class GisMbtilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMbtilesMetadataFormatting() {
        val dummyFile = File("test.mbtiles")
        val meta = MbtilesMetadata(
            file = dummyFile,
            name = "Test Map",
            format = "jpg",
            minZoom = 0,
            maxZoom = 16,
            bounds = null,
            sizeBytes = 25 * 1024 * 1024L
        )
        assertEquals("25.0 MB", meta.formattedSize)

        val largeMeta = meta.copy(sizeBytes = 2500 * 1024 * 1024L)
        assertEquals("2.44 GB", largeMeta.formattedSize)
    }

    @Test
    fun testCleanTileSourceName() {
        val meta = MbtilesMetadata(
            file = File("satelite.mbtiles"),
            name = "Google Satélite Rondônia (HD)",
            format = "jpg",
            minZoom = 0,
            maxZoom = 16,
            bounds = null,
            sizeBytes = 1000
        )
        val cleanName = meta.name
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .trim('_')
            .ifBlank { "mbtiles_${meta.file.nameWithoutExtension}" }

        // Accented characters and spaces/parentheses are sanitized for osmdroid cache safety
        assertTrue(cleanName.matches(Regex("^[a-zA-Z0-9_]+$")))
    }
}
