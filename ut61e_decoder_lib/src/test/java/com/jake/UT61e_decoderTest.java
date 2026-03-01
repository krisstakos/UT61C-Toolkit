package com.jake;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test different inputs
 */
public class UT61e_decoderTest {
    @Test
    public void parseVoltageDC() throws Exception {
        UT61e_decoder data = new UT61e_decoder();
        byte[] testInput = {52, 50, 50, -75, 56, -80, 59, 49, -80, -80, 56, -80, 13, -118};
        assertEquals(data.parse(testInput), true);
        assertEquals(data.getValue(), 225.8, 0.0001);
        assertEquals(data.isOL(), true);
        assertEquals(data.getMode(), data.MODE_VOLTAGE);
        assertEquals(data.isDC(), true);
        assertEquals(data.isFreq(), false);
        assertEquals(data.isDuty(), false);
    }

    @Test
    public void parseCapacity() throws Exception {
        UT61e_decoder data = new UT61e_decoder();
        byte[] testInput = {-80, -80, -80, -77, 50, -80, -74, -80, -80, -80, 50, -80, 13, -118};
        assertEquals(data.parse(testInput), true);
        assertEquals(data.getValue(), 0.320, 0.0001);
        assertEquals(data.isOL(), false);
        assertEquals(data.getMode(), data.MODE_CAPACITANCE);
        assertEquals(data.isDC(), false);
        assertEquals(data.isFreq(), false);
        assertEquals(data.isDuty(), false);
    }

    @Test
    public void parseUT61Cflags() throws Exception {
        UT61e_decoder data = new UT61e_decoder();
        byte[] line = new byte[14];
        // simple ascii frame: +0123 0
        line[0] = 0x2B; // '+' sign
        line[1] = 0x30;
        line[2] = 0x31;
        line[3] = 0x32;
        line[4] = 0x33;
        line[5] = 0x20; // space
        line[6] = 0x30; // '0' decimal-point indicator
        // set flags bits for HOLD, REL, MIN, MAX, DIODE, BEEP, CAP
        long flags = (1L<<1) | (1L<<2) | (1L<<12) | (1L<<13) | (1L<<18) | (1L<<19) | (1L<<26);
        int fl = (int)flags;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(fl);
        System.arraycopy(bb.array(), 0, line, 7, 4);
        line[11] = 0; // unused
        line[12] = 0x0D;
        line[13] = 0x0A;
        assertTrue(data.parse(line));
        assertTrue(data.isHold());
        assertTrue(data.isRel());
        assertTrue(data.isMin());
        assertTrue(data.isMax());
        assertTrue(data.isDiode());
        assertTrue(data.isBeep());
        assertTrue(data.isCapacitance());
        // toString should contain the textual flags
        String s = data.toString();
        assertTrue(s.contains("HOLD"));
        assertTrue(s.contains("REL"));
        assertTrue(s.contains("MIN"));
        assertTrue(s.contains("MAX"));
        assertTrue(s.toUpperCase().contains("DIODE"));
        assertTrue(s.toUpperCase().contains("BEEP"));
        // hFE no longer reported
    }

}