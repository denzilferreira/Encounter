package com.awareframework.covid19.database

import androidx.room.*

@Entity(tableName = "symptoms")
data class Symptoms(
    @PrimaryKey(autoGenerate = true) var uid : Int?,
    @ColumnInfo(name = "uuid") var uuid: String,
    @ColumnInfo(name = "timestamp") var timestamp : Long,
    @ColumnInfo(name = "fever") var fever : Boolean,
    @ColumnInfo(name = "cough") var cough : Boolean,
    @ColumnInfo(name = "fatigue") var fatigue : Boolean,
    @ColumnInfo(name = "breathless") var breath : Boolean,
    @ColumnInfo(name = "smell") var smell : Boolean,
    @ColumnInfo(name = "taste") var taste : Boolean,
    @ColumnInfo(name = "headache") var head : Boolean,
    @ColumnInfo(name = "throat") var throat : Boolean,
    @ColumnInfo(name = "body") var body : Boolean
)

@Dao
interface SymptomsDao {
    @Transaction
    @Insert
    fun insert(symtom : Symptoms)

    @Query("SELECT * FROM symptoms WHERE timestamp = :timestamp")
    fun getSymptoms(timestamp : Long) : Array<Symptoms>
}