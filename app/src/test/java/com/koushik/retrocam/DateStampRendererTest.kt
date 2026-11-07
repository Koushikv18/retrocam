package com.koushik.retrocam

import com.koushik.retrocam.data.model.DateStampConfig
import com.koushik.retrocam.util.DateStampRenderer
import org.junit.Assert.*
import org.junit.Test

class DateStampRendererTest {

    @Test
    fun testFormatCurrentDateDefault() {
        val config = DateStampConfig(format = "'YY  M  D")
        val formatted = DateStampRenderer.formatCurrentDate(config)

        assertNotNull(formatted)
        assertTrue("Formatted date should start with apostrophe", formatted.startsWith("'"))
        // e.g. "'24  11  07" has length ~10
        assertTrue("Formatted date should contain spaces", formatted.contains("  "))
    }

    @Test
    fun testFormatCurrentDateIso() {
        val config = DateStampConfig(format = "YYYY-MM-DD")
        val formatted = DateStampRenderer.formatCurrentDate(config)

        assertNotNull(formatted)
        assertTrue("Should contain hyphens", formatted.contains("-"))
        assertEquals("YYYY-MM-DD has length 10", 10, formatted.length)
    }

    @Test
    fun testFormatCurrentDateVhs() {
        val config = DateStampConfig(format = "M D 'YY HH:mm")
        val formatted = DateStampRenderer.formatCurrentDate(config)

        assertNotNull(formatted)
        assertTrue("Should contain colon for time", formatted.contains(":"))
        assertTrue("Should contain apostrophe for year", formatted.contains("'"))
    }
}

