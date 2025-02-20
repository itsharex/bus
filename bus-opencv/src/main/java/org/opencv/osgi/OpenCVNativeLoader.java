package org.opencv.osgi;

import org.opencv.core.Core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is intended to provide a convenient way to load OpenCV's native
 * library from the Java bundle. If Blueprint is enabled in the OSGi container
 * this class will be instantiated automatically and the init() method called
 * loading the native library.
 */
public class OpenCVNativeLoader implements OpenCVInterface {

    public void init() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            Logger.getLogger("org.opencv.osgi").log(Level.INFO, "Successfully loaded OpenCV native library.");
        } catch (Throwable e) {
            Logger.getLogger("org.opencv.osgi").log(Level.SEVERE, "Cannot load OpenCV native library: " + e.getMessage());
        }
    }
}
