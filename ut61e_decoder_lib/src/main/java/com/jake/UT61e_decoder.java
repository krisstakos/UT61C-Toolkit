package com.jake;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Class that holds one dataset/measurement from a Uni-T UT61E Multimeter
 */
public class UT61e_decoder {

    private byte[] raw;
    public double value;
    private int mode;
    private int unit, type, info;
    public String unit_str;

    // Mode constants
    public final int MODE_VOLTAGE = 0xB;
    public final int MODE_CURRENT_A = 0x0;
    public final int MODE_CURRENT_mA = 0xF;
    public final int MODE_CURRENT_µA = 0xD;
    public final int MODE_RESISTANCE = 0x3;
    public final int MODE_FREQ = 0x2;
    public final int MODE_CAPACITANCE = 0x6;
    public final int MODE_DIODE = 0x1;
    public final int MODE_CONTINUITY = 0x5;
    private final int MODE_DUTY = 0x8;

    //Type constants
    private final int DC = 16;
    private final int AC = 8;

    private final int HZ = 134217728;
    private final int DUTY = 131072;
    //Info constants
    private final int OL = 0x1; // byte 7
    // LOWBAT not currently used by the decoder
    // private final int LOWBAT = 0x2;
    private final int NEG = 0x4;

    private final int UL = 0x8; // byte 9

    //Unit constants
    private final String[][] units = new String[16][];
    // UT61-C support
    private boolean ut61c = false;
    private long ut61cFlags = 0L;
    private boolean ut61cOL = false;
    private boolean ut61cNeg = false;

    public UT61e_decoder() {
        units[MODE_VOLTAGE] = new String[]{"V", "V", "V", "V", "mV"};
        units[MODE_CURRENT_A] = new String[]{"A"};
        units[MODE_CURRENT_mA] = new String[]{"mA", "mA"};
        units[MODE_CURRENT_µA] = new String[]{"µA", "µA"};
        units[MODE_RESISTANCE] = new String[]{"Ω", "kΩ", "kΩ", "kΩ", "MΩ", "MΩ", "MΩ"};
        units[MODE_FREQ] = new String[]{"Hz", "Hz", "", "kHz", "kHz", "MHz", "MHz", "MHz"};
        units[MODE_CAPACITANCE] = new String[]{"nF", "nF", "µF", "µF", "µF", "mF", "mF", "mF"};
        units[MODE_DIODE] = new String[]{"V"};
        units[MODE_CONTINUITY] = new String[]{"Ω"};
        units[MODE_DUTY] = new String[]{"%"};

    }

    /**
     * Decode the input data an set all data fields in the object
     * @param input 14 bytes (one measurement) from the multimeter serial connection
     * @return true if parsing was successful, false otherwise
     */
    public boolean parse(byte[] input) {

        if (!checkLength(input)) {
            System.err.print("decoder: wrong input length");
            return false;
        } else if (!checkParity(input, true)) {
            System.err.print("decoder: parity check failed");
            return false;
        }
        try {
            set_info();
            set_mode();
            calcValue();
        } catch (NullPointerException e) {
            System.err.print("decoder: mode/unit not found");
            return false;
        }

        return true;
    }

    /**
     * Autodetect and parse either UT61E (original) or UT61C ASCII frame.
     */
    public boolean parseAuto(byte[] input) {
        if (isUT61CFrame(input)) {
            return parseUT61C(input);
        }
        // fallback to classic binary parser
        ut61c = false;
        return parse(input);
    }

    private boolean isUT61CFrame(byte[] input) {
        if (input == null || input.length != 14) return false;
        // CR LF at end and space at byte 5 and sign at byte 0
        if (input[12] != 0x0D || input[13] != 0x0A) return false;
        if (input[5] != 0x20) return false;
        if (input[0] != 0x2B && input[0] != 0x2D) return false;
        // bytes 1-4 should be ASCII digits or the '?0:?' OL pattern
        for (int i = 1; i <= 4; i++) {
            int b = input[i] & 0xFF;
            if (b >= 0x30 && b <= 0x39) continue; // digit
            // allow '?' and ':' for OL marker
            if (b == 0x3F || b == 0x3A) continue;
            return false;
        }
        return true;
    }

