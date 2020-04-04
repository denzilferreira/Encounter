package com.awareframework.encounter.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Stats::class,
        Encounter::class
    ], version = 1, exportSchema = true
)

abstract class EncounterDatabase : RoomDatabase() {
    abstract fun UserDao(): UserDao
    abstract fun StatsDao(): StatsDao
    abstract fun EncounterDao(): EncounterDao
}