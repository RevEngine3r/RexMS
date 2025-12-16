package r.messaging.rexms.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import r.messaging.rexms.data.local.ConversationDao
import r.messaging.rexms.data.local.MessageDao
import r.messaging.rexms.data.local.SmsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideSmsDatabase(
        @ApplicationContext context: Context
    ): SmsDatabase {
        return Room.databaseBuilder(
            context,
            SmsDatabase::class.java,
            "rexms_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideConversationDao(database: SmsDatabase): ConversationDao {
        return database.conversationDao()
    }
    
    @Provides
    @Singleton
    fun provideMessageDao(database: SmsDatabase): MessageDao {
        return database.messageDao()
    }
}