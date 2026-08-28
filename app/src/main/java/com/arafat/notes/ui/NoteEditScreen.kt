package com.arafat.notes.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.arafat.notes.data.Note
import com.arafat.notes.util.ImageStorageHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    var currentNote by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLocked by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var exportedPdfFile by remember { mutableStateOf<File?>(null) }
    var showPdfDialog by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            val note = viewModel.getNoteById(noteId)
            if (note != null) {
                currentNote = note
                title = note.title
                content = note.content
                imagePaths = ImageStorageHelper.parseImagePaths(note.imagePathsJson)
                isLocked = note.isLocked
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val newPaths = uris.mapNotNull { uri ->
            viewModel.copyImageToAppFolder(uri)
        }
        if (newPaths.isNotEmpty()) {
            imagePaths = imagePaths + newPaths
        }
    }

    fun saveCurrentNote(onComplete: (Long) -> Unit = {}) {
        viewModel.saveNote(
            id = currentNote?.id ?: 0L,
            title = title,
            content = content,
            imagePaths = imagePaths,
            isLocked = isLocked,
            password = password.ifBlank { null },
            onSaved = { savedId ->
                onComplete(savedId)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == 0L) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = {
                        saveCurrentNote()
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showPasswordDialog = true
                    }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock Note",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = {
                        val tempNote = Note(
                            id = currentNote?.id ?: System.currentTimeMillis(),
                            title = title,
                            content = content,
                            imagePathsJson = ImageStorageHelper.toJson(imagePaths),
                            isLocked = isLocked
                        )
                        viewModel.exportToPdf(tempNote, imagePaths) { file ->
                            if (file != null) {
                                exportedPdfFile = file
                                showPdfDialog = true
                            }
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export to PDF")
                    }

                    IconButton(onClick = {
                        saveCurrentNote()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save Note")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Attached Images Section
            if (imagePaths.isNotEmpty()) {
                Text(
                    text = "Attached Images (${imagePaths.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    itemsIndexed(imagePaths) { index, path ->
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(path)),
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            IconButton(
                                onClick = {
                                    imagePaths = imagePaths.filterIndexed { i, _ -> i != index }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Image",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Image")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attach Images")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Note content...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp),
                maxLines = Int.MAX_VALUE
            )
        }

        // Set Password Dialog
        if (showPasswordDialog) {
            var inputPassword by remember { mutableStateOf(password) }
            var lockState by remember { mutableStateOf(isLocked) }

            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text("Password Lock Settings") },
                text = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Password Lock", modifier = Modifier.weight(1f))
                            Switch(
                                checked = lockState,
                                onCheckedChange = { lockState = it }
                            )
                        }

                        if (lockState) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                label = { Text("Set Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (currentNote?.passwordHash != null && inputPassword.isBlank())
                                    "Leave blank to keep existing password"
                                else "Enter a password to protect this note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        isLocked = lockState
                        if (lockState && inputPassword.isNotBlank()) {
                            password = inputPassword
                        } else if (!lockState) {
                            password = ""
                        }
                        showPasswordDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // PDF Exported Dialog
        if (showPdfDialog && exportedPdfFile != null) {
            val pdfFile = exportedPdfFile!!
            AlertDialog(
                onDismissRequest = { showPdfDialog = false },
                title = { Text("PDF Exported Successfully") },
                text = { Text("PDF saved at:\n${pdfFile.absolutePath}") },
                confirmButton = {
                    TextButton(onClick = {
                        showPdfDialog = false
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                pdfFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPdfDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
