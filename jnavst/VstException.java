package org.urish.jnavst;

public class VstException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public VstException() {
		super();
	}

	public VstException(String message, Throwable cause) {
		super(message, cause);
	}

	public VstException(String message) {
		super(message);
	}

	public VstException(Throwable cause) {
		super(cause);
	}
}
