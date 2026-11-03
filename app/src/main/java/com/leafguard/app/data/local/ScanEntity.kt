package com.leafguard.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed leaf scan, persisted so the user can review past diagnoses (Phase 5) and per-plant
 * history can be built on it (Phase 6). The Bitmaps (source leaf + saliency overlay) are NOT
 * serialized -- they stay in [com.leafguard.app.data.inference.ScanResultHolder] (in-memory) for
 * the result screen; this row holds the diagnosis metadata and the optional link to the [Plant].
 *
 * [plantId] is nullable: a scan launched from the Scan tab is unattached; a scan launched from
 * PlantDetail ("Scan this plant") is attached. Deleting a plant sets attached scans' [plantId] to
 * NULL (ON DELETE SET NULL) rather than cascading, so the diagnosis history survives.
 *
 * [labelName] is the raw PlantVillage label (e.g. "Apple___Apple_scab", matching labels.txt);
 * [labelCondition] is the human-readable condition; [confidence] is the model's top-1 probability.
 */
@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("plantId")],
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long? = null,
    val labelName: String,
    val labelCondition: String,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis(),
)
