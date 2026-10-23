package org.urish.jnavst;

/**
 * The constants of the VST 2.4 plug-in interface, as they are spelled in {@code aeffect.h} and
 * {@code aeffectx.h}.
 * <p>
 * The names keep the {@code VST_} prefix the rest of this package already used rather than the
 * SDK's {@code kVst}/{@code eff} ones, so nothing that referred to them before has to change.
 */
public interface VstConst {

    int VST_FALSE = 0;
    int VST_TRUE = 1;

    int VST_VERSION_1_0 = 1;
    int VST_VERSION_2_0 = 2;
    int VST_VERSION_2_1 = 2100;
    int VST_VERSION_2_2 = 2200;
    int VST_VERSION_2_3 = 2300;
    int VST_VERSION_2_4 = 2400;

    /**
     * The buffer sizes the SDK documents for the string opcodes. A plug-in is only supposed to
     * write this much, but a few write the full 64 bytes the host is told to reserve, so
     * {@link VstPlugin} always hands over a far bigger buffer than these.
     */
    int VST_MaxProgNameLen = 24;
    int VST_MaxParamStrLen = 8;
    int VST_MaxVendorStrLen = 64;
    int VST_MaxProductStrLen = 64;
    int VST_MaxEffectNameLen = 32;

    // AEffect flags

    /** the plug-in has an editor, so {@code effEditOpen} is worth calling */
    int VST_FlagsHasEditor = 1;
    /** deprecated in 2.4 */
    int VST_FlagsHasClip = 1 << 1;
    /** deprecated in 2.4 */
    int VST_FlagsHasVu = 1 << 2;
    /** deprecated in 2.4 */
    int VST_FlagsCanMono = 1 << 3;
    /** {@code processReplacing} is supported - every 2.4 plug-in sets this */
    int VST_FlagsCanReplacing = 1 << 4;
    /** the program data is handled through {@code effGetChunk}/{@code effSetChunk} */
    int VST_FlagsProgramChunks = 1 << 5;
    /** the plug-in is an instrument and wants MIDI events rather than audio in */
    int VST_FlagsIsSynth = 1 << 8;
    /** the plug-in is silent while no input arrives, so it may be skipped when idle */
    int VST_FlagsNoSoundInStop = 1 << 9;
    /** {@code processDoubleReplacing} is supported */
    int VST_FlagsCanDoubleReplacing = 1 << 12;

    // VstTimeInfo flags

    int VST_TransportChanged = 1;
    int VST_TransportPlaying = 1 << 1;
    int VST_TransportCycleActive = 1 << 2;
    int VST_TransportRecording = 1 << 3;
    int VST_AutomationWriting = 1 << 6;
    int VST_AutomationReading = 1 << 7;
    int VST_NanosValid = 1 << 8;
    int VST_PpqPosValid = 1 << 9;
    int VST_TempoValid = 1 << 10;
    int VST_BarsValid = 1 << 11;
    int VST_CyclePosValid = 1 << 12;
    int VST_TimeSigValid = 1 << 13;
    int VST_SmpteValid = 1 << 14;
    int VST_ClockValid = 1 << 15;

    // VstEvent types

    int VST_MidiType = 1;
    int VST_AudioType = 2;
    int VST_VideoType = 3;
    int VST_ParameterType = 4;
    int VST_TriggerType = 5;
    int VST_SysExType = 6;

    /** {@code sizeof(VstEvent)} and {@code sizeof(VstMidiEvent)} alike - both are 32 bytes */
    int VST_EventSize = 32;

    // process levels, the answer to audioMasterGetCurrentProcessLevel

    int VST_ProcessLevelUnknown = 0;
    int VST_ProcessLevelUser = 1;
    int VST_ProcessLevelRealtime = 2;
    int VST_ProcessLevelPrefetch = 3;
    int VST_ProcessLevelOffline = 4;

    int VST_LangEnglish = 1;

    /** the answer a plug-in expects from {@code audioMasterCanDo} for something the host supports */
    int VST_CanDoYes = 1;
    int VST_CanDoNo = -1;
    int VST_CanDoUnknown = 0;
}
