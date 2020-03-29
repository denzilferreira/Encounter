package com.awareframework.encounter.database

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

    @Query("SELECT * FROM stats WHERE timestamp = :timestamp and country like :country")
    fun getCountryDataDay(timestamp: Long, country: String) : Array<Stats>

    @Query("UPDATE stats SET confirmed=:confirmed, deaths=:deaths, recovered=:recovered WHERE timestamp=:timestamp AND country LIKE :country")
    fun update(confirmed: Long, deaths: Long, recovered: Long, timestamp: Long, country: String)

    @Query("SELECT * FROM stats where country like :country ORDER BY timestamp ASC")
    fun getCountryData(country: String) : Array<Stats>
}