package org.urish.jnavst;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public class AEffect extends Structure {
	// 'VstP' big Endian
	public static final long K_EFFECT_MAGIC = 0x56737450;

	/**
	 * Must always be equal to K_EFFECT_MAGIC
	 */
	public int magic;

	public DispatcherCallback dispatcher;

	public ProcessCallback process;

	public SetParameterCallback setParameter;
	public GetParameterCallback getParameter;

	// number of Programs
	public int numPrograms;
	// all programs are assumed to have numParams
	// parameters
	public int numParams;
	// number of Audio Inputs
	public int numInputs;
	// number of Audio Outputs
	public int numOutputs;

	// see constants (Flags Bits)
	public int flags;

	// reserved for Host, must be 0 (Dont use it)
	public int resvd1;
	// reserved for Host, must be 0 (Dont use it)
	public int resvd2;

	// for algorithms which need input in the first place
	public int initialDelay;

	// number of realtime qualities (0: realtime)
	public int realQualities;
	// number of offline qualities (0: realtime only)
	public int offQualities;
	// input samplerate to output samplerate ratio, not used yet
	public float ioRatio;

	public Pointer object;
	// user access
	public Pointer user;

	public int uniqueID;
	public int version;

	public ProcessReplacingCallback processReplacing;

	// all zeroes
	public byte[] future = new byte[60];

	public interface DispatcherCallback extends Callback {
		int callback(AEffect effect, int opCode, int index, int value, Pointer ptr, float opt);
	}

	public interface ProcessCallback extends Callback {
		void callback(AEffect effect, Pointer inputs, Pointer outputs, int sampleFrames);
	}
	
	public interface SetParameterCallback extends Callback {
		void callback(AEffect effect, int index, float parameter);
	}

	public interface GetParameterCallback extends Callback {
		float callback(AEffect effect, int index);
	}

	public interface ProcessReplacingCallback extends Callback {
		void callback(AEffect effect, Pointer inputs, Pointer outputs, int sampleFrames);
	}

	public enum Opcode {
		effOpen(0),
		effClose(1),
		effSetProgram(2),
		effGetProgram(3),
		effSetProgramName(4),
		effGetProgramName(5),
		effGetParamLabel(6),
		effGetParamDisplay(7),
		effGetParamName(8),
		effGetVu(9),
		effSetSampleRate(10),
		effSetBlockSize(11),
		effMainsChanged(12),
		effEditGetRect(13),
		effEditOpen(14),
		effEditClose(15),
		effEditIdle(19),
		effGetChunk(23),
		effSetChunk(24),
		effProcessEvents(25),
		effCanBeAutomated(26),
		effString2Parameter(27),
		effGetProgramNameIndexed(29),
		effGetInputProperties(33),
		effGetOutputProperties(34),
		effGetPlugCategory(35),
		effOfflineNotify(38),
		effOfflinePrepare(39),
		effOfflineRun(40),
		effProcessVarIo(41),
		effSetSpeakerArrangement(42),
		effSetBypass(44),
		effGetEffectName(45),
		effGetVendorString(47),
		effGetProductString(48),
		effGetVendorVersion(49),
		effVendorSpecific(50),
		effCanDo(51),
		effGetTailSize(52),
		effIdle(53),
		effGetParameterProperties(56),
		effGetVstVersion(58),
		effEditKeyDown(59),
		effEditKeyUp(60),
		effSetEditKnobMode(61),
		effGetMidiProgramName(62),
		effGetCurrentMidiProgram(63),
		effGetMidiProgramCategory(64),
		effHasMidiProgramsChanged(65),
		effGetMidiKeyName(66),
		effBeginSetProgram(67),
		effEndSetProgram(68),
		effGetSpeakerArrangement(69),
		effShellGetNextPlugin(70),
		effStartProcess(71),
		effStopProcess(72),
		effSetTotalSampleToProcess(73),
		effSetPanLaw(74),
		effBeginLoadBank(75),
		effBeginLoadProgram(76),
		effSetProcessPrecision(77),
		effGetNumMidiInputChannels(78),
		effGetNumMidiOutputChannels(79);

		public final int code;

		private Opcode(int code) {
			this.code = code;
		}
	}

    protected List<String> getFieldOrder() {
        return Arrays.asList("magic",
                             "dispatcher",
                             "process",
                             "setParameter",
                             "getParameter",
                             "numPrograms",
                             "numParams",
                             "numInputs",
                             "numOutputs",
                             "flags",
                             "resvd1",
                             "resvd2",
                             "initialDelay",
                             "realQualities",
                             "offQualities",
                             "ioRatio",
                             "object",
                             "user",
                             "uniqueID",
                             "version",
                             "processReplacing",
                             "future");
    }
}
