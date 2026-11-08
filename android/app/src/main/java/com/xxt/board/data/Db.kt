package com.xxt.board.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "courses", primaryKeys = ["courseId", "classId"])
data class CourseEntity(
    val courseId: String,
    val classId: String,
    val cpi: String,
    val name: String,
    val subject: String,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val key: String,
    val type: String,
    val courseId: String,
    val classId: String,
    val courseName: String,
    val title: String,
    val status: String,
    val publishAt: Long?,
    val dueAt: Long?,
    val submittedAt: Long?,
    val gradedAt: Long?,
    val score: String?,
    val url: String,
    val firstSeenAt: Long,
    val lastSyncAt: Long,
    val detailFetchedAt: Long?,
)

/** 状态迁移历史：提交/批阅时间的推断依据 */
@Entity(tableName = "status_history", primaryKeys = ["taskKey", "changedAt"])
data class StatusHistoryEntity(
    val taskKey: String,
    val status: String,
    val changedAt: Long,
)

@Dao
interface BoardDao {
    @Query("SELECT * FROM tasks ORDER BY (dueAt IS NULL), dueAt ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE key = :key")
    suspend fun task(key: String): TaskEntity?

    @Upsert
    suspend fun upsertTasks(items: List<TaskEntity>)

    @Upsert
    suspend fun upsertCourses(items: List<CourseEntity>)

    @Query("SELECT * FROM courses")
    suspend fun courses(): List<CourseEntity>

    @Query("DELETE FROM tasks WHERE key = :key")
    suspend fun deleteTask(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(item: StatusHistoryEntity)

    @Query("SELECT * FROM status_history WHERE taskKey = :key ORDER BY changedAt DESC")
    suspend fun history(key: String): List<StatusHistoryEntity>
}

@Database(
    entities = [CourseEntity::class, TaskEntity::class, StatusHistoryEntity::class, ScheduleEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BoardDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        /** v2 只加了一张课表，不动 tasks / status_history */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, " +
                        "`location` TEXT NOT NULL, `weekday` INTEGER NOT NULL, " +
                        "`startSection` INTEGER NOT NULL, `endSection` INTEGER NOT NULL, " +
                        "`weeks` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "board.db")
                // 绝不 destructive migration：会清空状态历史，提交时间推断就废了
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

fun TaskEntity.toDomain(): Task = Task(
    key = key,
    type = runCatching { TaskType.valueOf(type) }.getOrDefault(TaskType.WORK),
    courseId = courseId,
    classId = classId,
    courseName = courseName,
    title = title,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.TODO),
    publishAt = publishAt,
    dueAt = dueAt,
    submittedAt = submittedAt,
    gradedAt = gradedAt,
    score = score,
    url = url,
)
