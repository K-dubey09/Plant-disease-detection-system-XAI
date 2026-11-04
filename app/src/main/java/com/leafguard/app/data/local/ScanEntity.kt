package com.leafguard.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed leaf scan, persisted so the user can review past diagnoses (Phase 5), per-plant
 * history (Phase 6 History/Records), and an image gallery (Phase 6 Images).
 *
 * [sourceImagePath] / [saliencyImagePath] (Phase 6 Images) are absolute paths to the captured leaf
 * and its occlusion-sensitivity overlay saved to internal storage by [com.leafguard.app.ui.scan.ScanViewModel]
 * at scan time, so the Images gallery can render past scans without the in-memory Bitmaps (which
 * live in [com.leafguard.app.data.inference.ScanResultHolder] only for the active result screen and
 * are lost on process death). Both are nullable: scans persisted before Phase 6 have no stored image.
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
    val sourceImagePath: String? = null,
    val saliencyImagePath: String? = null,
)
