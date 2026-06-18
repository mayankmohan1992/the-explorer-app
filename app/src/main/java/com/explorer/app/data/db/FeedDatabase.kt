package com.explorer.app.data.db

import android.content.Context
import androidx.room.*

@Entity(tableName = "rss_sources")
data class RssSource(
    @PrimaryKey val url: String,
    val title: String
)

@Entity(
    tableName = "rss_articles",
    indices = [Index(value = ["link"], unique = true)]
)
data class RssArticle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false
)

@Dao
interface RssDao {
    @Query("SELECT * FROM rss_sources")
    suspend fun getAllSources(): List<RssSource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: RssSource)

    @Delete
    suspend fun deleteSource(source: RssSource)

    @Query("SELECT * FROM rss_articles ORDER BY id DESC")
    suspend fun getAllArticles(): List<RssArticle>

    @Query("SELECT * FROM rss_articles WHERE sourceUrl = :sourceUrl ORDER BY id DESC")
    suspend fun getArticlesBySource(sourceUrl: String): List<RssArticle>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<RssArticle>)

    @Query("UPDATE rss_articles SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE rss_articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Long, bookmarked: Boolean)

    @Query("SELECT * FROM rss_articles WHERE isBookmarked = 1 ORDER BY id DESC")
    suspend fun getBookmarkedArticles(): List<RssArticle>

    @Query("DELETE FROM rss_articles WHERE sourceUrl = :sourceUrl")
    suspend fun deleteArticlesBySource(sourceUrl: String)
}

@Database(entities = [RssSource::class, RssArticle::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rssDao(): RssDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "explorer_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
