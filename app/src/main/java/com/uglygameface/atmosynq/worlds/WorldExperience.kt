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

data class WorldExperience(
    val kind: WorldKind,
    val title: String,
    val subtitle: String,
    val signatureMoment: String,
    val eyeSpyTargets: List<EyeSpyTarget>
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

    private fun cabinWorld(): WorldExperience =
        WorldExperience(
            kind = WorldKind.CABIN,
            title = "Fireside Highlands",
            subtitle =
                "A warm retreat inside the same living weather system.",
            signatureMoment = "Cabin fireside • marshmallow roast",
            eyeSpyTargets =
                listOf(
                    EyeSpyTarget(
                        id = "ridge",
                        label = "the high ridge",
                        hint = "Look above the horizon.",
                        detail = "The ridge is the natural overlook for this world.",
                        region = NormalizedRegion(0.1f, 0.18f, 0.48f, 0.42f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "cloud",
                        label = "the wandering cloud",
                        hint = "Search the upper sky.",
                        detail = "Cloud structure shifts with the live weather state.",
                        region = NormalizedRegion(0.52f, 0.08f, 0.84f, 0.34f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "warm-light",
                        label = "the warm light",
                        hint = "Look low in the shelter line.",
                        detail = "A warm shelter light marks the cabin entry point.",
                        region = NormalizedRegion(0.52f, 0.48f, 0.77f, 0.74f),
                        action = WorldHotspotAction.ENTER
                    )
                )
        )

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

    private fun forestWorld(): WorldExperience =
        WorldExperience(
            kind = WorldKind.FOREST,
            title = "Weather Trail",
            subtitle =
                "Step past the dashboard and explore the atmosphere.",
            signatureMoment = "Wind trail • hidden woodland details",
            eyeSpyTargets =
                listOf(
                    EyeSpyTarget(
                        id = "tree-line",
                        label = "the tree line",
                        hint = "Look along the lower-left edge.",
                        detail = "The tree line is the main wind-reactive foreground zone.",
                        region = NormalizedRegion(0.03f, 0.46f, 0.46f, 0.84f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "cloud",
                        label = "the small cloud",
                        hint = "Search the upper half.",
                        detail = "Cloud cover and brightness follow the active weather report.",
                        region = NormalizedRegion(0.48f, 0.06f, 0.82f, 0.36f),
                        action = WorldHotspotAction.FOCUS
                    ),
                    EyeSpyTarget(
                        id = "horizon",
                        label = "the far horizon",
                        hint = "Look past the foreground.",
                        detail = "The distant horizon carries visibility, fog and incoming weather.",
                        region = NormalizedRegion(0.52f, 0.34f, 0.96f, 0.6f),
                        action = WorldHotspotAction.FOCUS
                    )
                )
        )
}
