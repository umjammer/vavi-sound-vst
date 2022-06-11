package org.urish.jnavst;

public interface VstConst {
	public static final int VST_FALSE = 0;
	public static final int VST_TRUE = 1;

	public static final int VST_VERSION_1_0 = 1;
	public static final int VST_VERSION_2_0 = 2;
	public static final int VST_VERSION_2_1 = 2100;
	public static final int VST_VERSION_2_2 = 2200;
	public static final int VST_VERSION_2_3 = 2300;
	public static final int VST_VERSION_2_4 = 2400;

	public static final int VST_MaxProgNameLen = 24;
	public static final int VST_MaxParamStrLen = 8;
	public static final int VST_MaxVendorStrLen = 64;
	public static final int VST_MaxProductStrLen = 64;
	public static final int VST_MaxEffectNameLen = 32;

	public static final int VST_TransportChanged = 1;
	public static final int VST_TransportPlaying = 1 << 1;
	public static final int VST_TransportCycleActive = 1 << 2;
	public static final int VST_AutomationWriting = 1 << 6;
	public static final int VST_AutomationReading = 1 << 7;
	public static final int VST_NanosValid = 1 << 8;
	public static final int VST_PpqPosValid = 1 << 9;
	public static final int VST_TempoValid = 1 << 10;
	public static final int VST_BarsValid = 1 << 11;
	public static final int VST_CyclePosValid = 1 << 12;
	public static final int VST_TimeSigValid = 1 << 13;
	public static final int VST_SmpteValid = 1 << 14;
	public static final int VST_ClockValid = 1 << 15;
}
