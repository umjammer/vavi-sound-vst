package org.urish.jnavst;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Set;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import static java.lang.System.getLogger;


/**
 * The {@code audioMasterCallback} a plug-in is handed at its entry point.
 * <p>
 * One instance belongs to one {@link VstPlugin} and has to outlive it: the plug-in holds the
 * function pointer JNA built around this object and calls it from its own threads, so letting it
 * be collected takes the process down. {@link VstPlugin} keeps the reference for that reason.
 * <p>
 * What is answered here is the part of the protocol that is the same for every host; anything the
 * application has an opinion about is asked of its {@link VstHost}.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-08-08 nsano initial version <br>
 */
public class HostCallback implements Callback {

    private static final Logger logger = getLogger(HostCallback.class.getName());

    // audioMaster opcodes, in the order aeffectx.h declares them

    private static final int audioMasterAutomate = 0;
    private static final int audioMasterVersion = 1;
    private static final int audioMasterCurrentId = 2;
    private static final int audioMasterIdle = 3;
    private static final int audioMasterPinConnected = 4;
    private static final int audioMasterWantMidi = 6;
    private static final int audioMasterGetTime = 7;
    private static final int audioMasterProcessEvents = 8;
    private static final int audioMasterTempoAt = 10;
    private static final int audioMasterGetNumAutomatableParameters = 11;
    private static final int audioMasterGetParameterQuantization = 12;
    private static final int audioMasterIOChanged = 13;
    private static final int audioMasterNeedIdle = 14;
    private static final int audioMasterSizeWindow = 15;
    private static final int audioMasterGetSampleRate = 16;
    private static final int audioMasterGetBlockSize = 17;
    private static final int audioMasterGetInputLatency = 18;
    private static final int audioMasterGetOutputLatency = 19;
    private static final int audioMasterWillReplaceOrAccumulate = 22;
    private static final int audioMasterGetCurrentProcessLevel = 23;
    private static final int audioMasterGetAutomationState = 24;
    private static final int audioMasterGetVendorString = 32;
    private static final int audioMasterGetProductString = 33;
    private static final int audioMasterGetVendorVersion = 34;
    private static final int audioMasterVendorSpecific = 35;
    private static final int audioMasterCanDo = 37;
    private static final int audioMasterGetLanguage = 38;
    private static final int audioMasterGetDirectory = 41;
    private static final int audioMasterUpdateDisplay = 42;
    private static final int audioMasterBeginEdit = 43;
    private static final int audioMasterEndEdit = 44;
    private static final int audioMasterOpenFileSelector = 45;
    private static final int audioMasterCloseFileSelector = 46;

    /**
     * What this host says yes to when a plug-in asks. Everything listed is answered by the code
     * here without the application having to do anything; a {@link VstHost} that supports more
     * says so from {@link VstHost#canDo}, which is asked first.
     */
    private static final Set<String> CAN_DO = Set.of(
            "sendVstEvents",
            "sendVstMidiEvent",
            "receiveVstEvents",
            "receiveVstMidiEvent",
            "sendVstTimeInfo",
            "sizeWindow",
            "startStopProcess",
            "acceptIOChanges",
            "supplyIdle");

    private final VstHost host;

    /** the plug-in this belongs to; null until its entry point has returned */
    VstPlugin plugin;

    /**
     * Answered to {@code audioMasterGetTime}. It is written afresh on every call and its address
     * handed back, which is what the protocol asks for - the plug-in reads it before it calls
     * again.
     */
    private final VstTimeInfo timeInfo = new VstTimeInfo();

    public HostCallback() {
        this(new VstHost() {});
    }

    public HostCallback(VstHost host) {
        this.host = host;
        // touch the memory once here so the pointer handed out later is never allocated from
        // inside a callback the audio thread is running
        timeInfo.write();
    }

    public VstHost getHost() {
        return host;
    }

    /**
     * The single method JNA turns into the native function pointer.
     * <p>
     * Nothing may be thrown out of here: the caller is C++ and an exception crossing back into it
     * is undefined, so everything unexpected is logged and answered with 0, which every opcode
     * reads as "not handled".
     */
    public long callback(Pointer effect, int opcode, int index, long value, Pointer ptr, float opt) {
        try {
            return dispatch(opcode, index, value, ptr, opt);
        } catch (Throwable t) {
            logger.log(Level.ERROR, "host callback failed for opcode " + opcode, t);
            return 0;
        }
    }

