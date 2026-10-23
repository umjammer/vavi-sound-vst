package org.urish.jnavst;

import java.awt.Component;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.urish.jnavst.AEffect.Opcode;

import static java.lang.System.getLogger;


/**
 * A VST 2.4 plug-in written in Java, the side of the conversation {@link VstPlugin} is not.
 * <p>
 * This is the SDK's {@code AudioEffectX} done over JNA: the {@link AEffect} is allocated here
 * and its function pointers are JNA callbacks into this object, so the struct is a real one any
 * host can call through. {@link #main} is the plug-in's entry point. A Java host takes it in
 * process with {@link VstPlugin#VstPlugin(VstEffect, VstHost)}; a native host would need a small
 * library that starts a jvm and calls it, which is not here.
 * <p>
 * A subclass sets itself up in its constructor with {@link #setNumOutputs} and friends, as an
 * {@code AudioEffectX} does, and overrides what it implements. Everything has an answer that is
 * safe to leave alone.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-21 nsano initial version <br>
 */
public abstract class VstEffect {

    private static final Logger logger = getLogger(VstEffect.class.getName());

    // audioMaster opcodes this calls
    private static final int audioMasterAutomate = 0;
    private static final int audioMasterVersion = 1;
    private static final int audioMasterProcessEvents = 8;
    private static final int audioMasterIOChanged = 13;
    private static final int audioMasterSizeWindow = 15;
    private static final int audioMasterGetSampleRate = 16;
    private static final int audioMasterGetBlockSize = 17;

    /** a midi message of a block, with where in the block it is */
    public record MidiEvent(byte[] data, int deltaFrames) {}

    private final AEffect effect = new AEffect();

    /** the callbacks, held here for the plug-in's whole life: the host has their addresses */
    private final AEffect.DispatcherCallback dispatcher = this::dispatchSafe;
    private final AEffect.ProcessCallback processReplacing = this::processReplacingSafe;
    private final AEffect.ProcessCallback process = this::processReplacingSafe;
    private final AEffect.SetParameterCallback setParameter = (e, index, value) -> setParameter(index, value);
    private final AEffect.GetParameterCallback getParameter = (e, index) -> getParameter(index);

    private Function audioMaster;

    private int numInputs;
    private int numOutputs = 2;
    private int numParams;
    private int numPrograms = 1;
    private int uniqueId;
    private int version = 1;
    private int flags = VstConst.VST_FlagsCanReplacing;

    private float sampleRate = 44100;
    private int blockSize = 512;

    /** kept until the next get chunk, the host copies it out of here */
    private Memory chunk;
    /** handed out by effEditGetRect */
    private final ERect rect = new ERect();
    private final Memory rectPointer = new Memory(Native.POINTER_SIZE);

    private float[][] inputs = new float[0][0];
    private float[][] outputs = new float[2][0];

    /**
     * the entry point, {@code VSTPluginMain}
     *
     * @param audioMaster the host's {@code audioMasterCallback}
     * @return the {@code AEffect*}
     */
    public final Pointer main(Pointer audioMaster) {
        this.audioMaster = audioMaster != null ? Function.getFunction(audioMaster) : null;
        effect.magic = AEffect.K_EFFECT_MAGIC;
        effect.dispatcher = dispatcher;
        effect.process = process;
        effect.processReplacing = processReplacing;
        effect.setParameter = setParameter;
        effect.getParameter = getParameter;
        writeEffect();
        return effect.getPointer();
    }

    private void writeEffect() {
        effect.numInputs = numInputs;
        effect.numOutputs = numOutputs;
        effect.numParams = numParams;
        effect.numPrograms = numPrograms;
        effect.uniqueID = uniqueId;
        effect.version = version;
        effect.setFlags(flags);
        effect.write();
    }

    // setup, as AudioEffectX has it

    protected void setNumInputs(int numInputs) {
        this.numInputs = numInputs;
    }

    protected void setNumOutputs(int numOutputs) {
        this.numOutputs = numOutputs;
    }

    protected void setNumParams(int numParams) {
        this.numParams = numParams;
    }

    protected void setUniqueID(int uniqueId) {
        this.uniqueId = uniqueId;
    }

    protected void setUniqueID(String fourCharacters) {
        int id = 0;
        for (char c : fourCharacters.toCharArray()) id = (id << 8) | (c & 0xff);
        this.uniqueId = id;
    }

    protected void setVersion(int version) {
        this.version = version;
    }

    protected void isSynth(boolean synth) {
        setFlag(VstConst.VST_FlagsIsSynth, synth);
    }

    protected void programsAreChunks(boolean chunks) {
        setFlag(VstConst.VST_FlagsProgramChunks, chunks);
    }

    protected void hasEditor(boolean editor) {
        setFlag(VstConst.VST_FlagsHasEditor, editor);
    }

    private void setFlag(int flag, boolean on) {
        flags = on ? flags | flag : flags & ~flag;
    }

    /** after the inputs or outputs changed while open */
    protected void ioChanged() {
        writeEffect();
        hostCall(audioMasterIOChanged, 0, 0, null, 0);
    }

    // the host

