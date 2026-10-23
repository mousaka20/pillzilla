package app.pillzilla.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One of the three daily reminders. [daysMask] bit 0 = Monday ... bit 6 = Sunday. */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = ALL_DAYS,
    val tagUid: String? = null,
    val tagName: String? = null,
    val enabled: Boolean = true,
) {
    fun isOnDay(isoDayOfWeek: Int): Boolean = daysMask and (1 shl (isoDayOfWeek - 1)) != 0

    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

enum class OccurrenceStatus { PENDING, ON_TIME, LATE, MISSED }

/** One scheduled dose of a reminder on a given day. */
@Entity(
    tableName = "occurrences",
    indices = [Index(value = ["reminderId", "scheduledAt"], unique = true)],
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Int,
    val scheduledAt: Long,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val nudgeCount: Int = 0,
    val confirmedAt: Long? = null,
)

/** Every change to the points balance, positive (earned) or negative (redeemed). */
@Entity(tableName = "points_ledger")
data class PointsEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val delta: Int,
    val reason: String,
)

@Entity(tableName = "rewards")
data class RewardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cost: Int,
    val requiredStreak: Int = 0,
)
