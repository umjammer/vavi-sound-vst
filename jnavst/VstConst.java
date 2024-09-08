package org.urish.jnavst;

public interface VstConst {
	int VST_FALSE = 0;
	int VST_TRUE = 1;

	int VST_VERSION_1_0 = 1;
	int VST_VERSION_2_0 = 2;
	int VST_VERSION_2_1 = 2100;
	int VST_VERSION_2_2 = 2200;
	int VST_VERSION_2_3 = 2300;
	int VST_VERSION_2_4 = 2400;

	int VST_MaxProgNameLen = 24;
	int VST_MaxParamStrLen = 8;
	int VST_MaxVendorStrLen = 64;
	int VST_MaxProductStrLen = 64;
	int VST_MaxEffectNameLen = 32;

	int VST_TransportChanged = 1;
	int VST_TransportPlaying = 1 << 1;
	int VST_TransportCycleActive = 1 << 2;
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
}
