package app.pillzilla.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun statusToString(status: OccurrenceStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): OccurrenceStatus = OccurrenceStatus.valueOf(value)
}

@Database(
    entities = [Reminder::class, Occurrence::class, PointsEntry::class, RewardItem::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun points(): PointsDao
    abstract fun rewards(): RewardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pillzilla.db")
                .build()
                .also { instance = it }
        }

        val DEFAULT_REMINDERS = listOf(
            Reminder(id = 1, name = "Morning", hour = 8, minute = 0),
            Reminder(id = 2, name = "Midday", hour = 13, minute = 30),
            Reminder(id = 3, name = "Evening", hour = 21, minute = 0),
        )
    }
}
