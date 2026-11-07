// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.io.Closeable
import java.util.concurrent.Executor

/** Random reads from a file that stays on the share. */
interface RandomRead : Closeable {
    val size: Long

    /** Reads up to [length] bytes at [position]; returns the count, 0 or less at the end of the file. */
    fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int
}

/**
 * Bounded read cache over a file on the share: [maxBlocks] blocks of [blockSize] bytes in memory,
 * oldest dropped first, and the block after the last one read fetched on [prefetch], one at a time.
 * A disc is read in long runs, so most core reads never wait for the network. A failed prefetch is
 * ignored: the read that needs the block tries again and reports the error.
 */
class ReadAheadFile(
    private val source: RandomRead,
    private val prefetch: Executor,
    private val blockSize: Int = 512 * 1024,
    private val maxBlocks: Int = 16,
) : Closeable {
    private val lock = Any()
    private val blocks =
        object : LinkedHashMap<Long, ByteArray>(maxBlocks, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > maxBlocks
        }
    private var prefetching = false
    private var closed = false

    val size: Long get() = source.size

    fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int {
        var done = 0
        while (done < length) {
            val at = position + done
            if (at >= size) break
            val index = at / blockSize
            val block = synchronized(lock) { block(index) }
            val inBlock = (at - index * blockSize).toInt()
            if (inBlock >= block.size) break
            val count = minOf(length - done, block.size - inBlock)
            System.arraycopy(block, inBlock, target, offset + done, count)
            done += count
            ahead(index + 1)
        }
        return done
    }

    private fun ahead(index: Long) {
        synchronized(lock) {
            if (closed || prefetching || index * blockSize >= size || index in blocks) return
            prefetching = true
        }
        prefetch.execute {
            synchronized(lock) {
                if (!closed) runCatching { block(index) }
                prefetching = false
            }
        }
    }

    /** The block at [index]; the caller holds [lock]. */
    private fun block(index: Long): ByteArray =
        blocks[index] ?: run {
            val start = index * blockSize
            val buffer = ByteArray(minOf(blockSize.toLong(), size - start).toInt())
            var filled = 0
            while (filled < buffer.size) {
                val count = source.read(start + filled, buffer, filled, buffer.size - filled)
                if (count <= 0) break
                filled += count
            }
            val block = if (filled == buffer.size) buffer else buffer.copyOf(filled)
            blocks[index] = block
            block
        }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            blocks.clear()
            source.close()
        }
    }
}
