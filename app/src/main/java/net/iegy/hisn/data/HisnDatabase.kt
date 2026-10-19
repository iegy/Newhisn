package net.iegy.hisn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        ShortcutEntity::class,
        DownloadEntity::class,
        SavedTabEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HisnDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedTabDao(): SavedTabDao

    companion object {
        const val FILE_NAME = "hisn.db"

        fun create(context: Context, name: String = FILE_NAME): HisnDatabase =
            Room.databaseBuilder(context.applicationContext, HisnDatabase::class.java, name).build()
    }
}
