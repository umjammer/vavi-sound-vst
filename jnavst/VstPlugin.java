package org.urish.jnavst;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.urish.jnavst.AEffect.Opcode;

public class VstPlugin {
	private final AEffect effect;

	public VstPlugin(File vstFile) {
		VstSharedLibrary vst = Native.load(vstFile.getAbsolutePath(), VstSharedLibrary.class);
		this.effect = vst.VSTPluginMain(new HostCallback());
		if (effect == null) {
			throw new VstException("VST Plugin Creation Failed, returned null");
		}
		if (effect.magic != AEffect.K_EFFECT_MAGIC) {
			throw new VstException("VST Plugin Creation Failed, returned invalid magic: " + effect.magic);
		}
	}

	private boolean b(long value) {
		return value != VstConst.VST_FALSE;
	}

	private int dispatch(Opcode opcode) {
		return effect.dispatcher.callback(effect, opcode.code, 0, 0, null, 0);
	}

	private int dispatch(Opcode opcode, Pointer ptr) {
		return effect.dispatcher.callback(effect, opcode.code, 0, 0, ptr, 0);
	}

	private int dispatchValue(Opcode opcode, int value) {
		return effect.dispatcher.callback(effect, opcode.code, 0, value, null, 0);
	}

	private int dispatchFloat(Opcode opcode, float value) {
		return effect.dispatcher.callback(effect, opcode.code, 0, 0, null, value);
	}

	private String stringDispatch(Opcode opcode, int maxLength, int index) {
		Pointer name = new Memory(maxLength + 1);
		effect.dispatcher.callback(effect, opcode.code, index, 0, name, 0.f);
		return name.getString(0);
	}

	private String stringDispatch(Opcode opcode, int maxLength) {
		return stringDispatch(opcode, maxLength, 0);
	}

	private boolean booleanDispatch(Opcode opcode) {
		return b(dispatch(opcode));
	}

	private boolean booleanDispatch(Opcode opcode, Pointer pointer) {
		return b(dispatch(opcode, pointer));
	}

	public boolean open() {
		return booleanDispatch(Opcode.effOpen);
	}

	public boolean close() {
		return booleanDispatch(Opcode.effClose);
	}

	public boolean idle() {
		return booleanDispatch(Opcode.effIdle);
	}

	void setProgram(int programIndex) {
		booleanDispatch(Opcode.effBeginSetProgram);
		dispatchValue(Opcode.effSetProgram, programIndex);
		booleanDispatch(Opcode.effEndSetProgram);
	}

	public int getProgram() {
		return dispatch(Opcode.effGetProgram);
	}

	public void setProgramName() {
		// TODO implement
	}

	public String getProgramName() {
		return stringDispatch(Opcode.effGetProgramName, VstConst.VST_MaxProgNameLen);
	}

	public String getProgramName(int index) {
		return stringDispatch(Opcode.effGetProgramName, VstConst.VST_MaxProgNameLen, index);
	}

	public String getParamName(int index) {
		return stringDispatch(Opcode.effGetParamName, VstConst.VST_MaxParamStrLen, index);
	}

	public String getParamDisplay(int index) {
		return stringDispatch(Opcode.effGetParamDisplay, VstConst.VST_MaxParamStrLen, index);
	}

	public String getParamLabel(int index) {
		return stringDispatch(Opcode.effGetParamLabel, VstConst.VST_MaxParamStrLen, index);
	}

	public void setSampleRate(float sampleRate) {
		dispatchFloat(Opcode.effSetSampleRate, sampleRate);
	}

	public void setBlockSize(int blockSize) {
		dispatchValue(Opcode.effSetBlockSize, blockSize);
	}

	public void suspend() {
		dispatchValue(Opcode.effMainsChanged, VstConst.VST_FALSE);
	}

	public void resume() {
		dispatchValue(Opcode.effMainsChanged, VstConst.VST_TRUE);
	}

