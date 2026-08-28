package com.arafat.notes.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arafat.notes.data.Note
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object SecurityUtils {
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, hash: String?): Boolean {
        if (hash == null) return false
        return hashPassword(password) == hash
    }
}

object ImageStorageHelper {
    private const val DELIMITER = "|||IMAGE_PATH_SEP|||"

    fun parseImagePaths(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return json.split(DELIMITER).filter { it.isNotBlank() }
    }

    fun toJson(paths: List<String>): String {
        if (paths.isEmpty()) return "[]"
        return paths.joinToString(DELIMITER)
    }

    fun copyImageToAppStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val destFile = File(context.filesDir, fileName)
            FileOutputStream(destFile).use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

object PdfExporter {
    fun exportNoteToPdf(context: Context, note: Note, imagePaths: List<String>): File? {
        val pdfDocument = PdfDocument()

        val pageWidth = 595 // A4 width in points at 72dpi
        val pageHeight = 842 // A4 height in points at 72dpi
        val margin = 36f

        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }

        val bodyPaint = Paint().apply {
            textSize = 14f
            color = android.graphics.Color.DKGRAY
        }

        var yPosition = margin + 30f

        // Draw Title
        canvas.drawText(note.title.ifBlank { "Untitled Note" }, margin, yPosition, titlePaint)
        yPosition += 40f

        // Draw Content lines
        val lines = note.content.split("\n")
        for (line in lines) {
            val words = line.split(" ")
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val textWidth = bodyPaint.measureText(testLine)
                if (textWidth > (pageWidth - 2 * margin)) {
                    if (yPosition + 20f > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        currentPageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = margin + 30f
                    }
                    canvas.drawText(currentLine, margin, yPosition, bodyPaint)
                    yPosition += 20f
                    currentLine = word
                } else {
                    currentLine = testLine
                }
            }
            if (currentLine.isNotEmpty()) {
                if (yPosition + 20f > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = margin + 30f
                }
                canvas.drawText(currentLine, margin, yPosition, bodyPaint)
                yPosition += 20f
            }
        }

        // Draw Images
        yPosition += 20f
        for (imagePath in imagePaths) {
            val file = File(imagePath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val maxImgWidth = pageWidth - 2 * margin
                    val scale = maxImgWidth / bitmap.width.toFloat()
                    val scaledWidth = maxImgWidth
                    val scaledHeight = bitmap.height * scale

                    if (yPosition + scaledHeight > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        currentPageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = margin + 30f
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth.toInt(), scaledHeight.toInt(), true)
                    canvas.drawBitmap(scaledBitmap, margin, yPosition, null)
                    yPosition += scaledHeight + 20f
                }
            }
        }

        pdfDocument.finishPage(page)

        val fileName = "Note_${note.id}_${System.currentTimeMillis()}.pdf"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                }
            }

            // Also save to external public documents / downloads folder if possible, or internal cache/files for sharing
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val pdfFile = File(downloadsDir, fileName)
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to internal app files
            val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, fileName)
            try {
                FileOutputStream(fallbackFile).use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                fallbackFile
            } catch (ex: Exception) {
                ex.printStackTrace()
                pdfDocument.close()
                null
            }
        }
    }
}