    private long dispatch(int opcode, int index, long value, Pointer ptr, float opt) {
        switch (opcode) {
        case audioMasterAutomate:
            host.automate(plugin, index, opt);
            return 1;
        case audioMasterVersion:
            return VstConst.VST_VERSION_2_4;
        case audioMasterCurrentId:
            // only a shell plug-in asks, to learn which of the plug-ins it holds to become
            return plugin != null ? plugin.getShellPluginId() : 0;
        case audioMasterIdle:
            return 0;
        case audioMasterPinConnected:
            // 0 means connected, and every pin this host asks for is
            return 0;
        case audioMasterWantMidi:
            return 1;
        case audioMasterGetTime:
            return Pointer.nativeValue(timeInfo(value));
        case audioMasterProcessEvents:
            readEvents(ptr);
            return 1;
        case audioMasterTempoAt:
            // in tempo * 10000, at a position this host does not vary the tempo over
            return (long) (host.getTempo() * 10000);
        case audioMasterGetNumAutomatableParameters:
            return 0;
        case audioMasterGetParameterQuantization:
            return 1;
        case audioMasterIOChanged:
            if (plugin != null) plugin.refresh();
            return 1;
        case audioMasterNeedIdle:
            return 1;
        case audioMasterSizeWindow:
            return host.sizeWindow(plugin, index, (int) value) ? 1 : 0;
        case audioMasterGetSampleRate:
            return (long) host.getSampleRate();
        case audioMasterGetBlockSize:
            return host.getBlockSize();
        case audioMasterGetInputLatency:
        case audioMasterGetOutputLatency:
            return 0;
        case audioMasterWillReplaceOrAccumulate:
            return 1; // replace
        case audioMasterGetCurrentProcessLevel:
            return VstConst.VST_ProcessLevelRealtime;
        case audioMasterGetAutomationState:
            return 1; // off
        case audioMasterGetVendorString:
            return writeString(ptr, host.getVendor(), VstConst.VST_MaxVendorStrLen);
        case audioMasterGetProductString:
            return writeString(ptr, host.getProduct(), VstConst.VST_MaxProductStrLen);
        case audioMasterGetVendorVersion:
            return host.getVendorVersion();
        case audioMasterVendorSpecific:
            return 0;
        case audioMasterCanDo:
            return canDo(ptr);
        case audioMasterGetLanguage:
            return VstConst.VST_LangEnglish;
        case audioMasterGetDirectory:
            return 0;
        case audioMasterUpdateDisplay:
            host.updateDisplay(plugin);
            return 1;
        case audioMasterBeginEdit:
        case audioMasterEndEdit:
            return 1;
        case audioMasterOpenFileSelector:
        case audioMasterCloseFileSelector:
            return 0;
        default:
            logger.log(Level.DEBUG, "unhandled VST host opcode " + opcode);
            return 0;
        }
    }

    /**
     * @param filter the flags the plug-in wants filled in - a field it did not ask for is left
     *               alone and its valid bit stays clear
     */
    private Pointer timeInfo(long filter) {
        timeInfo.samplePos = host.getSamplePosition();
        timeInfo.sampleRate = host.getSampleRate();
        timeInfo.flags = host.isPlaying() ? VstConst.VST_TransportPlaying : 0;

        if ((filter & VstConst.VST_NanosValid) != 0) {
            timeInfo.nanoSeconds = System.nanoTime();
            timeInfo.flags |= VstConst.VST_NanosValid;
        }
        double quarterNotes = timeInfo.samplePos / timeInfo.sampleRate * host.getTempo() / 60.0;
        if ((filter & VstConst.VST_PpqPosValid) != 0) {
            timeInfo.ppqPos = quarterNotes;
            timeInfo.flags |= VstConst.VST_PpqPosValid;
        }
        if ((filter & VstConst.VST_TempoValid) != 0) {
            timeInfo.tempo = host.getTempo();
            timeInfo.flags |= VstConst.VST_TempoValid;
        }
        if ((filter & VstConst.VST_BarsValid) != 0) {
            int beatsPerBar = Math.max(1, host.getTimeSigNumerator());
            timeInfo.barStartPos = Math.floor(quarterNotes / beatsPerBar) * beatsPerBar;
            timeInfo.flags |= VstConst.VST_BarsValid;
        }
        if ((filter & VstConst.VST_CyclePosValid) != 0) {
            timeInfo.cycleStartPos = 0;
            timeInfo.cycleEndPos = 0;
            timeInfo.flags |= VstConst.VST_CyclePosValid;
        }
        if ((filter & VstConst.VST_TimeSigValid) != 0) {
            timeInfo.timeSigNumerator = host.getTimeSigNumerator();
            timeInfo.timeSigDenominator = host.getTimeSigDenominator();
            timeInfo.flags |= VstConst.VST_TimeSigValid;
        }
        if ((filter & VstConst.VST_ClockValid) != 0) {
            timeInfo.samplesToNextClock = 0;
            timeInfo.flags |= VstConst.VST_ClockValid;
        }
        timeInfo.write();
        return timeInfo.getPointer();
    }

    /** the MIDI a plug-in sends back out, unpacked from the {@code VstEvents} it points at */
    private void readEvents(Pointer events) {
        if (events == null) return;
        int count = events.getInt(0);
        for (int i = 0; i < count; i++) {
            Pointer event = events.getPointer(16 + (long) i * Native.POINTER_SIZE);
            if (event == null) continue;
            int type = event.getInt(0);
            int deltaFrames = event.getInt(8);
            if (type == VstConst.VST_MidiType) {
                byte[] data = event.getByteArray(24, 4);
                host.midiOut(plugin, trim(data), deltaFrames);
            } else if (type == VstConst.VST_SysExType) {
                int length = event.getInt(16);
                Pointer dump = event.getPointer(32);
                if (dump != null && length > 0) {
                    host.midiOut(plugin, dump.getByteArray(0, length), deltaFrames);
                }
            }
        }
    }

    /** a MIDI event always carries four bytes; the message in it may be shorter */
    private static byte[] trim(byte[] data) {
        int length = switch (data[0] & 0xf0) {
            case 0xc0, 0xd0 -> 2;
            default -> 3;
        };
        byte[] message = new byte[length];
        System.arraycopy(data, 0, message, 0, length);
        return message;
    }

    private long canDo(Pointer ptr) {
        if (ptr == null) return VstConst.VST_CanDoUnknown;
        String what = ptr.getString(0);
        int answer = host.canDo(what);
        if (answer != VstConst.VST_CanDoUnknown) return answer;
        return CAN_DO.contains(what) ? VstConst.VST_CanDoYes : VstConst.VST_CanDoNo;
    }

    private static long writeString(Pointer ptr, String value, int max) {
        if (ptr == null) return 0;
        String text = value == null ? "" : value;
        if (text.length() > max) text = text.substring(0, max);
        ptr.setString(0, text);
        return 1;
    }
}
