package com.youme.memoria.roomCach

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingConversionTest {

    @Test
    fun floatArraySurvivesTheByteArrayRoundTrip() {
        val embedding = floatArrayOf(0f, -0f, 1.5f, -2.25f, Float.MIN_VALUE, Float.MAX_VALUE)

        assertArrayEquals(embedding, embedding.toByteArray().toFloatArray(), 0f)
    }

    @Test
    fun eachFloatTakesFourLittleEndianBytes() {
        val bytes = floatArrayOf(1f).toByteArray()

        // 1.0f is 0x3F800000
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), bytes)
    }

    @Test
    fun emptyArraysStayEmpty() {
        assertEquals(0, FloatArray(0).toByteArray().size)
        assertEquals(0, ByteArray(0).toFloatArray().size)
    }
}