    /** calls the host, 0 when there is none */
    protected long hostCall(int opcode, int index, long value, Pointer ptr, float opt) {
        if (audioMaster == null) return 0;
        return audioMaster.invokeLong(new Object[] {effect.getPointer(), opcode, index, value, ptr, opt});
    }

    protected int getHostVersion() {
        return (int) hostCall(audioMasterVersion, 0, 0, null, 0);
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public int getBlockSize() {
        return blockSize;
    }

    /** a parameter changed in the editor */
    protected void setParameterAutomated(int index, float value) {
        setParameter(index, value);
        hostCall(audioMasterAutomate, index, 0, null, value);
    }

    /** the editor would like another size */
    protected boolean sizeWindow(int width, int height) {
        return hostCall(audioMasterSizeWindow, width, height, null, 0) != 0;
    }

    /** midi out to the host, sent with the block being rendered */
    protected void sendMidi(byte[] message, int deltaFrames) {
        if (audioMaster == null) return;
        VstEventQueue queue = new VstEventQueue();
        queue.add(message, deltaFrames);
        hostCall(audioMasterProcessEvents, 0, 0, queue.flush(), 0);
    }

    // what a plug-in implements

    protected void open() {
    }

    protected void close() {
    }

    /** switched on, rendering is about to start */
    protected void resume() {
    }

    /** switched off */
    protected void suspend() {
    }

    protected void setSampleRate(float sampleRate) {
    }

    protected void setBlockSize(int blockSize) {
    }

    /**
     * renders one block
     *
     * @param inputs one array per input
     * @param outputs one array per output, to be filled from 0
     */
    protected abstract void processReplacing(float[][] inputs, float[][] outputs, int sampleFrames);

    /** the midi of the next block, in the order it comes */
    protected void processEvents(List<MidiEvent> events) {
    }

    protected String getEffectName() {
        return getClass().getSimpleName();
    }

    protected String getVendorString() {
        return "";
    }

    protected String getProductString() {
        return getEffectName();
    }

    protected int getVendorVersion() {
        return version;
    }

    protected String getProgramName() {
        return "";
    }

    /**
     * @return {@link VstConst#VST_CanDoYes}, {@link VstConst#VST_CanDoNo} or {@link VstConst#VST_CanDoUnknown}
     */
    protected int canDo(String what) {
        return VstConst.VST_CanDoUnknown;
    }

    protected float getParameter(int index) {
        return 0;
    }

    protected void setParameter(int index, float value) {
    }

    protected String getParameterName(int index) {
        return "";
    }

    /** the state, when the programs are chunks, null for none */
    protected byte[] getChunk(boolean program) {
        return null;
    }

    protected void setChunk(byte[] data, boolean program) {
    }

    /** the size of the editor, width and height, null for none */
    protected int[] getEditorSize() {
        return null;
    }

    /**
     * opens the editor
     *
     * @param window the host's native window, which a java plug-in may not be able to use
     */
    protected boolean editOpen(Pointer window) {
        return false;
    }

    protected void editClose() {
    }

    protected void editIdle() {
    }

    /** the editor for a java host, which puts it into its own window instead of a native one, null for none */
    public Component getEditorComponent() {
        return null;
    }

    // the dispatcher

    /** nothing may be thrown back into the host */
    private long dispatchSafe(Pointer e, int opcode, int index, long value, Pointer ptr, float opt) {
        try {
            return dispatch(opcode, index, value, ptr, opt);
        } catch (Throwable t) {
            logger.log(Level.ERROR, "dispatcher failed for opcode " + opcode, t);
            return 0;
        }
    }

    private static boolean is(int opcode, Opcode o) {
        return opcode == o.code;
    }

    private long dispatch(int opcode, int index, long value, Pointer ptr, float opt) {
        if (is(opcode, Opcode.effOpen)) {
            open();
        } else if (is(opcode, Opcode.effClose)) {
            close();
        } else if (is(opcode, Opcode.effSetSampleRate)) {
            sampleRate = opt;
            setSampleRate(opt);
        } else if (is(opcode, Opcode.effSetBlockSize)) {
            blockSize = (int) value;
            setBlockSize((int) value);
        } else if (is(opcode, Opcode.effMainsChanged)) {
            if (value != 0) resume(); else suspend();
        } else if (is(opcode, Opcode.effProcessEvents)) {
            processEvents(readEvents(ptr));
            return 1;
        } else if (is(opcode, Opcode.effGetEffectName)) {
            return writeString(ptr, getEffectName(), VstConst.VST_MaxEffectNameLen);
        } else if (is(opcode, Opcode.effGetVendorString)) {
            return writeString(ptr, getVendorString(), VstConst.VST_MaxVendorStrLen);
        } else if (is(opcode, Opcode.effGetProductString)) {
            return writeString(ptr, getProductString(), VstConst.VST_MaxProductStrLen);
        } else if (is(opcode, Opcode.effGetVendorVersion)) {
            return getVendorVersion();
        } else if (is(opcode, Opcode.effGetVstVersion)) {
            return VstConst.VST_VERSION_2_4;
        } else if (is(opcode, Opcode.effGetPlugCategory)) {
            return (flags & VstConst.VST_FlagsIsSynth) != 0 ? 2 : 1; // kPlugCategSynth : kPlugCategEffect
        } else if (is(opcode, Opcode.effCanDo)) {
            return ptr == null ? 0 : canDo(ptr.getString(0));
        } else if (is(opcode, Opcode.effGetProgramName) || is(opcode, Opcode.effGetProgramNameIndexed)) {
            return writeString(ptr, getProgramName(), VstConst.VST_MaxProgNameLen);
        } else if (is(opcode, Opcode.effGetParamName)) {
            return writeString(ptr, getParameterName(index), VstConst.VST_MaxParamStrLen);
        } else if (is(opcode, Opcode.effGetParamDisplay)) {
            return writeString(ptr, String.format("%.2f", getParameter(index)), VstConst.VST_MaxParamStrLen);
        } else if (is(opcode, Opcode.effGetChunk)) {
            byte[] data = getChunk(index != 0);
            if (data == null || data.length == 0 || ptr == null) return 0;
            chunk = new Memory(data.length);
            chunk.write(0, data, 0, data.length);
            ptr.setPointer(0, chunk);
            return data.length;
        } else if (is(opcode, Opcode.effSetChunk)) {
            if (ptr != null && value > 0) setChunk(ptr.getByteArray(0, (int) value), index != 0);
            return 1;
        } else if (is(opcode, Opcode.effEditGetRect)) {
            int[] size = getEditorSize();
            if (size == null || ptr == null) return 0;
            rect.top = 0;
            rect.left = 0;
            rect.bottom = (short) size[1];
            rect.right = (short) size[0];
            rect.write();
            ptr.setPointer(0, rect.getPointer());
            return 1;
        } else if (is(opcode, Opcode.effEditOpen)) {
            return editOpen(ptr) ? 1 : 0;
        } else if (is(opcode, Opcode.effEditClose)) {
            editClose();
            return 1;
        } else if (is(opcode, Opcode.effEditIdle)) {
            editIdle();
            return 1;
        } else if (is(opcode, Opcode.effStartProcess) || is(opcode, Opcode.effStopProcess)) {
            return 1;
        } else if (is(opcode, Opcode.effGetNumMidiInputChannels)) {
            return (flags & VstConst.VST_FlagsIsSynth) != 0 ? 16 : 0;
        }
        return 0;
    }

    private static long writeString(Pointer ptr, String value, int max) {
        if (ptr == null) return 0;
        String text = value == null ? "" : value;
        if (text.length() > max) text = text.substring(0, max);
        ptr.setString(0, text);
        return 1;
    }

    /** VstEvents: numEvents, reserved, then the pointers, see {@link VstEventQueue} */
    private static List<MidiEvent> readEvents(Pointer events) {
        List<MidiEvent> list = new ArrayList<>();
        if (events == null) return list;
        int count = events.getInt(0);
        for (int i = 0; i < count; i++) {
            Pointer event = events.getPointer(16 + (long) i * Native.POINTER_SIZE);
            if (event == null) continue;
            int type = event.getInt(0);
            int deltaFrames = event.getInt(8);
            if (type == VstConst.VST_MidiType) {
                byte[] data = event.getByteArray(24, 4);
                int length = switch (data[0] & 0xf0) {
                    case 0xc0, 0xd0 -> 2;
                    default -> 3;
                };
                byte[] message = new byte[length];
                System.arraycopy(data, 0, message, 0, length);
                list.add(new MidiEvent(message, deltaFrames));
            } else if (type == VstConst.VST_SysExType) {
                int length = event.getInt(16);
                Pointer dump = event.getPointer(32);
                if (dump != null && length > 0) {
                    list.add(new MidiEvent(dump.getByteArray(0, length), deltaFrames));
                }
            }
        }
        return list;
    }

    // processing

    private void processReplacingSafe(Pointer e, Pointer in, Pointer out, int sampleFrames) {
        try {
            if (inputs.length != numInputs || (numInputs > 0 && inputs[0].length < sampleFrames)) {
                inputs = new float[numInputs][Math.max(sampleFrames, blockSize)];
            }
            if (outputs.length != numOutputs || outputs[0].length < sampleFrames) {
                outputs = new float[numOutputs][Math.max(sampleFrames, blockSize)];
            }
            for (int ch = 0; ch < numInputs; ch++) {
                Pointer p = in == null ? null : in.getPointer((long) ch * Native.POINTER_SIZE);
                if (p != null) p.read(0, inputs[ch], 0, sampleFrames);
            }
            processReplacing(inputs, outputs, sampleFrames);
            for (int ch = 0; ch < numOutputs; ch++) {
                Pointer p = out == null ? null : out.getPointer((long) ch * Native.POINTER_SIZE);
                if (p != null) p.write(0, outputs[ch], 0, sampleFrames);
            }
        } catch (Throwable t) {
            logger.log(Level.ERROR, "processReplacing failed", t);
        }
    }

    /** the native function pointer of a java callback, what a host hands over as audioMaster */
    static Pointer pointerOf(com.sun.jna.Callback callback) {
        return CallbackReference.getFunctionPointer(callback);
    }
}
