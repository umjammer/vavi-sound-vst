package org.urish.jnavst;

/**
 * The side of the conversation the application owns.
 * <p>
 * A plug-in asks its host for the things only the host knows - what the sample rate is, where the
 * transport stands, whether a feature is available - and tells it about things only the host can
 * act on, a knob that moved or an editor that wants to be a different size. Every method has an
 * answer that is safe to leave alone, so an application implements the two or three it cares
 * about.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-08-08 nsano initial version <br>
 */
public interface VstHost {

    /** in Hz - the same value the plug-in was told through {@code effSetSampleRate} */
    default float getSampleRate() {
        return 44100;
    }

    /** the largest number of frames one process call will be asked for */
    default int getBlockSize() {
        return 512;
    }

    /** how many frames have been rendered so far, which is the transport position */
    default long getSamplePosition() {
        return 0;
    }

    default double getTempo() {
        return 120;
    }

    default int getTimeSigNumerator() {
        return 4;
    }

    default int getTimeSigDenominator() {
        return 4;
    }

    /** whether the transport is rolling; a plug-in with a synced LFO listens to this */
    default boolean isPlaying() {
        return true;
    }

    default String getVendor() {
        return "vavi";
    }

    default String getProduct() {
        return "mdplayer";
    }

    default int getVendorVersion() {
        return 1;
    }

    /** a parameter was moved in the plug-in's own editor */
    default void automate(VstPlugin plugin, int index, float value) {
    }

    /** the editor asks to be resized; answering true means the window was actually resized */
    default boolean sizeWindow(VstPlugin plugin, int width, int height) {
        return false;
    }

    /** something the host displays about the plug-in - a program name, say - has changed */
    default void updateDisplay(VstPlugin plugin) {
    }

    /** MIDI the plug-in produced, one whole message per call */
    default void midiOut(VstPlugin plugin, byte[] message, int deltaFrames) {
    }

    /**
     * Whether the host can do what the plug-in is asking about.
     *
     * @return {@link VstConst#VST_CanDoYes}, {@link VstConst#VST_CanDoNo} or
     *         {@link VstConst#VST_CanDoUnknown} to fall back on what {@link HostCallback} answers
     */
    default int canDo(String what) {
        return VstConst.VST_CanDoUnknown;
    }
}
