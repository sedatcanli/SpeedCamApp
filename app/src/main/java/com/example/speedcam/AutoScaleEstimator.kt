package com.example.speedcam

/**
 * Otomatik ölçek kestirici: bilinen gerçek boyutlu nesnelerin
 * piksel boylarından metre/piksel gözlemleri toplar, medyan alır.
 */
class AutoScaleEstimator(var windowMs: Long = 3000L) {
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun add(obsMpp: Float, nowMs: Long) {
        if (obsMpp <= 0f || obsMpp.isNaN()) return
        samples.addLast(Pair(nowMs, obsMpp))
        prune(nowMs)
    }

    fun median(nowMs: Long): Float? {
        prune(nowMs)
        if (samples.size < 3) return null
        val sorted = samples.map { it.second }.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    fun count(nowMs: Long): Int {
        prune(nowMs)
        return samples.size
    }

    fun clear() {
        samples.clear()
    }

    private fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().first > windowMs) {
            samples.removeFirst()
        }
    }
}
