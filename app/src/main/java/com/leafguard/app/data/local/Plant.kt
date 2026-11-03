package com.leafguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A plant the user is tracking. One row per physical plant the user owns; each future scan is
 * attached to a [Plant] via its id so per-plant disease history is possible. `name` is the
 * user-given label (e.g. "Balcony Tomato"); `species`/`notes` are optional free text.
 */
@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
