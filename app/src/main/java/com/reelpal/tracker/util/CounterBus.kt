package com.reelpal.tracker.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lightweight in-process pub/sub so the overlay bubble can react instantly
 * to new counts without polling the database. The source of truth is still
 * Room (ReelRepository); this is just a fast mirror of "today's" counts.
 */
object CounterBus {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun setCount(packageName: String, count: Int) {
        counts.value = counts.value.toMutableMap().apply { put(packageName, count) }
    }
}
