package com.reelpal.tracker.data

import androidx.room.Entity

@Entity(tableName = "reel_counts", primaryKeys = ["date", "packageName"])
data class ReelScrollEntity(
    val date: String,       // yyyy-MM-dd, local device date
    val packageName: String,
    val count: Int
)
