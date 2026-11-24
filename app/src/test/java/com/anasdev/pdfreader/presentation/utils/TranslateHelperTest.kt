package com.anasdev.pdfreader.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateHelperTest {

    @Test
    fun `cleanTextForTranslation rejoins hyphenated line breaks`() {
        val input = "This is an extraor-\ndinary discov-\r\nery in modern science."
        val expected = "This is an extraordinary discovery in modern science."
        val actual = TranslateHelper.cleanTextForTranslation(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `cleanTextForTranslation replaces single line breaks with spaces`() {
        val input = "PDF text often has\nbroken lines inside\na single paragraph."
        val expected = "PDF text often has broken lines inside a single paragraph."
        val actual = TranslateHelper.cleanTextForTranslation(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `cleanTextForTranslation preserves paragraph breaks`() {
        val input = "Paragraph one with\na line break.\n\nParagraph two with\nanother break.\n\n\nParagraph three."
        val expected = "Paragraph one with a line break.\n\nParagraph two with another break.\n\nParagraph three."
        val actual = TranslateHelper.cleanTextForTranslation(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `cleanTextForTranslation trims excessive whitespace and spaces`() {
        val input = "   Lots   of   spaces   \n   across lines.   "
        val expected = "Lots of spaces across lines."
        val actual = TranslateHelper.cleanTextForTranslation(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `cleanTextForTranslation returns empty string for blank input`() {
        assertEquals("", TranslateHelper.cleanTextForTranslation(""))
        assertEquals("", TranslateHelper.cleanTextForTranslation("   \n\n  \t  "))
    }
}
