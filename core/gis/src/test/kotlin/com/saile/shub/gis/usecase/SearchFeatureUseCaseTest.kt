package com.saile.shub.gis.usecase

import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchFeatureUseCaseTest {

    private class FakeGisDao : GisDao {
        val features = mutableListOf<GisFeatureEntity>()

        override suspend fun insertPackage(pkg: ImportedPackageEntity): Long = 1L
        override suspend fun insertFeatures(features: List<GisFeatureEntity>) {
            this.features.addAll(features)
        }
        override suspend fun updatePackageStatus(packageId: Long, status: String, count: Int) {}

        override suspend fun searchFeatures(query: String, limit: Int, offset: Int): List<GisFeatureEntity> {
            return features.filter {
                (it.codeNorm?.contains(query) == true) ||
                (it.searchContent?.contains(query) == true) ||
                (it.codeRaw?.contains(query, ignoreCase = true) == true)
            }.take(limit)
        }

        override suspend fun findNearestFeatures(
            targetLat: Double,
            targetLon: Double,
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double,
            limit: Int
        ): List<GisFeatureEntity> {
            return features.filter {
                val lat = it.centroidLat ?: return@filter false
                val lon = it.centroidLon ?: return@filter false
                lat in minLat..maxLat && lon in minLon..maxLon
            }.take(limit)
        }

        override suspend fun getAllPackages(): List<ImportedPackageEntity> = emptyList()
        override suspend fun deleteAllPackages() {}
        override suspend fun deleteAllFeatures() { features.clear() }
        override suspend fun getActivePackage(): ImportedPackageEntity? = null
        override suspend fun getPackageById(packageId: Long): ImportedPackageEntity? = null
        override suspend fun getFeatureCountByPackage(packageId: Long): Int = features.size
    }

    @Test
    fun testSearchCoordinateGeneratesCoordinateMatchAndNearbyAssets() = runBlocking {
        val dao = FakeGisDao()
        dao.features.add(
            GisFeatureEntity(
                id = 1,
                packageId = 1,
                sourceLayer = "Transformador",
                featureSourceId = "2456136",
                codeRaw = "PTUP0024",
                codeNorm = "ptup0024",
                searchContent = "ptup0024 2456136 td 138kv 75kva",
                geometryType = "Point",
                centroidLat = -11.434,
                centroidLon = -61.447,
                minLat = -11.434,
                minLon = -61.447,
                maxLat = -11.434,
                maxLon = -61.447,
                metadataJson = "{}"
            )
        )

        val useCase = SearchFeatureUseCase(dao)

        // Search by lat, lon coordinate
        val result = useCase.execute("-11.434, -61.447")
        assertNotNull(result.coordinateMatch)
        assertEquals(-11.434, result.coordinateMatch!!.latitude, 0.001)
        assertEquals(-61.447, result.coordinateMatch!!.longitude, 0.001)
        assertEquals(1, result.features.size)
        assertEquals("PTUP0024", result.features[0].title)
    }

    @Test
    fun testSearchByCodeOrAttributeReturnsExactMatch() = runBlocking {
        val dao = FakeGisDao()
        dao.features.add(
            GisFeatureEntity(
                id = 2,
                packageId = 1,
                sourceLayer = "SUBESTAÇÃO",
                featureSourceId = "47308",
                codeRaw = "SE GUAJARA MIRIM",
                codeNorm = "seguajaramirim",
                searchContent = "se guajara mirim gmi 138kv 138kv energisa",
                geometryType = "MultiPolygon",
                centroidLat = -10.78,
                centroidLon = -65.33,
                minLat = -10.79,
                minLon = -65.34,
                maxLat = -10.77,
                maxLon = -65.32,
                metadataJson = "{}"
            )
        )

        val useCase = SearchFeatureUseCase(dao)

        // Search by Sigla "GMI"
        val resultSigla = useCase.execute("GMI")
        assertEquals(1, resultSigla.features.size)
        assertEquals("SE GUAJARA MIRIM", resultSigla.features[0].title)

        // Search by Partial Name "guajara"
        val resultName = useCase.execute("guajara")
        assertEquals(1, resultName.features.size)
        assertEquals("SE GUAJARA MIRIM", resultName.features[0].title)
    }
}

