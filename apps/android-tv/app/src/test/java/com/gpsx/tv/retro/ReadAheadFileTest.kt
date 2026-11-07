package com.gpsx.tv.retro

import java.io.IOException
import java.util.concurrent.Executor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAheadFileTest {
    private class Memory(private val data: ByteArray) : RandomRead {
        var reads = 0
        var failing = false
        var closed = false
        override val size: Long = data.size.toLong()

        override fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (failing) throw IOException("share gone")
            reads++
            // Short reads, as a network file may give.
            val count = minOf(length, 3, data.size - position.toInt())
            if (count <= 0) return -1
            System.arraycopy(data, position.toInt(), target, offset, count)
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private val noPrefetch = Executor { }
    private val data = ByteArray(100) { it.toByte() }

    @Test
    fun `reads across blocks return the file's bytes`() {
        val file = ReadAheadFile(Memory(data), noPrefetch, blockSize = 16, maxBlocks = 2)
        val target = ByteArray(40)
        assertEquals(40, file.read(10, target, 0, 40))
        assertArrayEquals(data.copyOfRange(10, 50), target)
    }

    @Test
    fun `a read stops at the end of the file`() {
        val file = ReadAheadFile(Memory(data), noPrefetch, blockSize = 16, maxBlocks = 2)
        val target = ByteArray(40)
        assertEquals(4, file.read(96, target, 0, 40))
        assertEquals(0, file.read(100, target, 0, 40))
        assertEquals(0, file.read(500, target, 0, 40))
    }

    @Test
    fun `a cached block is not read again and memory stays bounded`() {
        val source = Memory(data)
        val file = ReadAheadFile(source, noPrefetch, blockSize = 16, maxBlocks = 2)
        file.read(0, ByteArray(4), 0, 4)
        val afterFirst = source.reads
        file.read(4, ByteArray(4), 0, 4)
        assertEquals(afterFirst, source.reads)
        file.read(16, ByteArray(1), 0, 1)
        file.read(32, ByteArray(1), 0, 1)
        val beforeEvicted = source.reads
        file.read(0, ByteArray(1), 0, 1)
        assertTrue("the oldest block was dropped", source.reads > beforeEvicted)
    }

    @Test
    fun `the next block is fetched ahead`() {
        val source = Memory(data)
        val file = ReadAheadFile(source, Executor { it.run() }, blockSize = 16, maxBlocks = 4)
        file.read(0, ByteArray(16), 0, 16)
        val afterRead = source.reads
        file.read(16, ByteArray(16), 0, 16)
        assertTrue("block 1 was already in memory, block 2 came ahead", source.reads > afterRead)
        assertEquals(16, file.read(16, ByteArray(16), 0, 16))
    }

    @Test
    fun `a failed read reaches the caller and a failed prefetch does not`() {
        val source = Memory(data)
        val file = ReadAheadFile(source, Executor { source.failing = true; it.run(); source.failing = false }, blockSize = 16, maxBlocks = 4)
        assertEquals(16, file.read(0, ByteArray(16), 0, 16))
        source.failing = true
        assertThrows(IOException::class.java) { file.read(48, ByteArray(1), 0, 1) }
    }

    @Test
    fun `close closes the share file once`() {
        val source = Memory(data)
        val file = ReadAheadFile(source, noPrefetch)
        file.close()
        file.close()
        assertTrue(source.closed)
    }
}
