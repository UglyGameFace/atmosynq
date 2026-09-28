package com.uglygameface.atmosynq.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class FilamentSceneAssetTest {
    @Test
    fun embeddedAtmosynqGlbIsProductionEnvironmentKit() {
        val source =
            File("src/main/assets/filament/atmos_scene.glb")
                .takeIf { it.exists() }
                ?: File("app/src/main/assets/filament/atmos_scene.glb")

        assertTrue("Filament GLB asset must exist", source.exists())

        val bytes = source.readBytes()
        assertTrue(
            "Production GLB unexpectedly fell back to prototype size",
            bytes.size >= 500_000
        )

        val buffer =
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46546C67, buffer.int)
        assertEquals(2, buffer.int)
        assertEquals(bytes.size, buffer.int)

        var chunks = 0
        var json = ""
        var sawBin = false

        while (buffer.remaining() >= 8) {
            val chunkLength = buffer.int
            val chunkType = buffer.int

            assertTrue(
                "GLB chunk length must be non-negative",
                chunkLength >= 0
            )
            assertTrue(
                "GLB chunk exceeds declared file length",
                chunkLength <= buffer.remaining()
            )

            val chunk = ByteArray(chunkLength)
            buffer.get(chunk)

            when (chunkType) {
                0x4E4F534A ->
                    json =
                        String(
                            chunk,
                            StandardCharsets.UTF_8
                        )
                0x004E4942 ->
                    sawBin = true
            }

            chunks += 1
        }

        assertEquals(
            "GLB must end exactly on a chunk boundary",
            0,
            buffer.remaining()
        )
        assertTrue("GLB must contain a JSON chunk", json.isNotBlank())
        assertTrue("GLB must contain a BIN chunk", sawBin)
        assertTrue(
            "GLB should contain at least JSON + BIN chunks",
            chunks >= 2
        )

        listOf(
            "Ground",
            "WetRoad",
            "Water",
            "Mountain0",
            "RollingHill0",
            "CityBuilding0",
            "CityGlass0",
            "CityWindows0",
            "House0",
            "HouseRoof0",
            "HouseGarage0",
            "HouseDoor0",
            "HouseChimney0",
            "Pine0",
            "Broadleaf0",
            "Palm0",
            "StreetLight0",
            "Cloud0",
            "Cloud4",
            "RibbonCyan",
            "RibbonPurple",
            "Sun"
        ).forEach { node ->
            assertTrue(
                "Production GLB missing required node $node",
                json.contains("\"name\":\"$node\"")
            )
        }

        listOf(
            "WetAsphalt",
            "Concrete",
            "Terrain",
            "Water",
            "WindowGlow",
            "HouseSiding",
            "Brick",
            "Stucco",
            "GarageDoor"
        ).forEach { material ->
            assertTrue(
                "Production GLB missing required PBR material $material",
                json.contains(material)
            )
        }
    }
}
