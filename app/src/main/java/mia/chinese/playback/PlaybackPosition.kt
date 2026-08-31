package mia.chinese.playback

fun clampPosition(positionMs: Long, durationMs: Long?): Long {
    val safe = positionMs.coerceAtLeast(0L)
    return if (durationMs != null && durationMs > 0L) safe.coerceAtMost(durationMs) else safe
}

fun seekPosition(currentMs: Long, deltaMs: Long, durationMs: Long?): Long {
    val target = when {
        deltaMs > 0L && currentMs > Long.MAX_VALUE - deltaMs -> Long.MAX_VALUE
        deltaMs < 0L && currentMs < Long.MIN_VALUE - deltaMs -> Long.MIN_VALUE
        else -> currentMs + deltaMs
    }
    return clampPosition(target, durationMs)
}
