package org.urish.jnavst;

import java.awt.Component;
import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Locale;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.urish.jnavst.AEffect.Opcode;

import static java.lang.System.getLogger;


/**
 * One loaded VST 2.4 plug-in.
 * <p>
 * The plug-in is a native library with a single entry point that hands back an {@link AEffect},
 * and everything else - opening it, asking it its name, rendering with it - is a call through one
 * of the function pointers in that struct. The order the calls have to come in is the SDK's:
 * {@link #open()}, then the sample rate and block size, then {@link #resume()} before the first
 * {@link #processReplacing}, and the reverse on the way out.
 * <p>
 * The audio buffers the plug-in writes into are allocated once, when the block size is set, and
 * reused: {@link #processReplacing} runs on the audio thread and must not allocate.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-08-08 nsano initial version <br>
 */
public class VstPlugin {

    private static final Logger logger = getLogger(VstPlugin.class.getName());

    /** the names a VST 2.x entry point goes by, newest spelling first */
    private static final String[] ENTRY_POINTS = {"VSTPluginMain", "main_macho", "main"};

    /** big enough for every string opcode - the SDK asks a host to reserve 64 bytes, some write more */
    private static final int STRING_BUFFER = 256;

    private final File file;
    private final NativeLibrary library;

    /** held for the plug-in's whole life - the native side calls it, so it must not be collected */
    private final HostCallback hostCallback;

    private final AEffect effect;

    /** the struct's own address, which is what every call wants as its first argument */
    private final Pointer handle;

    private final VstEventQueue events = new VstEventQueue();

    /** which plug-in of a shell to become, answered to {@code audioMasterCurrentId} */
    private int shellPluginId;

    private boolean opened;
    private boolean resumed;
    private boolean editorOpen;

    private int blockSize;
    private float sampleRate;

    /** the pointer arrays and sample storage handed to {@code processReplacing} */
    private Memory inputPointers;
    private Memory inputData;
    private Memory outputPointers;
    private Memory outputData;
    private int bufferFrames;

    private final Memory stringBuffer = new Memory(STRING_BUFFER);

    public VstPlugin(File vstFile) {
        this(vstFile, new VstHost() {});
    }

    public VstPlugin(File vstFile, VstHost host) {
        this(vstFile, host, 0);
    }

    /**
     * @param shellPluginId which plug-in a shell library should become, or 0 for a plain one
     */
    public VstPlugin(File vstFile, VstHost host, int shellPluginId) {
        this.file = vstFile;
        this.shellPluginId = shellPluginId;
        this.hostCallback = new HostCallback(host);

        File binary = binaryOf(vstFile);
        this.library = NativeLibrary.getInstance(binary.getAbsolutePath());

        Function entry = entryPoint(library);
        Pointer pointer = entry.invokePointer(new Object[] {hostCallback});
        if (pointer == null) {
            throw new VstException("VST plugin creation failed, " + entry.getName() + " returned null: " + vstFile);
        }

        this.handle = pointer;
        this.effect = new AEffect(pointer);
        this.effect.read();
        if (effect.magic != AEffect.K_EFFECT_MAGIC) {
            throw new VstException("VST plugin creation failed, invalid magic 0x%08x: %s"
                    .formatted(effect.magic, vstFile));
        }
        hostCallback.plugin = this;
    }

    /**
     * The library to hand to {@code dlopen}.
     * <p>
     * On macOS a plug-in is a bundle directory rather than a file, and the library inside it is
     * named after the bundle in {@code Contents/MacOS}. Everywhere else the path already is the
     * library.
     */
    static File binaryOf(File file) {
        if (!file.isDirectory()) return file;

        File macos = new File(file, "Contents/MacOS");
        File[] candidates = macos.listFiles(f -> f.isFile() && !f.isHidden());
        if (candidates == null || candidates.length == 0) {
            throw new VstException("not a VST bundle, nothing in Contents/MacOS: " + file);
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        for (File candidate : candidates) {
            if (candidate.getName().equals(name)) return candidate;
        }
        return candidates[0];
    }

    private static Function entryPoint(NativeLibrary library) {
        UnsatisfiedLinkError last = null;
        for (String name : ENTRY_POINTS) {
            try {
                return library.getFunction(name);
            } catch (UnsatisfiedLinkError e) {
                last = e;
            }
        }
        throw new VstException("not a VST 2 plugin, no entry point in " + library.getFile(), last);
    }

    /** whether a path looks like something this can load, by its name alone */
    public static boolean isPluginFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (file.isDirectory()) return name.endsWith(".vst");
        return name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
    }

