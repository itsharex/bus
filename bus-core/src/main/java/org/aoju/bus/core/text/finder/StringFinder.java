/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2021 aoju.org and other contributors.                      *
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
package org.aoju.bus.core.text.finder;

import org.aoju.bus.core.lang.Assert;
import org.aoju.bus.core.toolkit.StringKit;

/**
 * 字符查找器
 *
 * @author Kimi Liu
 * @version 6.2.9
 * @since JDK 1.8+
 */
public class StringFinder extends TextFinder {

    private static final long serialVersionUID = 1L;

    private final CharSequence text;
    private final boolean caseInsensitive;

    /**
     * 构造
     *
     * @param text            被查找的字符串
     * @param caseInsensitive 是否忽略大小写
     */
    public StringFinder(CharSequence text, boolean caseInsensitive) {
        Assert.notEmpty(text);
        this.text = text;
        this.caseInsensitive = caseInsensitive;
    }

    @Override
    public int start(int from) {
        Assert.notNull(this.text, "Text to find must be not null!");
        return StringKit.indexOf(this.text, this.text, from, caseInsensitive);
    }

    @Override
    public int end(int start) {
        if (start < 0) {
            return -1;
        }
        return start + this.text.length();
    }

}
