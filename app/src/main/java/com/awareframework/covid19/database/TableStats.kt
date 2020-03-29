package com.awareframework.covid19.database

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

    @Query("Select * from stats where timestamp = :timestamp")
    fun getDay(timestamp: Long) : Array<Stats>
}