package io.github.kovalpv.classbundle.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `empty string has zero tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `estimation is chars divided by 3_5`() {
        assertEquals(100, TokenEstimator.estimate("a".repeat(350)))
        assertEquals(10, TokenEstimator.estimate("a".repeat(35)))
    }

    @Test
    fun `estimation is monotonic`() {
        val small = TokenEstimator.estimate("abc")
        val large = TokenEstimator.estimate("abc".repeat(1000))
        assertTrue(large > small)
    }

    @Test
    fun `estimateOrZero handles null`() {
        assertEquals(0, TokenEstimator.estimateOrZero(null))
        assertEquals(0, TokenEstimator.estimateOrZero(""))
    }
}