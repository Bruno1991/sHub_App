package com.saile.shub.gis

import com.saile.shub.gis.domain.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageManifestTest {

    @Test
    fun testPackageCategoryPriorityOrder() {
        // Strict operational priority: Corridors HD (1) > Municipal (2) > Regional (3) > World (4)
        assertTrue(PackageCategory.NETWORK_CORRIDORS.priority < PackageCategory.MUNICIPAL_HD.priority)
        assertTrue(PackageCategory.MUNICIPAL_HD.priority < PackageCategory.REGIONAL.priority)
        assertTrue(PackageCategory.REGIONAL.priority < PackageCategory.WORLD_BASE.priority)
    }

    @Test
    fun testManifestJsonRoundtrip() {
        val original = PackageManifest(
            id = "cacoal_hd_v1",
            name = "Cacoal Hub Operacional HD",
            version = "1.2.0",
            category = PackageCategory.MUNICIPAL_HD,
            format = "jpg",
            minZoom = 12,
            maxZoom = 18,
            bounds = BoundingBox(minLon = -61.5, maxLon = -61.4, minLat = -11.5, maxLat = -11.4),
            sizeBytes = 150_000_000L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sourceOrigin = "sGis Pipeline",
            license = "Internal Concession License",
            attribution = "sGis RO Offline"
        )

        val jsonString = original.toJson()
        val deserialized = PackageManifest.fromJson(jsonString)

        assertNotNull(deserialized)
        assertEquals(original.id, deserialized!!.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.version, deserialized.version)
        assertEquals(original.category, deserialized.category)
        assertEquals(original.sha256, deserialized.sha256)
        assertEquals(original.bounds?.minLon, deserialized.bounds?.minLon)
    }
}