	public float getVu() {
		return dispatch(Opcode.effGetVu) / (float) 32767.;
	}

	public boolean editOpen(Pointer window) {
		return booleanDispatch(Opcode.effEditOpen, window);
	}

	public boolean editOpen(Component component) {
		return editOpen(Native.getComponentPointer(component));
	}

	public boolean editOpen(String windowTitle) {
		Window frame = new Frame("VST Plugin");
		frame.setSize(400, 400);
		frame.setVisible(true);
		Canvas panel = new Canvas();
		panel.setSize(400, 400);
		panel.setVisible(true);
		frame.add(panel);
		boolean result = editOpen(frame);
		ERect rect = getEditRect();
		System.out.println(rect);
		return result;
	}

	public boolean editClose() {
		return booleanDispatch(Opcode.effEditClose);
	}

	public boolean editIdle() {
		return booleanDispatch(Opcode.effEditIdle);
	}

	public ERect getEditRect() {
		PointerByReference rectPointer = new PointerByReference();
		if (booleanDispatch(Opcode.effEditGetRect, rectPointer.getPointer())) {
			ERect result = new ERect(rectPointer.getValue());
			result.read();
			return result;
		} else {
			return null;
		}
	}

	public int getNumInputs() {
		return effect.numInputs;
	}

	public int getNumOutputs() {
		return effect.numOutputs;
	}

	private Pointer createFloatArray2D(float[][] source, int numFrames) {
		if (source.length == 0) {
			return null;
		}

		int pointerArraySize = source.length * Native.POINTER_SIZE;
		int floatArraySize = numFrames * 4;
		Pointer pointerArray = new Memory(pointerArraySize + (long) floatArraySize * source.length);
		Pointer dataStorage = pointerArray.share(pointerArraySize);
		for (int i = 0; i < source.length; i++) {
			pointerArray.setPointer((long) i * Native.POINTER_SIZE, dataStorage.share((long) floatArraySize * i, floatArraySize));
		}
		return pointerArray;
	}

	public void processReplacing(float[][] inputs, float[][] outputs, int numFrames) {
		assert(inputs.length == getNumInputs());
		assert(outputs.length == getNumOutputs());

		Pointer inputsMemory = createFloatArray2D(inputs, numFrames);
		Pointer outputsMemory = createFloatArray2D(outputs, numFrames);
		if (inputsMemory == null) {
			inputsMemory = outputsMemory;
		}

		for (int i = 0; i < inputs.length; i++) {
			Pointer inputsArray = inputsMemory.getPointer((long) i * Native.POINTER_SIZE);
			inputsArray.write(0, inputs[i], 0, numFrames);
		}
		effect.processReplacing.callback(effect, inputsMemory, outputsMemory, numFrames);
		for (int i = 0; i < outputs.length; i++) {
			Pointer outputArray = outputsMemory.getPointer((long) i * Native.POINTER_SIZE);
			outputArray.read(0, outputs[i], 0, numFrames);
		}
	}

	/**
	 * Returns the name of the VST Plugin
	 */
	public String getName() {
		Pointer name = new Memory(VstConst.VST_MaxEffectNameLen + 1);
		effect.dispatcher.callback(effect, Opcode.effGetEffectName.code, 0, 0, name, 0.f);
		return name.getString(0);
	}

	/**
	 * Returns the name of the VST Plugin Vendor
	 */
	public String getVendorString() {
		Pointer name = new Memory(VstConst.VST_MaxVendorStrLen + 1);
		effect.dispatcher.callback(effect, Opcode.effGetVendorString.code, 0, 0, name, 0.f);
		return name.getString(0);
	}

	/**
	 * Returns the name of the VST Plugin Product
	 */
	public String getProductString() {
		Pointer name = new Memory(VstConst.VST_MaxProductStrLen + 1);
		effect.dispatcher.callback(effect, Opcode.effGetProductString.code, 0, 0, name, 0.f);
		return name.getString(0);
	}
}