    public File getFile() {
        return file;
    }

    public AEffect getEffect() {
        return effect;
    }

    /** the MIDI waiting to be handed over by the next {@link #processReplacing} */
    public VstEventQueue getEventQueue() {
        return events;
    }

    public int getShellPluginId() {
        return shellPluginId;
    }

    public void setShellPluginId(int shellPluginId) {
        this.shellPluginId = shellPluginId;
    }

    /**
     * Re-reads the struct, after the plug-in said its inputs or outputs changed.
     * <p>
     * Does nothing outside the plug-in's open life. The struct belongs to the plug-in and is gone
     * once {@code effClose} has run, and this is reachable from the plug-in's own callback, so
     * reading it unguarded would be reading freed memory. {@link #open()} calls it itself, which
     * is where the numbers first become trustworthy.
     */
    public void refresh() {
        if (!opened) return;
        effect.read();
    }

    // the dispatcher

    private long dispatch(Opcode opcode, int index, long value, Pointer ptr, float opt) {
        return effect.dispatcher.callback(handle, opcode.code, index, value, ptr, opt);
    }

    public long dispatch(Opcode opcode) {
        return dispatch(opcode, 0, 0, null, 0);
    }

    private boolean b(long value) {
        return value != VstConst.VST_FALSE;
    }

    /**
     * The string opcodes all work the same way: the plug-in writes into a buffer the host owns.
     * The buffer is cleared first so a plug-in that answers nothing leaves an empty string rather
     * than whatever was there before.
     */
    private synchronized String string(Opcode opcode, int index) {
        stringBuffer.clear();
        dispatch(opcode, index, 0, stringBuffer, 0);
        return stringBuffer.getString(0).trim();
    }

    // lifecycle

    /** tells the plug-in it has been instantiated; nothing else may be called before this */
    public boolean open() {
        if (opened) return true;
        dispatch(Opcode.effOpen, 0, 0, null, 0);
        opened = true;
        refresh();
        return true;
    }

