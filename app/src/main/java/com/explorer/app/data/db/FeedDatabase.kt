package com.explorer.app.data.db

import android.content.Context
import androidx.room.*

@Entity(tableName = "rss_sources")
data class RssSource(
    @PrimaryKey val url: String,
    val title: String,
    val category: String? = null
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

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val url: String,
    val title: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs")
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songUri: String,
    val title: String
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

    // Bookmarks operations
    @Query("SELECT * FROM bookmarks ORDER BY dateAdded DESC")
    suspend fun getAllBookmarks(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    // Playlists operations
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Playlist Songs operations
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongsForPlaylist(playlistId: Long): List<PlaylistSong>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSong)

    @Delete
    suspend fun deletePlaylistSong(song: PlaylistSong)
}

@Database(
    entities = [
        RssSource::class,
        RssArticle::class,
        Bookmark::class,
        Playlist::class,
        PlaylistSong::class
    ],
    version = 3,
    exportSchema = false
)
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
