package org.urish.jnavst;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;


public class VstTimeInfo extends Structure {
    /**
     * current Position in audio samples (always valid)
     */
    public double samplePos;
    /**
     * current Sample Rate in Herz (always valid)
     */
    public double sampleRate;
    /**
     * System Time in nanoseconds (10^-9 second)
     */
    public double nanoSeconds;
    /**
     * Musical Position, in Quarter Note (1.0 equals 1 Quarter Note)
     */
    public double ppqPos;
    /**
     * current Tempo in BPM (Beats Per Minute)
     */
    public double tempo;
    /**
     * last Bar Start Position, in Quarter Note
     */
    public double barStartPos;
    /**
     * Cycle Start (left locator), in Quarter Note
     */
    public double cycleStartPos;
    /**
     * Cycle End (right locator), in Quarter Note
     */
    public double cycleEndPos;
    /**
     * Time Signature Numerator (e.g. 3 for 3/4)
     */
    public int timeSigNumerator;
    /**
     * Time Signature Denominator (e.g. 4 for 3/4)
     */
    public int timeSigDenominator;
    /**
     * SMPTE offset (in SMPTE subframes (bits; 1/80 of a
     * frame)). The current SMPTE position can be calculated
     * using #samplePos, #sampleRate, and #smpteFrameRate.
     */
    public int smpteOffset;
    /**
     * @see VstSmpteFrameRate
     */
    public int smpteFrameRate;
    /**
     * MIDI Clock Resolution (24 Per Quarter Note),
     * can be negative (nearest clock)
     */
    public int samplesToNextClock;
    /**
     * @see VstTimeInfoFlags
     */
    public int flags;

    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "samplePos", "sampleRate", "nanoSeconds", "ppqPos", "tempo", "barStartPos", "cycleStartPos",
                "cycleEndPos", "timeSigNumerator", "timeSigDenominator", "smpteOffset", "smpteFrameRate",
                "samplesToNextClock", "flags");
    }
}