    /**
     * Destroys the plug-in. The {@link AEffect} is gone afterwards and nothing else here may be
     * called, so this is idempotent - the manager code around it closes along more than one path.
     */
    public boolean close() {
        if (!opened) return false;
        try {
            if (editorOpen) editClose();
            if (resumed) suspend();
            dispatch(Opcode.effClose, 0, 0, null, 0);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "closing " + file + " failed", t);
        }
        opened = false;
        return true;
    }

    public boolean isOpen() {
        return opened;
    }

    /** whether the plug-in is between {@link #resume()} and {@link #suspend()} */
    public boolean isResumed() {
        return resumed;
    }

    public boolean idle() {
        return b(dispatch(Opcode.effIdle));
    }

    /** switches the plug-in on: it allocates, and may be asked to render from here on */
    public void resume() {
        if (resumed) return;
        dispatch(Opcode.effMainsChanged, 0, VstConst.VST_TRUE, null, 0);
        dispatch(Opcode.effStartProcess, 0, 0, null, 0);
        resumed = true;
    }

    /** switches the plug-in off, which is also how its tails and held notes are dropped */
    public void suspend() {
        if (!resumed) return;
        dispatch(Opcode.effStopProcess, 0, 0, null, 0);
        dispatch(Opcode.effMainsChanged, 0, VstConst.VST_FALSE, null, 0);
        resumed = false;
        events.clear();
    }

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate;
        dispatch(Opcode.effSetSampleRate, 0, 0, null, sampleRate);
    }

    public float getSampleRate() {
        return sampleRate;
    }

    /**
     * Tells the plug-in the largest block it will be asked for, and allocates the buffers for it.
     * The plug-in has to be suspended for this, which is the caller's business.
     */
    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
        dispatch(Opcode.effSetBlockSize, 0, blockSize, null, 0);
        allocateBuffers(blockSize);
    }

    public int getBlockSize() {
        return blockSize;
    }

    // what the plug-in is

    public int getNumInputs() {
        return effect.numInputs;
    }

    public int getNumOutputs() {
        return effect.numOutputs;
    }

    public int getNumParams() {
        return effect.numParams;
    }

    public int getNumPrograms() {
        return effect.numPrograms;
    }

    public int getUniqueId() {
        return effect.uniqueID;
    }

    /** how many frames the plug-in's own processing lags by, to be compensated by the host */
    public int getInitialDelay() {
        return effect.initialDelay;
    }

    /** an instrument, which is played with MIDI and has no audio input */
    public boolean isSynth() {
        return effect.hasFlag(VstConst.VST_FlagsIsSynth);
    }

    public boolean hasEditor() {
        return effect.hasFlag(VstConst.VST_FlagsHasEditor);
    }

    public boolean canReplacing() {
        return effect.hasFlag(VstConst.VST_FlagsCanReplacing);
    }

    /** the plug-in keeps its state as an opaque chunk rather than as parameter values */
    public boolean usesChunks() {
        return effect.hasFlag(VstConst.VST_FlagsProgramChunks);
    }

    public int getVstVersion() {
        return (int) dispatch(Opcode.effGetVstVersion);
    }

    /** what the plug-in answers when asked whether it can do something, e.g. "receiveVstEvents" */
    public int canDo(String what) {
        Memory memory = new Memory(what.length() + 1L);
        memory.setString(0, what);
        return (int) dispatch(Opcode.effCanDo, 0, 0, memory, 0);
    }

    /** the name of the plug-in */
    public String getName() {
        return string(Opcode.effGetEffectName, 0);
    }

    public String getVendorString() {
        return string(Opcode.effGetVendorString, 0);
    }

    public String getProductString() {
        return string(Opcode.effGetProductString, 0);
    }

    public int getVendorVersion() {
        return (int) dispatch(Opcode.effGetVendorVersion);
    }

    // programs and parameters

    public void setProgram(int programIndex) {
        dispatch(Opcode.effBeginSetProgram, 0, 0, null, 0);
        dispatch(Opcode.effSetProgram, 0, programIndex, null, 0);
        dispatch(Opcode.effEndSetProgram, 0, 0, null, 0);
    }

    public int getProgram() {
        return (int) dispatch(Opcode.effGetProgram);
    }

    public String getProgramName() {
        return string(Opcode.effGetProgramName, 0);
    }

    /** the name of a program other than the current one, which not every plug-in answers */
    public String getProgramName(int index) {
        String name = string(Opcode.effGetProgramNameIndexed, index);
        return name.isEmpty() && index == getProgram() ? getProgramName() : name;
    }

    public String getParamName(int index) {
        return string(Opcode.effGetParamName, index);
    }

    public String getParamDisplay(int index) {
        return string(Opcode.effGetParamDisplay, index);
    }

    public String getParamLabel(int index) {
        return string(Opcode.effGetParamLabel, index);
    }

    /** every VST parameter is a value from 0 to 1, whatever it means to the plug-in */
    public float getParameter(int index) {
        return effect.getParameter.callback(handle, index);
    }

    public void setParameter(int index, float value) {
        effect.setParameter.callback(handle, index, value);
    }

    /** every parameter at once, which is how a host stores the state of a plug-in without chunks */
    public float[] getParameters() {
        float[] values = new float[effect.numParams];
        for (int i = 0; i < values.length; i++) {
            values[i] = getParameter(i);
        }
        return values;
    }

    public void setParameters(float[] values) {
        if (values == null) return;
        for (int i = 0; i < Math.min(values.length, effect.numParams); i++) {
            setParameter(i, values[i]);
        }
    }

    /**
     * The plug-in's state as it keeps it itself.
     *
     * @param program true for the current program only, false for the whole bank
     * @return null when the plug-in does not use chunks
     */
    public byte[] getChunk(boolean program) {
        if (!usesChunks()) return null;
        PointerByReference chunk = new PointerByReference();
        long length = dispatch(Opcode.effGetChunk, program ? 1 : 0, 0, chunk.getPointer(), 0);
        if (length <= 0 || chunk.getValue() == null) return null;
        return chunk.getValue().getByteArray(0, (int) length);
    }

    public void setChunk(byte[] chunk, boolean program) {
        if (chunk == null || chunk.length == 0 || !usesChunks()) return;
        Memory memory = new Memory(chunk.length);
        memory.write(0, chunk, 0, chunk.length);
        dispatch(Opcode.effSetChunk, program ? 1 : 0, chunk.length, memory, 0);
    }

    // rendering

    /**
     * Hands over the MIDI that has piled up since the last block. Called by
     * {@link #processReplacing} - the SDK wants the events immediately before the block they
     * belong to, and nowhere else.
     */
    private void sendEvents() {
        Pointer block = events.flush();
        if (block != null) {
            dispatch(Opcode.effProcessEvents, 0, 0, block, 0);
        }
    }

    /**
     * Renders one block.
     * <p>
     * An instrument is given no input and writes its own sound; an effect is given the audio to
     * work on and replaces it. Channels beyond what the plug-in has are ignored, and channels it
     * has that the caller did not supply are read as silence.
     *
     * @param inputs one array per channel, or empty for an instrument
     * @param outputs one array per channel, filled in
     * @param numFrames how many samples of each, at most the block size that was set
     */
    public void processReplacing(float[][] inputs, float[][] outputs, int numFrames) {
        if (!resumed) resume();
        if (numFrames > bufferFrames) allocateBuffers(numFrames);

        sendEvents();

        int channels = Math.min(inputs.length, effect.numInputs);
        inputData.clear();
        for (int i = 0; i < channels; i++) {
            inputData.write((long) i * bufferFrames * Float.BYTES, inputs[i], 0, numFrames);
        }
        outputData.clear();

        effect.processReplacing.callback(handle, inputPointers, outputPointers, numFrames);

        channels = Math.min(outputs.length, effect.numOutputs);
        for (int i = 0; i < channels; i++) {
            outputData.read((long) i * bufferFrames * Float.BYTES, outputs[i], 0, numFrames);
        }
    }

    /**
     * The pointer arrays a process call takes: one address per channel into one flat block of
     * samples. A plug-in with no inputs still gets a valid array, because more than a few read
     * through it before looking at how many inputs they have.
     */
    private void allocateBuffers(int frames) {
        if (frames <= 0) return;
        bufferFrames = frames;

        int inputChannels = Math.max(1, effect.numInputs);
        int outputChannels = Math.max(1, effect.numOutputs);

        inputPointers = new Memory((long) inputChannels * Native.POINTER_SIZE);
        inputData = new Memory((long) inputChannels * frames * Float.BYTES);
        inputData.clear();
        for (int i = 0; i < inputChannels; i++) {
            inputPointers.setPointer((long) i * Native.POINTER_SIZE,
                    inputData.share((long) i * frames * Float.BYTES, (long) frames * Float.BYTES));
        }

        outputPointers = new Memory((long) outputChannels * Native.POINTER_SIZE);
        outputData = new Memory((long) outputChannels * frames * Float.BYTES);
        outputData.clear();
        for (int i = 0; i < outputChannels; i++) {
            outputPointers.setPointer((long) i * Native.POINTER_SIZE,
                    outputData.share((long) i * frames * Float.BYTES, (long) frames * Float.BYTES));
        }
    }

    // the editor

    /**
     * Opens the plug-in's own editor inside a window the host owns.
     *
     * @param window the native window handle - an {@code HWND} on Windows, an {@code NSView*} on
     *               macOS, a {@code Window} on X11
     */
    public boolean editOpen(Pointer window) {
        if (!hasEditor()) return false;
        editorOpen = b(dispatch(Opcode.effEditOpen, 0, 0, window, 0));
        return editorOpen;
    }

    /**
     * Opens the editor inside an AWT component.
     * <p>
     * Whether this works at all is up to JNA, which can only find the native handle of a component
     * on the platforms its own library has the window support for. On macOS it has none and hands
     * back a null pointer instead of failing, which has to be caught here: a plug-in given a null
     * window does not refuse it, it walks into it and takes the process down. Where the handle
     * cannot be had the editor simply does not open, and the plug-in is still perfectly playable.
     */
    public boolean editOpen(Component component) {
        Pointer window;
        try {
            window = Native.getComponentPointer(component);
        } catch (Throwable t) {
            logger.log(Level.INFO, "no native handle for the editor window, " + file.getName()
                    + " will play without its editor: " + t);
            return false;
        }
        if (window == null || Pointer.nativeValue(window) == 0) {
            logger.log(Level.INFO, "this platform does not hand out native window handles, "
                    + file.getName() + " will play without its editor");
            return false;
        }
        return editOpen(window);
    }

    public boolean editClose() {
        if (!editorOpen) return false;
        editorOpen = false;
        return b(dispatch(Opcode.effEditClose));
    }

    public boolean isEditorOpen() {
        return editorOpen;
    }

    /** has to be called regularly while the editor is open, or its display freezes */
    public boolean editIdle() {
        if (!editorOpen) return false;
        return b(dispatch(Opcode.effEditIdle));
    }

    /**
     * How big the editor wants to be.
     *
     * @return null when the plug-in has no editor or will not say
     */
    public ERect getEditRect() {
        if (!hasEditor()) return null;
        PointerByReference rect = new PointerByReference();
        if (!b(dispatch(Opcode.effEditGetRect, 0, 0, rect.getPointer(), 0))) return null;
        if (rect.getValue() == null) return null;
        ERect result = new ERect(rect.getValue());
        result.read();
        return result;
    }

    @Override
    public String toString() {
        return "VstPlugin[" + file.getName() + "]";
    }
}
