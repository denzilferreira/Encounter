package com.awareframework.covid19.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Stats::class,
        Symptoms::class
    ], version = 1, exportSchema = true
)

abstract class CovidDatabase : RoomDatabase() {
    abstract fun UserDao(): UserDao
    abstract fun StatsDao() : StatsDao
    abstract fun SymptomsDao() : SymptomsDao
}