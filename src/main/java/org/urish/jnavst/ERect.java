package org.urish.jnavst;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public class ERect extends Structure {
	public short top;
	public short left;
	public short bottom;
	public short right;

	public ERect() {
		super();
	}

	public ERect(Pointer p) {
		super(p);
	}

	@Override
	public String toString() {
		return "Rect[" + top + ", " + left + ", " + bottom + ", " + right + "]";
	}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("top", "left", "bottom", "right");
    }
}
