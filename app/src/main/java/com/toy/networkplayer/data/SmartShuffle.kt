package com.toy.networkplayer.data

import kotlin.math.pow
import kotlin.random.Random

enum class ShuffleBias(val label: String) {
    EVEN("골고루 무작위"),
    LESS_WATCHED("덜 본 영상 위주"),
    OFTEN_WATCHED("자주 본 영상 위주"),
}

data class ShuffleOptions(
    /** 0이면 길이 조건 없음 */
    val minMinutes: Int = 0,
    val onlyUnwatched: Boolean = false,
    val bias: ShuffleBias = ShuffleBias.EVEN,
)

/** 조건으로 거르고, 시청 기록에 따라 가중치를 준 무작위 순서로 섞는다. */
object SmartShuffle {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun needsDuration(options: ShuffleOptions) = options.minMinutes > 0

    fun shuffle(
        server: ServerConfig,
        videos: List<SmbEntry>,
        options: ShuffleOptions,
        random: Random = Random.Default,
    ): List<SmbEntry> {
        val now = System.currentTimeMillis()
        val minMs = options.minMinutes * 60_000L
        return videos
            .map { it to WatchHistory.get(server, it) }
            .filter { (_, r) -> minMs == 0L || r.durationMs >= minMs }
            .filter { (_, r) -> !options.onlyUnwatched || r.playCount == 0 }
            .map { (e, r) ->
                val weight = when (options.bias) {
                    ShuffleBias.EVEN -> 1.0
                    // 본 횟수가 적을수록, 마지막으로 본 지 오래될수록 앞에 올 확률이 높다.
                    ShuffleBias.LESS_WATCHED -> {
                        val days = if (r.lastPlayed == 0L) 30.0 else ((now - r.lastPlayed) / DAY_MS).toDouble().coerceAtMost(30.0)
                        (1.0 + days / 10.0) / (1.0 + r.playCount)
                    }
                    ShuffleBias.OFTEN_WATCHED -> 1.0 + r.playCount * 2.0
                }
                // 가중 무작위 정렬 (Efraimidis–Spirakis): key = u^(1/w), 큰 순서.
                e to random.nextDouble().pow(1.0 / weight)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
