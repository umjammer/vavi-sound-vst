package org.urish.jnavst;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * The whole exported surface of a VST 2.x plug-in: one function, which hands back the
 * {@link AEffect} everything else goes through.
 * <p>
 * The name it goes by depends on how old the plug-in is - {@code VSTPluginMain} since 2.4,
 * {@code main_macho} on older Mach-O builds, plain {@code main} before that - so
 * {@link VstPlugin} looks the symbol up itself rather than binding this interface, which is kept
 * as the statement of the signature.
 */
public interface VstSharedLibrary extends Library {

    /** @param hostCallback the {@code audioMasterCallback}, i.e. a {@link HostCallback} */
    Pointer VSTPluginMain(Callback hostCallback);

    Pointer main_macho(Callback hostCallback);

    Pointer main(Callback hostCallback);
}
