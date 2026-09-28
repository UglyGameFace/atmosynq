package com.uglygameface.atmosynq.worlds

import com.uglygameface.atmosynq.render.LatitudeBand
import com.uglygameface.atmosynq.render.SceneProfile
import com.uglygameface.atmosynq.render.SettlementKind
import com.uglygameface.atmosynq.render.TerrainKind
import com.uglygameface.atmosynq.weather.WeatherSnapshot

enum class WorldKind {
    CABIN,
    CITY,
    COAST,
    FOREST,
    STORM
}

data class NormalizedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f)
        require(top in 0f..1f)
        require(right in 0f..1f)
        require(bottom in 0f..1f)
        require(left < right)
        require(top < bottom)
    }

    val centerX: Float
        get() = (left + right) * 0.5f

    val centerY: Float
        get() = (top + bottom) * 0.5f

    fun contains(
        x: Float,
        y: Float
    ): Boolean =
        x in left..right &&
            y in top..bottom
}

enum class WorldHotspotAction {
    FOCUS,
    ENTER,
    OBSERVE
}

data class EyeSpyTarget(
    val id: String,
    val label: String,
    val hint: String,
    val detail: String,
    val region: NormalizedRegion,
    val action: WorldHotspotAction = WorldHotspotAction.FOCUS
)

data class WorldSceneLayer(
    val id: String,
    val title: String,
    val subtitle: String,
    val assetPath: String,
    val sourceWidth: Int = 1440,
    val sourceHeight: Int = 2160,
    val hotspots: List<EyeSpyTarget>
) {
    init {
        require(sourceWidth > 0)
        require(sourceHeight > 0)
        require(assetPath.isNotBlank())
    }
}

data class WorldExperience(
    val kind: WorldKind,
    val title: String,
    val subtitle: String,
    val signatureMoment: String,
    val eyeSpyTargets: List<EyeSpyTarget>,
    val primaryScene: WorldSceneLayer? = null,
    val interiorScene: WorldSceneLayer? = null
)

object WorldExperienceResolver {
    private val thunderCodes = setOf(95, 96, 97, 99)

    fun resolve(
        profile: SceneProfile,
        weather: WeatherSnapshot?
    ): WorldExperience {
        val thunder = weather?.weatherCode in thunderCodes
        val wet =
            (weather?.precipitationMm ?: 0.0) > 0.15 ||
                (weather?.rainMm ?: 0.0) > 0.15 ||
                (weather?.showersMm ?: 0.0) > 0.15
        val snowy = (weather?.snowfallCm ?: 0.0) > 0.05

        return when {
            snowy ||
                profile.latitudeBand == LatitudeBand.POLAR ||
                profile.terrain == TerrainKind.MOUNTAIN ->
                cabinWorld()

            thunder || wet ->
                stormWorld()

            profile.settlement == SettlementKind.METRO ||
                profile.settlement == SettlementKind.CITY ->
                cityWorld()

            profile.latitudeBand == LatitudeBand.TROPICAL ||
                (
                    profile.latitudeBand == LatitudeBand.WARM &&
                        profile.terrain == TerrainKind.FLAT
                    ) ->
                coastWorld()

            else ->
                forestWorld()
        }
    }

