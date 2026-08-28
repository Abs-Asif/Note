package com.arafat.notes.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arafat.notes.data.AppDatabase
import com.arafat.notes.data.Note
import com.arafat.notes.util.ImageStorageHelper
import com.arafat.notes.util.PdfExporter
import com.arafat.notes.util.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = AppDatabase.getDatabase(application).noteDao()

    val searchQuery = MutableStateFlow("")

    private val _allNotes = noteDao.getAllNotes()

    val notesState: StateFlow<List<Note>> = _allNotes
        .combine(searchQuery) { notes, query ->
            if (query.isBlank()) {
                notes
            } else {
                notes.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.content.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val unlockedNoteIds = MutableStateFlow<Set<Long>>(emptySet())

    fun isNoteUnlocked(noteId: Long): Boolean {
        return unlockedNoteIds.value.contains(noteId)
    }

    fun unlockNote(note: Note, passwordInput: String): Boolean {
        if (note.passwordHash == null || SecurityUtils.verifyPassword(passwordInput, note.passwordHash)) {
            unlockedNoteIds.value = unlockedNoteIds.value + note.id
            return true
        }
        return false
    }

    fun lockNote(noteId: Long) {
        unlockedNoteIds.value = unlockedNoteIds.value - noteId
    }

    suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)
    }

    fun saveNote(
        id: Long = 0,
        title: String,
        content: String,
        imagePaths: List<String>,
        isLocked: Boolean,
        password: String?,
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val passwordHash = if (isLocked && !password.isNullOrBlank()) {
                SecurityUtils.hashPassword(password)
            } else if (isLocked && id != 0L) {
                // retain existing password hash if note was already locked and password not changed
                val existing = noteDao.getNoteById(id)
                existing?.passwordHash
            } else null

            val updatedNote = Note(
                id = id,
                title = title,
                content = content,
                imagePathsJson = ImageStorageHelper.toJson(imagePaths),
                isLocked = isLocked,
                passwordHash = passwordHash,
                updatedAt = System.currentTimeMillis()
            )

            if (id == 0L) {
                val newId = noteDao.insertNote(updatedNote)
                if (isLocked) {
                    unlockedNoteIds.value = unlockedNoteIds.value + newId
                }
                onSaved(newId)
            } else {
                noteDao.updateNote(updatedNote)
                if (isLocked) {
                    unlockedNoteIds.value = unlockedNoteIds.value + id
                }
                onSaved(id)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
            lockNote(note.id)
        }
    }

    fun copyImageToAppFolder(uri: Uri): String? {
        return ImageStorageHelper.copyImageToAppStorage(getApplication(), uri)
    }

    fun exportToPdf(note: Note, imagePaths: List<String>, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val file = PdfExporter.exportNoteToPdf(getApplication(), note, imagePaths)
            if (file != null) {
                Toast.makeText(getApplication(), "Exported to PDF: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(getApplication(), "Failed to export PDF", Toast.LENGTH_SHORT).show()
            }
            onResult(file)
        }
    }
}
