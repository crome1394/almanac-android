package app.crome1394.almanac.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Email suggestions for adding guests (only when contacts access was given). */
object ContactsSearch {
    data class Hit(val name: String, val email: String)

    suspend fun find(c: Context, q: String, limit: Int = 8): List<Hit> = withContext(Dispatchers.IO) {
        if (q.isBlank() || !Permissions.hasContacts(c)) return@withContext emptyList()
        runCatching {
            c.contentResolver.query(
                Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(q.trim())),
                arrayOf(Email.DISPLAY_NAME_PRIMARY, Email.ADDRESS), null, null, null,
            )?.use { cur ->
                buildList {
                    while (cur.moveToNext() && size < limit) {
                        val email = cur.getString(1) ?: continue
                        add(Hit(cur.getString(0) ?: "", email))
                    }
                }
            }.orEmpty().distinctBy { it.email.lowercase() }
        }.getOrDefault(emptyList())
    }

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun isEmail(s: String) = s.length <= 254 && EMAIL.matches(s.trim())
}
