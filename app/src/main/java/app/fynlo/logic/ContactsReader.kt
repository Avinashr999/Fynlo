package app.fynlo.logic

import android.content.Context
import android.provider.ContactsContract

/**
 * C22 (3.2.71) — read system contacts for People import.
 *
 * Queries `ContactsContract.CommonDataKinds.Phone` (one row per phone
 * number — so a contact with two numbers shows up twice). We dedup by
 * normalised phone digits so the import dialog doesn't show "Priya
 * (mobile)" and "Priya (home)" as two confusing entries when both go
 * to the same person.
 *
 * Pure data fetch — no UI. Caller checks the READ_CONTACTS permission
 * before invoking. Returns an empty list on permission denied, query
 * failure, or a device without a contacts provider (uncommon but
 * possible on stripped-down Android distributions).
 */
object ContactsReader {

    /** Minimum surface for the import dialog row. */
    data class Entry(val name: String, val phone: String)

    /**
     * Read all phone-bearing contacts. Sorted alphabetically by name so
     * the import dialog is browseable.
     */
    fun read(context: Context): List<Entry> {
        val cr = context.contentResolver
        val cursor = runCatching {
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )
        }.getOrNull() ?: return emptyList()

        val out = LinkedHashMap<String, Entry>()  // key = name+normalisedPhone
        cursor.use { c ->
            val nameIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || phoneIdx < 0) return emptyList()
            while (c.moveToNext()) {
                val name  = c.getString(nameIdx)?.trim().orEmpty()
                val phone = c.getString(phoneIdx)?.trim().orEmpty()
                if (name.isBlank() || phone.isBlank()) continue
                val normalised = normalisePhone(phone)
                val key = "$name|$normalised"
                if (key !in out) {
                    out[key] = Entry(name = name, phone = phone)
                }
            }
        }
        return out.values.toList()
    }

    /** Strip spaces / dashes / parens / leading + for dedup comparison only.
     *  The user-facing phone keeps its original formatting. */
    private fun normalisePhone(raw: String): String =
        raw.filter { it.isDigit() }
}
