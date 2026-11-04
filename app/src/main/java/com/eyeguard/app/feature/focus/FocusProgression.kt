package com.eyeguard.app.feature.focus

/** 七个大境界，每境十层；普通 1 修为/分钟，严格为 1.5 倍并向上取整。 */
object FocusProgression {
    const val REALM_COUNT = 7
    const val LAYERS_PER_REALM = 10
    const val BASE_LAYER_XP = 50

    val thresholds: List<Int> = buildList {
        add(0)
        var total = 0
        for (realm in 0 until REALM_COUNT) {
            val layerXp = BASE_LAYER_XP * (1 shl realm)
            repeat(LAYERS_PER_REALM) {
                total += layerXp
                add(total)
            }
        }
    }

    data class Level(
        val realmIndex: Int,
        val layer: Int,
        val currentXp: Int,
        val currentThreshold: Int,
        val nextThreshold: Int?,
        val progress: Float,
        val xpToNext: Int
    ) {
        val index: Int get() = realmIndex
        val isMax: Boolean get() = nextThreshold == null
    }

    fun levelFor(xp: Int): Level {
        val safeXp = xp.coerceAtLeast(0)
        if (safeXp >= thresholds.last()) {
            return Level(
                realmIndex = REALM_COUNT - 1,
                layer = LAYERS_PER_REALM,
                currentXp = safeXp,
                currentThreshold = thresholds[thresholds.lastIndex - 1],
                nextThreshold = null,
                progress = 1f,
                xpToNext = 0
            )
        }
        val stageIndex = thresholds.indexOfLast { safeXp >= it }
            .coerceIn(0, REALM_COUNT * LAYERS_PER_REALM - 1)
        val current = thresholds[stageIndex]
        val next = thresholds.getOrNull(stageIndex + 1)
        val progress = if (next == null) 1f
        else ((safeXp - current).toFloat() / (next - current)).coerceIn(0f, 1f)
        return Level(
            realmIndex = stageIndex / LAYERS_PER_REALM,
            layer = stageIndex % LAYERS_PER_REALM + 1,
            currentXp = safeXp,
            currentThreshold = current,
            nextThreshold = next,
            progress = progress,
            xpToNext = next?.let { (it - safeXp).coerceAtLeast(0) } ?: 0
        )
    }

    fun realmStartXp(realmIndex: Int): Int =
        thresholds[(realmIndex.coerceIn(0, REALM_COUNT - 1)) * LAYERS_PER_REALM]

    fun xpForMinutes(minutes: Int, strictMode: Boolean): Int {
        val safeMinutes = minutes.coerceAtLeast(0)
        return if (strictMode) (safeMinutes * 3 + 1) / 2 else safeMinutes
    }
}
