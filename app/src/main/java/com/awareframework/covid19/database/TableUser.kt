package com.awareframework.covid19.database

import androidx.room.*

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true) var uid: Int?,
    @ColumnInfo(name = "uuid") var uuid: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "contact") var contact: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB, name = "photo") var photo: ByteArray?
)

@Dao
interface UserDao {
    @Transaction
    @Insert
    fun insert(user: User)

    @Query("SELECT * FROM user ORDER BY timestamp DESC LIMIT 1")
    fun getUser(): Array<User>
}