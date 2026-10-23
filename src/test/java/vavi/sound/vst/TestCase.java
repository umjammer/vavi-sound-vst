/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.vst;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.urish.jnavst.VstConst;
import org.urish.jnavst.VstEffect;
import org.urish.jnavst.VstHost;
import org.urish.jnavst.VstPlugin;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestCase.
 * <p>
 * The plug-in is a java one hosted in process, so the whole VST 2.4 protocol is exercised
 * without a native library. A native plug-in is tried only when {@code local.properties} names one.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 ${DATE} nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vst.plugin")
    String vst = "";

    /** a stereo gain, with midi in to see the events arrive */
    static class Gain extends VstEffect {

        float gain = 1;
        final List<String> calls = new ArrayList<>();
        final List<MidiEvent> events = new ArrayList<>();

        Gain() {
            setNumInputs(2);
            setNumOutputs(2);
            setNumParams(1);
            setUniqueID("vGan");
            setVersion(3);
        }

        @Override protected void open() { calls.add("open"); }
        @Override protected void close() { calls.add("close"); }
        @Override protected void resume() { calls.add("resume"); }
        @Override protected void suspend() { calls.add("suspend"); }

        @Override
        protected void processReplacing(float[][] inputs, float[][] outputs, int sampleFrames) {
            for (int ch = 0; ch < 2; ch++) {
                for (int i = 0; i < sampleFrames; i++) {
                    outputs[ch][i] = inputs[ch][i] * gain;
                }
            }
        }

        @Override
        protected void processEvents(List<MidiEvent> events) {
            this.events.addAll(events);
        }

        @Override protected String getEffectName() { return "Gain"; }
        @Override protected String getVendorString() { return "vavi"; }
        @Override protected String getParameterName(int index) { return "gain"; }
        @Override protected float getParameter(int index) { return gain; }
        @Override protected void setParameter(int index, float value) { gain = value; }

        @Override
        protected int canDo(String what) {
            return "receiveVstMidiEvent".equals(what) ? VstConst.VST_CanDoYes : VstConst.VST_CanDoNo;
        }

        /** as if the knob were turned in the editor */
        void turn(float value) {
            setParameterAutomated(0, value);
        }
    }

    /** a synth keeping its state as a chunk */
    static class Chunky extends VstEffect {

        byte[] state = "initial".getBytes(StandardCharsets.US_ASCII);

        Chunky() {
            setNumInputs(0);
            setNumOutputs(1);
            isSynth(true);
            programsAreChunks(true);
        }

        @Override
        protected void processReplacing(float[][] inputs, float[][] outputs, int sampleFrames) {
            for (int i = 0; i < sampleFrames; i++) {
                outputs[0][i] = 0.5f;
            }
        }

        @Override protected byte[] getChunk(boolean program) { return state; }
        @Override protected void setChunk(byte[] data, boolean program) { state = data; }
    }

    Gain gain;
    VstPlugin plugin;
    final List<Float> automated = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        gain = new Gain();
        plugin = new VstPlugin(gain, new VstHost() {
            @Override
            public void automate(VstPlugin plugin, int index, float value) {
                automated.add(value);
            }
        });
        plugin.open();
    }

    @AfterEach
    void teardown() {
        plugin.close();
    }

    @Test
    void testProperties() throws Exception {
        assertEquals(2, plugin.getNumInputs());
        assertEquals(2, plugin.getNumOutputs());
        assertEquals(1, plugin.getNumParams());
        assertEquals(('v' << 24) | ('G' << 16) | ('a' << 8) | 'n', plugin.getUniqueId());
        assertTrue(plugin.canReplacing());
        assertFalse(plugin.isSynth());
        assertFalse(plugin.hasEditor());
        assertFalse(plugin.usesChunks());
        assertNull(plugin.getEditRect());

        assertEquals("Gain", plugin.getName());
        assertEquals("vavi", plugin.getVendorString());
        assertEquals("Gain", plugin.getProductString());
        assertEquals(3, plugin.getVendorVersion());
        assertEquals(VstConst.VST_VERSION_2_4, plugin.getVstVersion());
        assertEquals("gain", plugin.getParamName(0));

        assertEquals(VstConst.VST_CanDoYes, plugin.canDo("receiveVstMidiEvent"));
        assertEquals(VstConst.VST_CanDoNo, plugin.canDo("offline"));
    }

    @Test
    void testLifecycle() throws Exception {
        assertTrue(plugin.isOpen());
        assertFalse(plugin.isResumed());

        plugin.resume();
        plugin.resume(); // twice is once
        assertTrue(plugin.isResumed());
        plugin.suspend();
        assertFalse(plugin.isResumed());

        plugin.resume();
        assertTrue(plugin.close());
        assertFalse(plugin.isOpen());
        assertFalse(plugin.close(), "close is idempotent");

        // close suspends a resumed plug-in first
        assertEquals(List.of("open", "resume", "suspend", "resume", "suspend", "close"), gain.calls);
    }

    @Test
    void testSampleRateAndBlockSize() throws Exception {
        plugin.setSampleRate(48000);
        plugin.setBlockSize(256);
        assertEquals(48000, plugin.getSampleRate());
        assertEquals(256, plugin.getBlockSize());
        assertEquals(48000, gain.getSampleRate());
        assertEquals(256, gain.getBlockSize());
    }

    @Test
    void testParameters() throws Exception {
        plugin.setParameter(0, 0.25f);
        assertEquals(0.25f, gain.gain);
        assertEquals(0.25f, plugin.getParameter(0));
        assertEquals(String.format("%.2f", 0.25f), plugin.getParamDisplay(0));

        plugin.setParameters(new float[] {0.75f, 0.1f}); // the extra one is ignored
        assertArrayEquals(new float[] {0.75f}, plugin.getParameters());

        assertNull(plugin.getChunk(true), "no chunks for a plug-in that does not use them");
    }

    @Test
    void testAutomate() throws Exception {
        gain.turn(0.4f);
        assertEquals(0.4f, plugin.getParameter(0));
        assertEquals(List.of(0.4f), automated);
    }

    @Test
    void testProcessReplacing() throws Exception {
        plugin.setSampleRate(44100);
        plugin.setBlockSize(64);
        plugin.setParameter(0, 0.5f);

        int frames = 64;
        float[][] inputs = new float[2][frames];
        for (int i = 0; i < frames; i++) {
            inputs[0][i] = (float) Math.sin(2 * Math.PI * i / frames);
            inputs[1][i] = i / (float) frames;
        }
        float[][] outputs = new float[2][frames];

        plugin.processReplacing(inputs, outputs, frames);
        assertTrue(plugin.isResumed(), "rendering resumes a suspended plug-in");

        for (int ch = 0; ch < 2; ch++) {
            for (int i = 0; i < frames; i++) {
                assertEquals(inputs[ch][i] * 0.5f, outputs[ch][i], 1e-6f);
            }
        }

        // a block larger than the block size reallocates instead of overrunning
        int large = 200;
        float[][] in2 = {new float[large], new float[large]};
        java.util.Arrays.fill(in2[0], 1f);
        java.util.Arrays.fill(in2[1], -1f);
        float[][] out2 = new float[2][large];
        plugin.processReplacing(in2, out2, large);
        assertEquals(0.5f, out2[0][large - 1]);
        assertEquals(-0.5f, out2[1][large - 1]);

        // a missing input channel is read as silence
        float[][] out3 = new float[2][frames];
        plugin.processReplacing(new float[][] {inputs[0]}, out3, frames);
        assertEquals(inputs[0][frames / 4] * 0.5f, out3[0][frames / 4], 1e-6f);
        assertEquals(0f, out3[1][frames - 1]);
    }

    @Test
    void testMidiEvents() throws Exception {
        plugin.setBlockSize(32);
        plugin.getEventQueue().add(new byte[] {(byte) 0x90, 60, 100}, 0);
        plugin.getEventQueue().add(new byte[] {(byte) 0xc0, 5}, 7);
        plugin.getEventQueue().add(new byte[] {(byte) 0xf0, 0x7e, 0x7f, 0x09, 0x01, (byte) 0xf7}, 16);

        plugin.processReplacing(new float[2][32], new float[2][32], 32);

        assertEquals(3, gain.events.size());
        assertArrayEquals(new byte[] {(byte) 0x90, 60, 100}, gain.events.get(0).data());
        assertEquals(0, gain.events.get(0).deltaFrames());
        assertArrayEquals(new byte[] {(byte) 0xc0, 5}, gain.events.get(1).data());
        assertEquals(7, gain.events.get(1).deltaFrames());
        assertArrayEquals(new byte[] {(byte) 0xf0, 0x7e, 0x7f, 0x09, 0x01, (byte) 0xf7}, gain.events.get(2).data());
        assertEquals(16, gain.events.get(2).deltaFrames());
        assertTrue(plugin.getEventQueue().isEmpty(), "events are sent once");

        plugin.processReplacing(new float[2][32], new float[2][32], 32);
        assertEquals(3, gain.events.size());
    }

    @Test
    void testSynthAndChunk() throws Exception {
        Chunky chunky = new Chunky();
        VstPlugin synth = new VstPlugin(chunky, new VstHost() {});
        synth.open();
        try {
            assertTrue(synth.isSynth());
            assertTrue(synth.usesChunks());
            assertEquals(0, synth.getNumInputs());

            assertArrayEquals("initial".getBytes(StandardCharsets.US_ASCII), synth.getChunk(true));
            byte[] state = "restored state".getBytes(StandardCharsets.US_ASCII);
            synth.setChunk(state, false);
            assertArrayEquals(state, chunky.state);
            assertArrayEquals(state, synth.getChunk(false));

            float[][] outputs = new float[1][16];
            synth.processReplacing(new float[0][], outputs, 16);
            for (float v : outputs[0]) {
                assertEquals(0.5f, v);
            }
        } finally {
            synth.close();
        }
    }

    @Test
    @EnabledIf("localPropertiesExists")
    void testNative() throws Exception {
        File file = new File(vst);
        if (!VstPlugin.isPluginFile(file)) return;
        VstPlugin vst = new VstPlugin(file);
        vst.open();
        try {
            System.err.println(vst + ": " + vst.getName() + " / " + vst.getVendorString()
                    + ", in: " + vst.getNumInputs() + ", out: " + vst.getNumOutputs() + ", params: " + vst.getNumParams()
                    + ", synth: " + vst.isSynth());
            vst.setSampleRate(44100);
            vst.setBlockSize(512);
            float[][] outputs = new float[vst.getNumOutputs()][512];
            vst.processReplacing(new float[vst.getNumInputs()][512], outputs, 512);
        } finally {
            vst.close();
        }
    }
}
