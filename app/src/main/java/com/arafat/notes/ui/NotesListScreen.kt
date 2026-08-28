package com.arafat.notes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arafat.notes.data.Note
import com.arafat.notes.util.ImageStorageHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit
) {
    val notes by viewModel.notesState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearching by remember { mutableStateOf(false) }

    var selectedNoteForPassword by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search notes...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Notes")
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            viewModel.searchQuery.value = ""
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNoteClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { paddingValues ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No notes found matching '$searchQuery'" else "No notes yet. Tap + to create one!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(notes, key = { it.id }) { note ->
                    val isUnlocked = !note.isLocked || viewModel.isNoteUnlocked(note.id)
                    NoteCard(
                        note = note,
                        isUnlocked = isUnlocked,
                        onClick = {
                            if (note.isLocked && !isUnlocked) {
                                selectedNoteForPassword = note
                            } else {
                                onNoteClick(note.id)
                            }
                        },
                        onDeleteClick = {
                            noteToDelete = note
                        }
                    )
                }
            }
        }

        // Password prompt dialog
        selectedNoteForPassword?.let { note ->
            PasswordUnlockDialog(
                noteTitle = note.title,
                onDismiss = { selectedNoteForPassword = null },
                onConfirm = { password ->
                    val success = viewModel.unlockNote(note, password)
                    if (success) {
                        selectedNoteForPassword = null
                        onNoteClick(note.id)
                    }
                    success
                }
            )
        }

        // Delete confirmation dialog
        noteToDelete?.let { note ->
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text("Delete Note") },
                text = { Text("Are you sure you want to delete '${note.title.ifBlank { "Untitled Note" }}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteNote(note)
                        noteToDelete = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val imageCount = remember(note.imagePathsJson) {
        ImageStorageHelper.parseImagePaths(note.imagePathsJson).size
    }

    val formattedDate = remember(note.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDeleteClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (note.isLocked) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Locked Note",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (note.isLocked && !isUnlocked) {
                Text(
                    text = "🔒 Password Protected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (imageCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Images attached",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$imageCount image${if (imageCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordUnlockDialog(
    noteTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock Note") },
        text = {
            Column {
                Text("Enter password to view '${noteTitle.ifBlank { "Untitled Note" }}':")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text(
                        text = "Incorrect password. Try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val success = onConfirm(password)
                if (!success) {
                    showError = true
                }
            }) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
