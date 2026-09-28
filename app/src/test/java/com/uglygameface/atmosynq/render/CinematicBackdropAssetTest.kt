package com.uglygameface.atmosynq.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CinematicBackdropAssetTest {
    @Test
    fun temperateTownBackdropIsRealWebpAsset() {
        val source =
            File(
                "src/main/assets/backdrops/scene_temperate_town.webp"
            ).takeIf { it.exists() }
                ?: File(
                    "app/src/main/assets/backdrops/scene_temperate_town.webp"
                )

        assertTrue(
            "Cinematic backdrop must exist",
            source.exists()
        )

        val bytes = source.readBytes()
        assertTrue(
            "Cinematic backdrop is unexpectedly tiny",
            bytes.size >= 10_000
        )

        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals('W'.code.toByte(), bytes[8])
        assertEquals('E'.code.toByte(), bytes[9])
        assertEquals('B'.code.toByte(), bytes[10])
        assertEquals('P'.code.toByte(), bytes[11])
    }
}
