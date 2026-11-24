package com.energypoints.finder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Один DataStore на всё приложение. Делегат объявлен на уровне файла,
 * поэтому случайно создать второй экземпляр нельзя.
 */
private val Context.notesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_notes"
)

/**
 * Пользовательские заметки.
 *
 * Хранятся только на устройстве, в DataStore Preferences.
 * Ключ: note_<район>_<тип>_<код> (см. Point.noteKey).
 */
class NotesRepository(private val context: Context) {

    val notes: Flow<Map<String, String>> = context.notesDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            preferences.asMap()
                .mapNotNull { (key, value) ->
                    val text = value as? String ?: return@mapNotNull null
                    if (!key.name.startsWith(NOTE_PREFIX) || text.isBlank()) {
                        null
                    } else {
                        key.name to text
                    }
                }
                .toMap()
        }

    suspend fun saveNote(noteKey: String, text: String) {
        val trimmed = text.trim()
        context.notesDataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(stringPreferencesKey(noteKey))
            } else {
                preferences[stringPreferencesKey(noteKey)] = trimmed
            }
        }
    }

    suspend fun deleteNote(noteKey: String) {
        context.notesDataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(noteKey))
        }
    }

    companion object {
        const val NOTE_PREFIX = "note_"
    }
}
