package app.pillzilla.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY id")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY id")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Int): Reminder?

    @Query("SELECT * FROM reminders WHERE tagUid = :uid LIMIT 1")
    suspend fun findByTag(uid: String): Reminder?

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM reminders")
    suspend fun maxId(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Update
    suspend fun update(reminder: Reminder)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrences WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    fun observeBetween(from: Long, to: Long): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    suspend fun between(from: Long, to: Long): List<Occurrence>

    @Query("SELECT * FROM occurrences WHERE reminderId = :reminderId AND status = 'PENDING' ORDER BY scheduledAt DESC LIMIT 1")
    suspend fun pendingFor(reminderId: Int): Occurrence?

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING'")
    fun observePending(): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE reminderId = :reminderId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun find(reminderId: Int, scheduledAt: Long): Occurrence?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: Occurrence): Long

    @Update
    suspend fun update(occurrence: Occurrence)

    @Query("DELETE FROM occurrences WHERE reminderId = :reminderId AND status = 'PENDING'")
    suspend fun deletePendingFor(reminderId: Int)
}

@Dao
interface PointsDao {
    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    suspend fun balance(): Int

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger WHERE delta > 0")
    fun observeLifetime(): Flow<Int>

    @Query("SELECT * FROM points_ledger ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PointsEntry>>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger WHERE delta > 0 AND at >= :from AND at < :to")
    fun observeEarnedBetween(from: Long, to: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: PointsEntry)
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY cost")
    fun observeAll(): Flow<List<RewardItem>>

    @Upsert
    suspend fun upsert(reward: RewardItem)

    @Delete
    suspend fun delete(reward: RewardItem)
}
