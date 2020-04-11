package com.awareframework.encounter.database

import androidx.room.*

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true) var uid: Int?,
    @ColumnInfo(name = "uuid") var uuid: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long
)

@Dao
interface UserDao {
    @Transaction
    @Insert
    fun insert(user: User)

    @Query("SELECT * FROM user ORDER BY timestamp DESC LIMIT 1")
    fun getUser(): Array<User>
}