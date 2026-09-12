package com.margin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.DayOfWeek

private val Context.referenceStore: DataStore<Preferences> by preferencesDataStore(name = "timetable_reference")

/**
 * The timetable the user last confirmed: the shipped one, a reviewed import, or the week after
 * their own edits. The stored week is checked against it, so a class that goes missing or
 * changes without the user doing it shows up as something to review instead of vanishing.
 */
class TimetableReferenceRepository(private val context: Context) {

    private object Keys {
        val entries = stringPreferencesKey("entries")
        val source = stringPreferencesKey("source")
    }

    /** Null until something has been confirmed. */
    val reference: Flow<List<TimetableEntry>?> = context.referenceStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> prefs[Keys.entries]?.let(::decode) }

    val source: Flow<String?> = context.referenceStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> prefs[Keys.source] }

    suspend fun get(): List<TimetableEntry>? = reference.first()

    suspend fun set(entries: List<TimetableEntry>, source: String) {
        context.referenceStore.edit { prefs ->
            prefs[Keys.entries] = encode(entries)
            prefs[Keys.source] = source
        }
    }

    @Serializable
    private data class Row(
        val day: Int,
        val start: Int,
        val end: Int,
        val code: String? = null,
        val title: String,
        val kind: String,
        val faculty: String? = null,
        val location: String? = null,
    )

    private fun encode(entries: List<TimetableEntry>): String = json.encodeToString(
        ListSerializer(Row.serializer()),
        entries.filter { it.active }.map {
            Row(it.dayOfWeek.value, it.start, it.end, it.subjectCode, it.title, it.kind.key, it.faculty, it.location)
        },
    )

    private fun decode(text: String): List<TimetableEntry>? = runCatching {
        json.decodeFromString(ListSerializer(Row.serializer()), text).map {
            TimetableEntry(
                dayOfWeek = DayOfWeek.of(it.day),
                start = it.start,
                end = it.end,
                subjectCode = it.code,
                title = it.title,
                kind = TimetableKind.fromKey(it.kind),
                faculty = it.faculty,
                location = it.location,
            )
        }
    }.getOrNull()

    companion object {
        const val SOURCE_SEED = "seed"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_EDIT = "edit"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
