package com.cybereun.dayflow.data

import androidx.room.*

@Entity(tableName="planner_records")
data class PlannerRecord(@PrimaryKey val id:String,val json:String)
@Dao interface PlannerDao {
    @Query("SELECT * FROM planner_records WHERE id = :id") suspend fun get(id:String):PlannerRecord?
    @Query("SELECT * FROM planner_records") suspend fun all():List<PlannerRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(record:PlannerRecord)
    @Query("DELETE FROM planner_records WHERE id = :id") suspend fun delete(id:String)
}
@Database(entities=[PlannerRecord::class],version=1,exportSchema=false)
abstract class PlannerDatabase:RoomDatabase() {abstract fun records():PlannerDao}
