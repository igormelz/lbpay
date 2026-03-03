package ru.openfs.lbpay.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NdsCalculatorTest {

    @Test
    void calcNDS() {
        assertEquals(53, NdsCalculator.extractNDS5(1111));
        assertEquals(699, NdsCalculator.extractNDS5(14677));
        assertEquals(697, NdsCalculator.extractNDS5(14631));
        assertEquals(695, NdsCalculator.extractNDS5(14601));

        assertEquals(17833, NdsCalculator.extractNDS20(107000));
        assertEquals(169733, NdsCalculator.extractNDS20(1018400));
    }

    @Test
    void testNeedNds() {
        assertFalse(NdsCalculator.needNds("2025-12-31 23:59:00"));
        assertFalse(NdsCalculator.needNds("2025-12-31 23:59:59"));

        assertFalse(NdsCalculator.needNds("2026-01-01 00:00:00"));

        assertTrue(NdsCalculator.needNds("2026-01-01 00:00:01"));
        assertTrue(NdsCalculator.needNds("2026-01-01 00:01:00"));
    }

}