    private fun cabinWorld(): WorldExperience {
        val exteriorTargets =
            listOf(
                EyeSpyTarget(
                    id = "cabin",
                    label = "the glowing cabin",
                    hint = "Look for the warm light between the trees.",
                    detail = "The cabin is enterable. Tap it again outside Eye Spy to go inside.",
                    region = NormalizedRegion(0.18f, 0.34f, 0.82f, 0.86f),
                    action = WorldHotspotAction.ENTER
                ),
                EyeSpyTarget(
                    id = "roofline",
                    label = "the roofline",
                    hint = "Look above the cabin lights.",
                    detail = "Snow, rain and wind effects will build around this roofline.",
                    region = NormalizedRegion(0.22f, 0.27f, 0.78f, 0.52f),
                    action = WorldHotspotAction.FOCUS
                ),
                EyeSpyTarget(
                    id = "forest-edge",
                    label = "the dark forest edge",
                    hint = "Search beside the cabin.",
                    detail = "The surrounding forest carries wind, fog and precipitation depth.",
                    region = NormalizedRegion(0.02f, 0.24f, 0.34f, 0.82f),
                    action = WorldHotspotAction.FOCUS
                )
            )

        val interiorTargets =
            listOf(
                EyeSpyTarget(
                    id = "fireplace",
                    label = "the fireplace",
                    hint = "Follow the warmest light in the room.",
                    detail = "The fireplace is the cabin's signature interaction point.",
                    region = NormalizedRegion(0.56f, 0.30f, 0.98f, 0.88f),
                    action = WorldHotspotAction.OBSERVE
                ),
                EyeSpyTarget(
                    id = "window",
                    label = "the cabin window",
                    hint = "Look toward the cooler light.",
                    detail = "The outside weather remains visible from inside the cabin.",
                    region = NormalizedRegion(0.00f, 0.18f, 0.46f, 0.62f),
                    action = WorldHotspotAction.FOCUS
                ),
                EyeSpyTarget(
                    id = "hearth",
                    label = "the hearth",
                    hint = "Look below the fireplace glow.",
                    detail = "This is where the marshmallow interaction will anchor.",
                    region = NormalizedRegion(0.54f, 0.58f, 0.98f, 0.98f),
                    action = WorldHotspotAction.FOCUS
                )
            )

        return WorldExperience(
            kind = WorldKind.CABIN,
            title = "Fireside Highlands",
            subtitle =
                "A real cabin exterior that opens into a warm fireplace interior.",
            signatureMoment = "Enter cabin • fireside interaction",
            eyeSpyTargets = exteriorTargets,
            primaryScene =
                WorldSceneLayer(
                    id = "cabin-exterior",
                    title = "Fireside Highlands",
                    subtitle = "Tap the glowing cabin to step inside.",
                    assetPath = "worlds/cabin_night_exterior.webp",
                    hotspots = exteriorTargets
                ),
            interiorScene =
                WorldSceneLayer(
                    id = "cabin-interior",
                    title = "Fireside",
                    subtitle = "Warm up by the fire while the weather stays alive outside.",
                    assetPath = "worlds/cabin_fireplace_interior.webp",
                    hotspots = interiorTargets
                )
        )
    }

