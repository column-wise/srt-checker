package io.github.columnwise.trainchecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.github.columnwise.trainchecker.data.model.*

class Converters {
    @TypeConverter fun fromTrainType(v: TrainType): String = v.name
    @TypeConverter fun toTrainType(v: String): TrainType = TrainType.valueOf(v)
    @TypeConverter fun fromSeatType(v: SeatType): String = v.name
    @TypeConverter fun toSeatType(v: String): SeatType = SeatType.valueOf(v)
    @TypeConverter fun fromWatchStatus(v: WatchStatus): String = v.name
    @TypeConverter fun toWatchStatus(v: String): WatchStatus = WatchStatus.valueOf(v)
}

@Database(entities = [WatchJob::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchJobDao(): WatchJobDao
}
