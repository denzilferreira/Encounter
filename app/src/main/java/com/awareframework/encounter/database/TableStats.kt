package com.awareframework.encounter.database

import android.database.Cursor
import androidx.room.*

@Entity(tableName = "stats")
data class Stats(
    @PrimaryKey(autoGenerate = true) var uid: Int?,
    @ColumnInfo(name = "country") var country : String,
    @ColumnInfo(name = "timestamp") var timestamp : Long,
    @ColumnInfo(name = "confirmed") var confirmed : Long,
    @ColumnInfo(name = "deaths") var deaths : Long,
    @ColumnInfo(name = "recovered") var recovered : Long
)

@Dao
interface StatsDao {
    @Transaction @Insert
    fun insert(data: Stats)

    @Query("DELETE FROM stats")
    fun clear()

    @Query("SELECT * FROM stats where country like :country ORDER BY timestamp ASC")
    fun getCountryData(country: String) : Array<Stats>

    @Query("SELECT * FROM stats where country like :country AND timestamp = :timestamp")
    fun getCountryDayData(country: String, timestamp: Long) : Array<Stats>

    @Transaction @Update
    fun update(data : Stats)

    @Query("SELECT * FROM stats GROUP BY country")
    fun getCountries() : Array<Stats>

    @Query("SELECT STRFTIME('%W', datetime(timestamp/1000, 'unixepoch','localtime')) AS week, confirmed FROM stats WHERE country LIKE :country ORDER BY timestamp ASC")
    fun getWeekly(country: String) : Cursor
}