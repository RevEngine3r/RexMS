package r.messaging.rexms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.reactionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "message_reactions")

@Serializable
data class MessageReaction(
    val messageId: Long,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class MessageReactionsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get reaction for a specific message
     */
    fun getReaction(messageId: Long): Flow<String?> {
        val key = stringPreferencesKey("reaction_$messageId")
        return context.reactionsDataStore.data.map { preferences ->
            preferences[key]?.let { jsonString ->
                try {
                    json.decodeFromString<MessageReaction>(jsonString).emoji
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Add or update reaction to a message
     */
    suspend fun addReaction(messageId: Long, emoji: String) {
        val key = stringPreferencesKey("reaction_$messageId")
        val reaction = MessageReaction(messageId, emoji)
        context.reactionsDataStore.edit { preferences ->
            preferences[key] = json.encodeToString(reaction)
        }
    }

    /**
     * Remove reaction from a message
     */
    suspend fun removeReaction(messageId: Long) {
        val key = stringPreferencesKey("reaction_$messageId")
        context.reactionsDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    /**
     * Get all reactions
     */
    fun getAllReactions(): Flow<Map<Long, String>> {
        return context.reactionsDataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith("reaction_") }
                .mapNotNull { (key, value) ->
                    try {
                        val reaction = json.decodeFromString<MessageReaction>(value as String)
                        reaction.messageId to reaction.emoji
                    } catch (e: Exception) {
                        null
                    }
                }
                .toMap()
        }
    }
}