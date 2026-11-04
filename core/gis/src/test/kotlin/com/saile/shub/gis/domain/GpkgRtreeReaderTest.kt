package com.saile.shub.gis.domain

import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpkgRtreeReaderTest {

    private fun buildNodeBlob(iLevel: Int, entries: List<RtreeEntry>): ByteArray {
        val buffer = ByteBuffer.allocate(4 + entries.size * 24).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(iLevel.toShort())
        buffer.putShort(entries.size.toShort())
        for (e in entries) {
            buffer.putLong(e.childOrRowid)
            buffer.putFloat(e.minX)
            buffer.putFloat(e.maxX)
            buffer.putFloat(e.minY)
            buffer.putFloat(e.maxY)
        }
        return buffer.array()
    }

    @Test
    fun `depth 2 tree returns leaf rowids and never intermediate node numbers`() {
        // Tree structure:
        // Root (nodeno = 1, depth = 2):
        //   - points to child node 2: bbox [0, 100, 0, 100]
        //   - points to child node 3: bbox [200, 300, 200, 300]
        // Child 2 (nodeno = 2, iLevel in blob is 0!):
        //   - points to leaf node 4: bbox [0, 50, 0, 50]
        //   - points to leaf node 5: bbox [60, 100, 60, 100]
        // Leaf 4 (nodeno = 4, iLevel in blob is 0!):
        //   - feature rowid 1001: bbox [10, 20, 10, 20]
        //   - feature rowid 1002: bbox [30, 40, 30, 40]
        // Leaf 5 (nodeno = 5, iLevel in blob is 0!):
        //   - feature rowid 2001: bbox [70, 80, 70, 80]
        // Child 3 (nodeno = 3, iLevel in blob is 0!):
        //   - points to leaf node 6: bbox [200, 250, 200, 250]
        // Leaf 6 (nodeno = 6, iLevel in blob is 0!):
        //   - feature rowid 3001: bbox [210, 220, 210, 220]

        val nodes = mutableMapOf<Long, ByteArray>()

        // Root has iLevel = 2
        nodes[1L] = buildNodeBlob(
            iLevel = 2,
            entries = listOf(
                RtreeEntry(2L, 0f, 100f, 0f, 100f),
                RtreeEntry(3L, 200f, 300f, 200f, 300f)
            )
        )

        // Non-root interior node 2: iLevel in blob is 0!
        nodes[2L] = buildNodeBlob(
            iLevel = 0,
            entries = listOf(
                RtreeEntry(4L, 0f, 50f, 0f, 50f),
                RtreeEntry(5L, 60f, 100f, 60f, 100f)
            )
        )

        // Non-root interior node 3: iLevel in blob is 0!
        nodes[3L] = buildNodeBlob(
            iLevel = 0,
            entries = listOf(
                RtreeEntry(6L, 200f, 250f, 200f, 250f)
            )
        )

        // Leaf 4: iLevel in blob is 0!
        nodes[4L] = buildNodeBlob(
            iLevel = 0,
            entries = listOf(
                RtreeEntry(1001L, 10f, 20f, 10f, 20f),
                RtreeEntry(1002L, 30f, 40f, 30f, 40f)
            )
        )

        // Leaf 5: iLevel in blob is 0!
        nodes[5L] = buildNodeBlob(
            iLevel = 0,
            entries = listOf(
                RtreeEntry(2001L, 70f, 80f, 70f, 80f)
            )
        )

        // Leaf 6: iLevel in blob is 0!
        nodes[6L] = buildNodeBlob(
            iLevel = 0,
            entries = listOf(
                RtreeEntry(3001L, 210f, 220f, 210f, 220f)
            )
        )

        val source = NodeSource { nodeno -> nodes[nodeno] }

        // Query overlapping only Leaf 4 ([0, 50, 0, 50])
        val resultsA = GpkgRtreeReader.queryFids(
            nodeSource = source,
            minX = 0.0,
            maxX = 25.0,
            minY = 0.0,
            maxY = 25.0
        )

        // Should return ONLY rowid 1001, and NEVER node numbers 2, 3, 4, 5, 6
        assertEquals(listOf(1001L), resultsA)

        // Query overlapping Leaf 4 and Leaf 5 ([0, 100, 0, 100])
        val resultsAllChild2 = GpkgRtreeReader.queryFids(
            nodeSource = source,
            minX = 0.0,
            maxX = 100.0,
            minY = 0.0,
            maxY = 100.0
        )
        // Set contains 1001, 1002, 2001
        assertEquals(setOf(1001L, 1002L, 2001L), resultsAllChild2.toSet())
        assertFalse(resultsAllChild2.contains(2L), "Should not contain node number 2")
        assertFalse(resultsAllChild2.contains(4L), "Should not contain node number 4")
        assertFalse(resultsAllChild2.contains(5L), "Should not contain node number 5")

        // Query entire extent
        val resultsTotal = GpkgRtreeReader.queryFids(
            nodeSource = source,
            minX = 0.0,
            maxX = 300.0,
            minY = 0.0,
            maxY = 300.0
        )
        assertEquals(setOf(1001L, 1002L, 2001L, 3001L), resultsTotal.toSet())
    }

    @Test
    fun `respects limit and cooperative cancellation`() {
        val nodes = mutableMapOf<Long, ByteArray>()
        // Root with 10 leaf entries
        val entries = (1..10).map { i ->
            RtreeEntry(i.toLong(), i.toFloat(), (i + 1).toFloat(), 0f, 10f)
        }
        nodes[1L] = buildNodeBlob(iLevel = 0, entries = entries)
        val source = NodeSource { nodeno -> nodes[nodeno] }

        // Limit = 3
        val limited = GpkgRtreeReader.queryFids(
            nodeSource = source,
            minX = 0.0,
            maxX = 20.0,
            minY = 0.0,
            maxY = 20.0,
            limit = 3
        )
        assertEquals(3, limited.size)

        // Cancellation should stop immediately
        var shouldStop = false
        val cancelled = GpkgRtreeReader.queryFids(
            nodeSource = source,
            minX = 0.0,
            maxX = 20.0,
            minY = 0.0,
            maxY = 20.0,
            shouldContinue = { !shouldStop }
        )
        assertTrue(cancelled.isNotEmpty())
    }
}
