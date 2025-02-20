/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org OSHI and other contributors.                 *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.health.unix.aix.drivers;

import org.aoju.bus.core.annotation.ThreadSafe;
import org.aoju.bus.core.lang.tuple.Pair;
import org.aoju.bus.core.lang.tuple.Triple;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Executor;

import java.util.List;

/**
 * Utility to query lscfg
 *
 * @author Kimi Liu
 * @since Java 17+
 */
@ThreadSafe
public final class Lscfg {

    /**
     * Query {@code lscfg -vp} to get all hardware devices
     *
     * @return A list of the output
     */
    public static List<String> queryAllDevices() {
        return Executor.runNative("lscfg -vp");
    }

    /**
     * Parse the output of {@code lscfg -vp} to get backplane info
     *
     * @param lscfg The output of a previous call to {@code lscfg -vp}
     * @return A triplet with backplane model, serial number, and version
     */
    public static Triple<String, String, String> queryBackplaneModelSerialVersion(List<String> lscfg) {
        final String planeMarker = "WAY BACKPLANE";
        final String modelMarker = "Part Number";
        final String serialMarker = "Serial Number";
        final String versionMarker = "Version";
        final String locationMarker = "Physical Location";

        // 1 WAY BACKPLANE :
        // Serial Number...............YL10243490FB
        // Part Number.................80P4315
        // Customer Card ID Number.....26F4
        // CCIN Extender...............1
        // FRU Number.................. 80P4315
        // Version.....................RS6K
        // Hardware Location Code......U0.1-P1
        // Physical Location: U0.1-P1

        String model = null;
        String serialNumber = null;
        String version = null;
        boolean planeFlag = false;
        for (final String checkLine : lscfg) {
            if (!planeFlag && checkLine.contains(planeMarker)) {
                planeFlag = true;
            } else if (planeFlag) {
                if (checkLine.contains(modelMarker)) {
                    model = Builder.removeLeadingDots(checkLine.split(modelMarker)[1].trim());
                } else if (checkLine.contains(serialMarker)) {
                    serialNumber = Builder.removeLeadingDots(checkLine.split(serialMarker)[1].trim());
                } else if (checkLine.contains(versionMarker)) {
                    version = Builder.removeLeadingDots(checkLine.split(versionMarker)[1].trim());
                } else if (checkLine.contains(locationMarker)) {
                    break;
                }
            }
        }
        return Triple.of(model, serialNumber, version);
    }

    /**
     * Query {@code lscfg -vl device} to get hardware info
     *
     * @param device The disk to get the model and serial from
     * @return A pair containing the model and serial number for the device, or null
     * if not found
     */
    public static Pair<String, String> queryModelSerial(String device) {
        String modelMarker = "Machine Type and Model";
        String serialMarker = "Serial Number";
        String model = null;
        String serial = null;
        for (String s : Executor.runNative("lscfg -vl " + device)) {
            // Default model to description at end of first line
            if (model == null && s.contains(device)) {
                String locDesc = s.split(device)[1].trim();
                int idx = locDesc.indexOf(' ');
                if (idx > 0) {
                    model = locDesc.substring(idx).trim();
                }
            }
            if (s.contains(modelMarker)) {
                model = Builder.removeLeadingDots(s.split(modelMarker)[1].trim());
            } else if (s.contains(serialMarker)) {
                serial = Builder.removeLeadingDots(s.split(serialMarker)[1].trim());
            }
        }
        return Pair.of(model, serial);
    }

}
