package com.uglygameface.atmosynq.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FilamentSceneAssetTest {
    @Test
    fun embeddedAtmosynqGlbHasValidContainer() {
        val source = File("src/main/assets/filament/atmos_scene.glb")
            .takeIf { it.exists() }
            ?: File("app/src/main/assets/filament/atmos_scene.glb")

        assertTrue("Filament GLB asset must exist", source.exists())

        val bytes = source.readBytes()
        assertTrue("GLB must include a 12-byte header", bytes.size >= 20)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46546C67, buffer.int)
        assertEquals(2, buffer.int)
        assertEquals(bytes.size, buffer.int)

        var chunks = 0
        var sawJson = false
        var sawBin = false

        while (buffer.remaining() >= 8) {
            val chunkLength = buffer.int
            val chunkType = buffer.int

            assertTrue("GLB chunk length must be non-negative", chunkLength >= 0)
            assertTrue(
                "GLB chunk exceeds declared file length",
                chunkLength <= buffer.remaining()
            )

            when (chunkType) {
                0x4E4F534A -> sawJson = true
                0x004E4942 -> sawBin = true
            }

            buffer.position(buffer.position() + chunkLength)
            chunks += 1
        }

        assertEquals("GLB must end exactly on a chunk boundary", 0, buffer.remaining())
        assertTrue("GLB must contain a JSON chunk", sawJson)
        assertTrue("GLB must contain a BIN chunk", sawBin)
        assertTrue("GLB should contain at least JSON + BIN chunks", chunks >= 2)
    }
}
