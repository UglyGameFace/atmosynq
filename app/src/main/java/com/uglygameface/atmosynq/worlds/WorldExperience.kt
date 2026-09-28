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

data class EyeSpyTarget(
    val id: String,
    val label: String,
    val hint: String,
    val xFraction: Float,
    val yFraction: Float
) {
    init {
        require(xFraction in 0f..1f)
        require(yFraction in 0f..1f)
    }
}

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
                        xFraction = 0.30f,
                        yFraction = 0.31f
                    ),
                    EyeSpyTarget(
                        id = "cloud",
                        label = "the wandering cloud",
                        hint = "Search the upper sky.",
                        xFraction = 0.68f,
                        yFraction = 0.20f
                    ),
                    EyeSpyTarget(
                        id = "warm-light",
                        label = "the warm light",
                        hint = "Look low in the shelter line.",
                        xFraction = 0.63f,
                        yFraction = 0.62f
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
                        xFraction = 0.25f,
                        yFraction = 0.23f
                    ),
                    EyeSpyTarget(
                        id = "reflection",
                        label = "a rain reflection",
                        hint = "Search near the lower center.",
                        xFraction = 0.53f,
                        yFraction = 0.72f
                    ),
                    EyeSpyTarget(
                        id = "horizon",
                        label = "the storm horizon",
                        hint = "Look where the world meets the sky.",
                        xFraction = 0.76f,
                        yFraction = 0.45f
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
                        xFraction = 0.58f,
                        yFraction = 0.39f
                    ),
                    EyeSpyTarget(
                        id = "sky-break",
                        label = "the bright sky break",
                        hint = "Look above the buildings.",
                        xFraction = 0.72f,
                        yFraction = 0.20f
                    ),
                    EyeSpyTarget(
                        id = "street-glow",
                        label = "the street glow",
                        hint = "Search near ground level.",
                        xFraction = 0.36f,
                        yFraction = 0.73f
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
                        xFraction = 0.77f,
                        yFraction = 0.22f
                    ),
                    EyeSpyTarget(
                        id = "water",
                        label = "the water shimmer",
                        hint = "Look low and near the center.",
                        xFraction = 0.52f,
                        yFraction = 0.73f
                    ),
                    EyeSpyTarget(
                        id = "wind-mark",
                        label = "the wind mark",
                        hint = "Search the open side of the scene.",
                        xFraction = 0.24f,
                        yFraction = 0.48f
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
                        xFraction = 0.24f,
                        yFraction = 0.62f
                    ),
                    EyeSpyTarget(
                        id = "cloud",
                        label = "the small cloud",
                        hint = "Search the upper half.",
                        xFraction = 0.63f,
                        yFraction = 0.24f
                    ),
                    EyeSpyTarget(
                        id = "horizon",
                        label = "the far horizon",
                        hint = "Look past the foreground.",
                        xFraction = 0.72f,
                        yFraction = 0.48f
                    )
                )
        )
}
