package com.awareframework.encounter.database

import androidx.room.*

@Entity(tableName = "encounters")
data class Encounter(
    @PrimaryKey(autoGenerate = true) var uid: Int?,
    @ColumnInfo(name = "uuid") var uuid: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "uuid_detected") var uuid_detected: String
)

@Dao
interface EncounterDao {
    @Transaction
    @Insert
    fun insert(data: Encounter)

    @Query("SELECT * FROM encounters WHERE timestamp >= :timestamp GROUP BY uuid_detected")
    fun getToday(timestamp: Long) : Array<Encounter>

    @Query("SELECT * FROM encounters WHERE timestamp between :start and :end")
    fun getWindow(start: Long, end: Long) : Array<Encounter>

    @Query("SELECT * FROM encounters")
    fun getAll() : Array<Encounter>

    @Query("DELETE FROM encounters")
    fun clear()
}