    private fun stormWorld(): WorldExperience =
        WorldExperience(
            kind = WorldKind.STORM,
            title = "Storm Watch",
            subtitle =
                "Move through the storm while real conditions evolve.",
            signatureMoment = "Lightning watch • storm photography",
            eyeSpyTargets =
                listOf(
                    EyeSpyTarget(
                        id = "storm-cloud",
                        label = "the darkest cloud",
                        hint = "Check the upper-left sky.",
                        detail = "This cloud bank is the storm's visual anchor.",
                        region = NormalizedRegion(0.05f, 0.08f, 0.45f, 0.38f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "reflection",
                        label = "a rain reflection",
                        hint = "Search near the lower center.",
                        detail = "Rain reflections intensify as precipitation rises.",
                        region = NormalizedRegion(0.34f, 0.62f, 0.7f, 0.9f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "horizon",
                        label = "the storm horizon",
                        hint = "Look where the world meets the sky.",
                        detail = "The horizon carries the storm's visibility and lightning events.",
                        region = NormalizedRegion(0.58f, 0.34f, 0.95f, 0.58f),
                        action = WorldHotspotAction.FOCUS
                    )
                )
        )

    private fun cityWorld(): WorldExperience =
        WorldExperience(
            kind = WorldKind.CITY,
            title = "Skyline Drift",
            subtitle =
                "Explore the city atmosphere beyond the forecast cards.",
            signatureMoment = "Rooftop view • skyline discoveries",
            eyeSpyTargets =
                listOf(
                    EyeSpyTarget(
                        id = "rooftop",
                        label = "the highest rooftop",
                        hint = "Scan the skyline.",
                        detail = "The rooftop becomes the city observation point.",
                        region = NormalizedRegion(0.44f, 0.24f, 0.72f, 0.52f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "sky-break",
                        label = "the bright sky break",
                        hint = "Look above the buildings.",
                        detail = "A break in the cloud deck reveals live sky lighting.",
                        region = NormalizedRegion(0.58f, 0.06f, 0.9f, 0.3f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "street-glow",
                        label = "the street glow",
                        hint = "Search near ground level.",
                        detail = "Street reflections react to rain and nighttime conditions.",
                        region = NormalizedRegion(0.18f, 0.62f, 0.56f, 0.92f),
                        action = WorldHotspotAction.FOCUS
                    )
                )
        )

    private fun coastWorld(): WorldExperience =
        WorldExperience(
            kind = WorldKind.COAST,
            title = "Weather Pier",
            subtitle =
                "Follow the light, water and wind at the edge of the scene.",
            signatureMoment = "Water ripples • stone skipping",
            eyeSpyTargets =
                listOf(
                    EyeSpyTarget(
                        id = "sun",
                        label = "the sun glow",
                        hint = "Search the bright side of the sky.",
                        detail = "Sun position and warmth follow the local time of day.",
                        region = NormalizedRegion(0.62f, 0.08f, 0.94f, 0.34f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "water",
                        label = "the water shimmer",
                        hint = "Look low and near the center.",
                        detail = "The water surface carries wind and precipitation response.",
                        region = NormalizedRegion(0.28f, 0.58f, 0.76f, 0.92f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "wind-mark",
                        label = "the wind mark",
                        hint = "Search the open side of the scene.",
                        detail = "Wind direction changes how particles and foliage move.",
                        region = NormalizedRegion(0.06f, 0.34f, 0.42f, 0.66f),
                        action = WorldHotspotAction.FOCUS
                    )
                )
        )

    private fun forestWorld(): WorldExperience {
        val targets =
            listOf(
                EyeSpyTarget(
                    id = "forest-wall",
                    label = "the misty tree wall",
                    hint = "Look where the trees disappear into fog.",
                    detail = "Fog depth and wind effects build through this forest layer.",
                    region = NormalizedRegion(0.00f, 0.08f, 0.56f, 0.74f),
                    action = WorldHotspotAction.FOCUS
                ),
                EyeSpyTarget(
                    id = "lake-reflection",
                    label = "the lake reflection",
                    hint = "Search the lower half of the scene.",
                    detail = "The lake becomes the visual surface for rain, wind and reflected light.",
                    region = NormalizedRegion(0.10f, 0.55f, 0.92f, 0.98f),
                    action = WorldHotspotAction.FOCUS
                ),
                EyeSpyTarget(
                    id = "far-shore",
                    label = "the far shoreline",
                    hint = "Look through the mist across the water.",
                    detail = "The far shore carries visibility and incoming-weather depth.",
                    region = NormalizedRegion(0.34f, 0.34f, 0.96f, 0.66f),
                    action = WorldHotspotAction.FOCUS
                )
            )

        return WorldExperience(
            kind = WorldKind.FOREST,
            title = "Weather Trail",
            subtitle =
                "Explore a high-resolution mist forest and lake that reacts to the live atmosphere.",
            signatureMoment = "Misty trail • lake reflections • hidden discoveries",
            eyeSpyTargets = targets,
            primaryScene =
                WorldSceneLayer(
                    id = "forest-lake",
                    title = "Weather Trail",
                    subtitle = "Tap real landmarks, drag the scene and pinch to inspect the mist.",
                    assetPath = "worlds/forest_mist_lake.webp",
                    hotspots = targets
                )
        )
    }

}
