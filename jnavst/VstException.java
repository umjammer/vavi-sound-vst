package org.urish.jnavst;

import java.io.Serial;


class VstException extends RuntimeException {
	@Serial
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
