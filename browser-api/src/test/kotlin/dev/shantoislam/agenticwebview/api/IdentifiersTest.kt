package dev.shantoislam.agenticwebview.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdentifiersTest {
    @Test
    fun elementReferenceIsScopedByDocumentAndFrame() {
        val first = ElementRef(DocumentId("doc-1"), FrameId.Main, ElementId("element-1"), ObservationRevision(4))
        val second = first.copy(documentId = DocumentId("doc-2"))

        assertNotEquals(first, second)
        assertEquals("element-1", first.elementId.value)
    }

    @Test
    fun identifiersRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) { DocumentId(" ") }
        assertThrows(IllegalArgumentException::class.java) { FrameId("") }
        assertThrows(IllegalArgumentException::class.java) { ElementId("\t") }
    }
}
