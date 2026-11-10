package com.localglasses.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class SparklineDataTest {

    @Test
    fun shortSeriesIsUnchanged() {
        assertEquals(listOf(1.0, 2.0, 3.0), SparklineData.downsample(listOf(1.0, 2.0, 3.0), 120))
    }

    @Test
    fun longSeriesIsDownsampledToTheLimit() {
        val values = (0 until 500).map { it.toDouble() }

        val downsampled = SparklineData.downsample(values, 120)

        assertEquals(120, downsampled.size)
        assertEquals(0.0, downsampled.first())
        assertEquals(499.0, downsampled.last())
    }

    @Test
    fun singlePointLimitKeepsTheFirstValue() {
        assertEquals(listOf(1.0), SparklineData.downsample(listOf(1.0, 2.0), 1))
    }

    @Test
    fun nullValuesCreateSegmentsAndAreNotZero() {
        val values = listOf(1.0, 2.0, null, null, 4.0, 5.0, null, 7.0)

        assertEquals(listOf(0..1, 4..5, 7..7), SparklineData.segments(values))
    }

    @Test
    fun allNullOrEmptyHasNoSegments() {
        assertEquals(emptyList<IntRange>(), SparklineData.segments(emptyList()))
        assertEquals(emptyList<IntRange>(), SparklineData.segments(listOf(null, null)))
    }
}
