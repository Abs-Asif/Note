package com.arafat.notes

import com.arafat.notes.data.Note
import com.arafat.notes.util.ImageStorageHelper
import com.arafat.notes.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteUnitTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val rawPassword = "secret_password_123"
        val hash = SecurityUtils.hashPassword(rawPassword)

        assertTrue(SecurityUtils.verifyPassword(rawPassword, hash))
        assertFalse(SecurityUtils.verifyPassword("wrong_password", hash))
    }

    @Test
    fun testImagePathsJsonSerialization() {
        val imagePaths = listOf("/path/to/img1.jpg", "/path/to/img2.jpg")
        val json = ImageStorageHelper.toJson(imagePaths)
        val parsedPaths = ImageStorageHelper.parseImagePaths(json)

        assertEquals(2, parsedPaths.size)
        assertEquals("/path/to/img1.jpg", parsedPaths[0])
        assertEquals("/path/to/img2.jpg", parsedPaths[1])
    }

    @Test
    fun testNoteCreation() {
        val note = Note(
            title = "Test Note",
            content = "This is a test note content"
        )
        assertEquals("Test Note", note.title)
        assertEquals("This is a test note content", note.content)
        assertFalse(note.isLocked)
    }
}
