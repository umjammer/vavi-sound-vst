package org.urish.jnavst;

import com.sun.jna.Library;

public interface VstSharedLibrary extends Library {

	AEffect VSTPluginMain(HostCallback hostCallback);

	AEffect main(HostCallback hostCallback);
}
