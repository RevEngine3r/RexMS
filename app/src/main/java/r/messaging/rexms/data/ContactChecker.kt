package r.messaging.rexms.data

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Checks if the given address/phone number is an unknown contact
     * (i.e., not saved in the device contacts).
     *
     * @param address Phone number to check
     * @return true if the contact is unknown (not in contacts), false if it's a saved contact
     */
    fun isUnknownContact(address: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        try {
            context.contentResolver.query(
                uri.buildUpon().appendPath(address).build(),
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                return !cursor.moveToFirst() // If no results, it's an unknown contact
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return true // Default to unknown if we can't check
    }

    /**
     * Get the contact name for a phone number, or null if not found
     */
    fun getContactName(address: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        try {
            context.contentResolver.query(
                uri.buildUpon().appendPath(address).build(),
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
}