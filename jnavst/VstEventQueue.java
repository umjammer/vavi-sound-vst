package org.urish.jnavst;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;


/**
 * The MIDI a host has for a plug-in, held until the next process call collects it.
 * <p>
 * A VST instrument is not sent MIDI as it arrives: the events for a block are handed over in one
 * {@code VstEvents} array right before the block is rendered, each carrying the offset in samples
 * from the start of the block it happens at. Notes therefore come in from whichever thread the
 * driver runs on and leave on the audio thread, so both ends are synchronized here.
 * <p>
 * The native block is built once per flush and reused while it is big enough, because the flush
 * happens per audio block and allocating there would be felt.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-08-08 nsano initial version <br>
 */
public final class VstEventQueue {

    /**
     * {@code sizeof(VstMidiSysexEvent)} on a 64 bit build, and the bigger of the two event structs,
     * so one slot size fits both - the plug-in reads the real length out of the event's byteSize.
     */
    private static final int SLOT_SIZE = 48;

    /** {@code numEvents}, padding, {@code reserved}, then the pointer array */
    private static final int HEADER_SIZE = 16;

    private record Event(byte[] data, int deltaFrames) {}

    private final List<Event> pending = new ArrayList<>();

    /** how many events the native block below was built for; 0 while nothing is allocated */
    private int capacity;

    private Memory events;
    private Memory slots;
    /** where the bytes of the sysex dumps live, since the event only points at them */
    private Memory dumps;
    private int dumpCapacity;

    /**
     * Queues one MIDI message.
     *
     * @param data a whole message - three bytes or fewer go over as a MIDI event, anything longer
     *             (a sysex dump) as a sysex event
     * @param deltaFrames how far into the next block it sounds, in samples
     */
    public synchronized void add(byte[] data, int deltaFrames) {
        if (data == null || data.length == 0) return;
        pending.add(new Event(data.clone(), Math.max(0, deltaFrames)));
    }

    /** whether anything is waiting */
    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    public synchronized void clear() {
        pending.clear();
    }

    /**
     * Lays what is queued out as a {@code VstEvents*} and empties the queue.
     * <p>
     * The memory stays owned here and is overwritten by the next flush, which is late enough: a
     * plug-in has to copy anything it wants to keep out of {@code effProcessEvents}.
     *
     * @return null when nothing was queued
     */
    public synchronized Pointer flush() {
        int count = pending.size();
        if (count == 0) return null;

        int dumpBytes = 0;
        for (Event e : pending) {
            if (e.data.length > 3) dumpBytes += e.data.length;
        }

        allocate(count, dumpBytes);

        events.setInt(0, count);
        events.setPointer(8, null); // reserved
        int dumpOffset = 0;
        for (int i = 0; i < count; i++) {
            Event e = pending.get(i);
            Pointer slot = slots.share((long) i * SLOT_SIZE, SLOT_SIZE);
            events.setPointer(HEADER_SIZE + (long) i * Native.POINTER_SIZE, slot);

            if (e.data.length > 3) {
                // VstMidiSysexEvent
                slot.setInt(0, VstConst.VST_SysExType);
                slot.setInt(4, SLOT_SIZE);
                slot.setInt(8, e.deltaFrames);
                slot.setInt(12, 0);
                slot.setInt(16, e.data.length);
                slot.setPointer(24, null);
                dumps.write(dumpOffset, e.data, 0, e.data.length);
                slot.setPointer(32, dumps.share(dumpOffset, e.data.length));
                slot.setPointer(40, null);
                dumpOffset += e.data.length;
            } else {
                // VstMidiEvent
                slot.setInt(0, VstConst.VST_MidiType);
                slot.setInt(4, VstConst.VST_EventSize);
                slot.setInt(8, e.deltaFrames);
                slot.setInt(12, 0); // flags - kVstMidiEventIsRealtime is 2.4 only and optional
                slot.setInt(16, 0); // noteLength, unused without kVstMidiEventIsRealtime
                slot.setInt(20, 0); // noteOffset
                for (int b = 0; b < 4; b++) {
                    slot.setByte(24 + b, b < e.data.length ? e.data[b] : 0);
                }
                slot.setByte(28, (byte) 0); // detune
                slot.setByte(29, (byte) 0); // noteOffVelocity
                slot.setByte(30, (byte) 0);
                slot.setByte(31, (byte) 0);
            }
        }

        pending.clear();
        return events;
    }

    private void allocate(int count, int dumpBytes) {
        if (count > capacity) {
            capacity = Math.max(count, Math.max(16, capacity * 2));
            events = new Memory(HEADER_SIZE + (long) capacity * Native.POINTER_SIZE);
            slots = new Memory((long) capacity * SLOT_SIZE);
            events.clear();
            slots.clear();
        }
        if (dumpBytes > dumpCapacity) {
            dumpCapacity = Math.max(dumpBytes, Math.max(256, dumpCapacity * 2));
            dumps = new Memory(dumpCapacity);
            dumps.clear();
        }
    }
}