    private boolean parseUT61C(byte[] line) {
        // follow gist logic (14-byte ASCII frame)
        if (line.length != 14) return false;
        ut61c = true;
        raw = line;
        ut61cNeg = line[0] == 0x2D;

        // digits 1..4
        boolean ol = (line[1] == 0x3F);
        ut61cOL = ol;
        double digits = 0;
        if (!ol) {
            try {
                digits = Integer.parseInt(new String(line, 1, 4, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        double point = 0.0;
        switch (line[6]) {
            case 0x30: // '0' - no decimal point visible (whole numbers)
                point = 1.0;
                break;
            case 0x31: // '1' - thousands
                point = 0.001;
                break;
            case 0x32: // '2' - hundredths
                point = 0.01;
                break;
            case 0x33: // '3' - tenths (up to ~100V)
                point = 0.1;
                break;
            case 0x34: // '4' - higher voltage range (tenths for 200V+ range)
                point = 0.1;
                break;
            default:
                return false;
        }

        // flags: little-endian 32-bit
        long rawFlags = ((long)ByteBuffer.wrap(line, 7, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()) & 0xFFFFFFFFL;
        ut61cFlags = rawFlags;

        // Determine mode from flags for UT61-C
        if ((rawFlags & (1L<<31)) != 0) {
            mode = MODE_VOLTAGE;
        } else if ((rawFlags & (1L<<30)) != 0) {
            mode = MODE_CURRENT_A;
        } else if ((rawFlags & (1L<<29)) != 0) {
            mode = MODE_RESISTANCE;
        } else {
            mode = MODE_VOLTAGE; // default
        }

        // bargraph not used here; determine value
        if (ol) {
            value = 0.0;
        } else {
            value = (ut61cNeg ? -1.0 : 1.0) * digits * point;
        }

        // build unit string from flags (best-effort)
        String prefix = "";
        if ((rawFlags & (1L<<20)) != 0) prefix = "M"; // BV(20) mega-prefix
        if ((rawFlags & (1L<<21)) != 0) prefix = "k"; // BV(21) kilo-prefix
        if ((rawFlags & (1L<<22)) != 0) prefix = "m"; // BV(22) milli-prefix
        if ((rawFlags & (1L<<23)) != 0) prefix = "µ"; // BV(23) micro-prefix

        // If it's a capacitance reading and no prefix flags are set, assume nano (nF)
        // UT61-C doesn't set a dedicated nano flag, so fall back to nF for small-cap ranges
        // when BV(26) indicates capacitance but no prefix bit is present.

        String base = "";
        if ((rawFlags & (1L<<31)) != 0) base = "V"; // BV(31)
        else if ((rawFlags & (1L<<30)) != 0) base = "A"; // BV(30)
        else if ((rawFlags & (1L<<29)) != 0) base = "Ω"; // BV(29)
        else if ((rawFlags & (1L<<27)) != 0) base = "Hz"; // BV(27)
        else if ((rawFlags & (1L<<26)) != 0) base = "F"; // BV(26) capacitance
        else if ((rawFlags & (1L<<25)) != 0) base = "°C"; // BV(25)
        else if ((rawFlags & (1L<<24)) != 0) base = "°F"; // BV(24)
        else base = "";

        // For capacitance, if no prefix flag was set, default to nano (nF)
        if ("F".equals(base) && prefix.isEmpty()) prefix = "n";
        unit_str = (prefix + base).trim();
        // adjust for mega-ohm range: UT61-C flags lack explicit 'M' prefix
        if (mode == MODE_RESISTANCE && !unit_str.isEmpty()) {
            if (Math.abs(value) >= 1_000_000) {
                unit_str = "M" + unit_str;
            }
        }
        if (mode == MODE_DUTY) {
            unit_str = "%";
        }

        return true;
    }

    /**
     * Check the whole input data against the parity bit in each byte and fills the raw field
     * @return true, if input is fine
     */
    private boolean checkParity(byte[] input, boolean odd) {
        boolean correct = true;
        raw = new byte[input.length];
        for (int i = 0; i<input.length; i++) {
            byte in = input[i];
            in ^= in >> 4;
            in ^= in >> 2;
            in ^= in >> 1;
            correct = ((in & 1) == 1) == odd? correct : false;

            raw[i] = (byte)(input[i] & 0x7F);
        }
        return correct;
    }

    /**
     * @return true, if input size is correct (14 bytes)
     */
    private boolean checkLength(byte[] input) {
        return input.length == 14;
    }


    /**
     * Set the mode, unit and unit string field, taking all relevant bytes into account
     */
    private void set_mode() {
        if (ut61c) {
            if (isFreq()) {
                mode = MODE_FREQ;
            } else if (isDuty()) {
                mode = MODE_DUTY;
            } else if ((ut61cFlags & (1L<<26)) != 0) {
                mode = MODE_CAPACITANCE;
            } else if ((ut61cFlags & (1L<<31)) != 0) {
                mode = MODE_VOLTAGE;
            } else if ((ut61cFlags & (1L<<30)) != 0) {
                mode = MODE_CURRENT_A;
            } else if ((ut61cFlags & (1L<<29)) != 0) {
                mode = MODE_RESISTANCE;
            } else {
                mode = MODE_VOLTAGE;
            }
            unit = raw[0] & 0x7;
            unit_str = units[mode][unit];
        } else {
            mode = raw[6] & 0x0F;
            if (isFreq()) mode = MODE_FREQ;
            if (isDuty()) mode = MODE_DUTY;
            unit = raw[0] & 0x7;
            unit_str = units[mode][unit];
        }
    }

    /**
     * Set the type and info half byte
     */
    private void set_info() {
        type = raw[10] & 0x0F;
        info = raw[7] & 0x0F;
    }

    /**
     * Calculate the measurement value from the raw data
     */
    private void calcValue() {
        // reset accumulator to avoid accidental accumulation across parses
        this.value = 0.0;
        int i = 1000;
        for (int i2 = 1; i2 < 5; i2++) {
            this.value += (this.raw[i2] & 15) * i;
            i /= 10;
        }
        this.value *= getDecimalPoint();
        if (isNeg()) {
            this.value *= -1.0d;
        }
    }

    /**
     * Get the decimal point multiplier based on raw[6] value
     */
    private double getDecimalPoint() {
        byte[] bArr = this.raw;
        if (bArr[6] == 48) {
            return 0.0;
        }
        if (bArr[6] == 49) {
            return 0.001d;
        }
        if (bArr[6] == 50) {
            return 0.01d;
        }
        if (bArr[6] == 51) {
            return 1.0d;
        }
        if (bArr[6] == 52) {
            return 0.1d;
        }
        return 0.0;
    }

    /**
     * @return true, if the measured value is negative
     */
    public boolean isNeg() {
        if (ut61c) return ut61cNeg;
        return (info & NEG) == NEG;
    }

    /* UT61-C flag helpers */
    public boolean isHold() { return ut61c && (ut61cFlags & (1L<<1)) != 0; }
    public boolean isRel() { return ut61c && (ut61cFlags & (1L<<2)) != 0; }
    public boolean isMin() { return ut61c && (ut61cFlags & (1L<<12)) != 0; }
    public boolean isMax() { return ut61c && (ut61cFlags & (1L<<13)) != 0; }
    public boolean isDiode() { return ut61c && (ut61cFlags & (1L<<18)) != 0; }
    public boolean isBeep() { return ut61c && (ut61cFlags & (1L<<19)) != 0; }
    public boolean isCapacitance() { return ut61c && (ut61cFlags & (1L<<26)) != 0; }

    /**
     * @return true, if the multimeter is overloaded (out of range)
     */
    public boolean isOL() {
        if (ut61c) return ut61cOL;
        return (info & OL) == OL;
    }

    /**
     * @return true, if the multimeter is underloaded (out of range)
     */
    public boolean isUL() {
        return (raw[9] & UL) == UL;
    }

    /**
     * @return true, if it is in frequency mode (through yellow push button)
     */
    public boolean isFreq() {
        if (ut61c) {
            if ((ut61cFlags & HZ) > 0) return true;
            if (mode == MODE_FREQ) return true;
            return false;
        }
        if (mode == MODE_FREQ) return true;
        return (type & HZ) == HZ;
    }

    /**
     * @return true, if it is in duty cycle mode (through yellow push button)
     */
    public boolean isDuty() {
        if (ut61c) {
            if ((ut61cFlags & DUTY) > 0) return true;
            if (mode == MODE_DUTY) return true;
            return false;
        }
        if (mode == MODE_DUTY) return true;
        return (info & MODE_DUTY) == MODE_DUTY || info == MODE_DUTY;
    }


    /**
     * @return true, if it is set to DC (voltage and current mode)
     */
    public boolean isDC() {
        if (ut61c) return (ut61cFlags & DC) > 0;
        return (type & DC) == DC;
    }

    /**
     * @return true, if it is set to AC (voltage and current mode)
     */
    public boolean isAC() {
        if (ut61c) return (ut61cFlags & AC) > 0;
        return (type & AC) == AC;
    }

    @Override
    public String toString() {
        String formatted = String.format(Locale.US,"%.4f", value);
        // Remove trailing zeros after decimal point
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        String suffix = unit_str;
        if (isDuty()) suffix = "%"; // force percent sign in duty mode
        return formatted + " " + suffix;
    }

    /**
     * @return CSV compatible line string
     */
    public String toCSVString() {
        String seperator = ";";
        String out = String.format(Locale.US,"%.4f", value) + seperator;
        out += unit_str + seperator;
        out += (isDC()? "DC":"") + (isAC()? "AC":"")+ (isFreq()? "Freq.":"")+ (isDuty()? "Duty":"") + seperator;
        out += (isOL()? "OL":"") + (isUL()? "UL":"") + seperator;
        // additional flag column
        StringBuilder flags = new StringBuilder();
        if (isHold()) flags.append("HOLD ");
        if (isRel()) flags.append("REL ");
        if (isMin()) flags.append("MIN ");
        if (isMax()) flags.append("MAX ");
        if (isDiode()) flags.append("DIODE ");
        if (isBeep()) flags.append("BEEP ");
        out += flags.toString().trim();
        return out;
    }

    /**
     * First line in a CSV file that describes the columns
     */
    public static String csvHeader = "Value;Unit;Type;Overloaded;Flags";


    /**
     * @return byte array from input
     */
    public byte[] getRaw() {
        return raw;
    }

    /**
     * @return the measured value
     */
    public double getValue() {
        return value;
    }

    /**
     * @return the mode (measurement type), can be compared to the MODE_XYZ constants
     */
    public int getMode() {
        return mode;
    }
}
