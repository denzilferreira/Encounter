package com.awareframework.encounter.database

import androidx.room.*

@Entity(tableName = "encounters")
data class Encounter(
    @PrimaryKey(autoGenerate = true) var uid: Int?,
    @ColumnInfo(name = "uuid") var uuid: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "is_found") var isFound : Boolean,
    @ColumnInfo(name = "is_lost") var isLost : Boolean,
    @ColumnInfo(name = "uuid_detected") var uuid_detected: String,
    @ColumnInfo(name = "distance_meters") var distance_meters : Double?,
    @ColumnInfo(name = "distance_accuracy") var distance_accuracy : Int?,
    @ColumnInfo(name = "signal_rssi") var signal_rssi : Int?,
    @ColumnInfo(name = "signal_tx_power") var signal_power : Int?
)

@Dao
interface EncounterDao {
    @Transaction
    @Insert
    fun insert(data: Encounter)
}