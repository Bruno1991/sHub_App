package com.saile.shub.gis.domain

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Abstraction for reading raw R-Tree node BLOBs.
 * Enables JVM unit testing without Android SQLite dependencies.
 */
fun interface NodeSource {
    fun read(nodeno: Long): ByteArray?
}

/**
 * Single entry within an SQLite R-Tree node.
 * For interior nodes (level > 0), [childOrRowid] points to child nodeno.
 * For leaf nodes (level == 0), [childOrRowid] is the feature ID (rowid / fid).
 */
data class RtreeEntry(
    val childOrRowid: Long,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

/**
 * Parsed representation of an SQLite R-Tree node BLOB.
 * In SQLite R-Tree, [iLevel] is only meaningful on the root node (nodeno = 1),
 * where it indicates the total depth of the tree (0 = leaf root, 1 = 1 level above leaves, etc.).
 * In all non-root nodes, [iLevel] is 0 in the binary format.
 */
data class RtreeNode(
    val iLevel: Int,
    val nEntry: Int,
    val entries: List<RtreeEntry>
)

/**
 * Direct binary reader for SQLite R-Tree physical node tables (`rtree_<layer>_<geom>_node`).
 * Bypasses the SQLite RTree virtual table C extension, ensuring 100% compatibility across
 * all Android versions and OEM devices where the standard framework SQLite omits the RTREE module.
 *
 * Traversal uses Depth-First Search (DFS) tracking exact node levels derived from root depth.
 */
object GpkgRtreeReader {

    /**
     * Parses an SQLite R-Tree node BLOB into an [RtreeNode].
     * Node structure:
     * - Bytes 0..1: iLevel (short, big-endian) - tree depth on root node; 0 on non-root nodes
     * - Bytes 2..3: nEntry (short, big-endian) - number of entries in this node
     * - Offset 4: Array of entries (24 bytes each):
     *     - Bytes 0..7: child_nodeno_or_rowid (long, big-endian)
     *     - Bytes 8..11: minX (float, big-endian)
     *     - Bytes 12..15: maxX (float, big-endian)
     *     - Bytes 16..19: minY (float, big-endian)
     *     - Bytes 20..23: maxY (float, big-endian)
     */
    fun parseNode(blob: ByteArray): RtreeNode? {
        if (blob.size < 4) return null
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)
        val iLevel = buffer.short.toInt() and 0xFFFF
        val nEntry = buffer.short.toInt() and 0xFFFF

        val entries = ArrayList<RtreeEntry>(nEntry)
        for (i in 0 until nEntry) {
            if (buffer.remaining() < 24) break
            val childOrRowid = buffer.long
            val minX = buffer.float
            val maxX = buffer.float
            val minY = buffer.float
            val maxY = buffer.float
            entries.add(RtreeEntry(childOrRowid, minX, maxX, minY, maxY))
        }

        return RtreeNode(iLevel, nEntry, entries)
    }

    /**
     * Traverses the SQLite R-Tree using Depth-First Search (DFS) and returns matching feature IDs.
     * Pure algorithm taking a [NodeSource] for full testability.
     */
    fun queryFids(
        nodeSource: NodeSource,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        limit: Int = 10000,
        maxVisited: Int = 20000,
        shouldContinue: () -> Boolean = { true },
        onLog: ((String) -> Unit)? = null
    ): List<Long> {
        val rootBlob = nodeSource.read(1L)
        if (rootBlob == null) {
            onLog?.invoke("Root node (nodeno=1) could not be read")
            return emptyList()
        }

        val rootNode = parseNode(rootBlob)
        if (rootNode == null) {
            onLog?.invoke("Root node (nodeno=1) failed to parse")
            return emptyList()
        }

        val rootDepth = rootNode.iLevel
        val qMinX = minOf(minX, maxX).toFloat()
        val qMaxX = maxOf(minX, maxX).toFloat()
        val qMinY = minOf(minY, maxY).toFloat()
        val qMaxY = maxOf(minY, maxY).toFloat()

        val matchingFids = mutableListOf<Long>()
        // Stack stores Pair(nodeno, level). For root node, level = rootDepth.
        val stack = ArrayDeque<Pair<Long, Int>>()
        stack.add(Pair(1L, rootDepth))

        var visited = 0

        while (stack.isNotEmpty() && matchingFids.size < limit && visited < maxVisited && shouldContinue()) {
            val (nodeno, level) = stack.removeLast()
            visited++

            val node = if (nodeno == 1L) {
                rootNode
            } else {
                val blob = nodeSource.read(nodeno)
                if (blob == null) {
                    onLog?.invoke("Node $nodeno could not be read")
                    continue
                }
                val parsed = parseNode(blob)
                if (parsed == null) {
                    onLog?.invoke("Node $nodeno failed to parse")
                    continue
                }
                parsed
            }

            val isInterior = (level > 0)

            for (i in 0 until node.entries.size) {
                val entry = node.entries[i]
                // Bounding box intersection check
                if (entry.minX <= qMaxX && entry.maxX >= qMinX && entry.minY <= qMaxY && entry.maxY >= qMinY) {
                    if (isInterior) {
                        stack.add(Pair(entry.childOrRowid, level - 1))
                    } else {
                        matchingFids.add(entry.childOrRowid)
                        if (matchingFids.size >= limit) break
                    }
                }
            }
        }

        val limitHit = matchingFids.size >= limit
        onLog?.invoke("RTree query finished: rootDepth=$rootDepth, visited=$visited, found=${matchingFids.size}, limitHit=$limitHit")

        return matchingFids
    }

    /**
     * Traverses the SQLite R-Tree for the given layer and returns matching feature IDs (fids).
     * Connects directly to [SQLiteDatabase] and queries physical shadow table `rtree_<layer>_<geom>_node`.
     */
    fun queryFidsInBoundingBox(
        db: SQLiteDatabase,
        layerName: String,
        geomCol: String,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        limit: Int = 10000,
        shouldContinue: () -> Boolean = { true }
    ): List<Long> {
        val nodeTable = "rtree_${layerName}_${geomCol}_node"
        val cleanNodeTable = nodeTable.replace("\"", "\"\"")

        val source = NodeSource { nodeno ->
            try {
                db.rawQuery("SELECT data FROM \"$cleanNodeTable\" WHERE nodeno = $nodeno", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getBlob(0) else null
                }
            } catch (e: Exception) {
                Log.w("ShubGis", "Error reading rtree node $nodeno on '$cleanNodeTable': ${e.message}")
                null
            }
        }

        return queryFids(
            nodeSource = source,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            limit = limit,
            maxVisited = 20000,
            shouldContinue = shouldContinue,
            onLog = { msg ->
                Log.d("ShubGis", "[$layerName] $msg")
            }
        )
    }
}
