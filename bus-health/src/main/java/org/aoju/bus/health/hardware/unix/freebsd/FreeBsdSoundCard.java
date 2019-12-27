/*
 * The MIT License
 *
 * Copyright (c) 2017 aoju.org All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.aoju.bus.health.hardware.unix.freebsd;

import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Command;
import org.aoju.bus.health.hardware.AbstractSoundCard;
import org.aoju.bus.health.hardware.SoundCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FreeBSD soundcard.
 *
 * @author Kimi Liu
 * @version 5.3.9
 * @since JDK 1.8+
 */
public class FreeBsdSoundCard extends AbstractSoundCard {

    private static final String LSHAL = "lshal";

    /**
     * <p>
     * Constructor for FreeBsdSoundCard.
     * </p>
     *
     * @param kernelVersion a {@link java.lang.String} object.
     * @param name          a {@link java.lang.String} object.
     * @param codec         a {@link java.lang.String} object.
     */
    public FreeBsdSoundCard(String kernelVersion, String name, String codec) {
        super(kernelVersion, name, codec);
    }

    /**
     * <p>
     * getSoundCards.
     * </p>
     *
     * @return a {@link java.util.List} object.
     */
    public static List<SoundCard> getSoundCards() {
        Map<String, String> vendorMap = new HashMap<>();
        Map<String, String> productMap = new HashMap<>();
        vendorMap.clear();
        productMap.clear();
        List<String> sounds = new ArrayList<>();
        String key = "";
        for (String line : Command.runNative(LSHAL)) {
            line = line.trim();
            if (line.startsWith("udi =")) {
                // we have the key.
                key = Builder.getSingleQuoteStringValue(line);
            } else if (!key.isEmpty() && !line.isEmpty()) {
                if (line.contains("freebsd.driver =") && "pcm".equals(Builder.getSingleQuoteStringValue(line))) {
                    sounds.add(key);
                } else if (line.contains("info.product")) {
                    productMap.put(key, Builder.getStringBetween(line, Symbol.C_SINGLE_QUOTE));
                } else if (line.contains("info.vendor")) {
                    vendorMap.put(key, Builder.getStringBetween(line, Symbol.C_SINGLE_QUOTE));
                }
            }
        }
        List<SoundCard> soundCards = new ArrayList<>();
        for (String _key : sounds) {
            soundCards.add(new FreeBsdSoundCard(productMap.get(_key), vendorMap.get(_key) + Symbol.SPACE + productMap.get(_key),
                    productMap.get(_key)));
        }
        return soundCards;
    }
}
