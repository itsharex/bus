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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则查找器
 *
 * @author Kimi Liu
 * @version 6.2.9
 * @since JDK 1.8+
 */
public class PatternFinder extends TextFinder {

    private static final long serialVersionUID = 1L;

    private final Pattern pattern;
    private Matcher matcher;

    /**
     * 构造
     *
     * @param regex           被查找的正则表达式
     * @param caseInsensitive 是否忽略大小写
     */
    public PatternFinder(String regex, boolean caseInsensitive) {
        this(Pattern.compile(regex, caseInsensitive ? Pattern.CASE_INSENSITIVE : 0));
    }

    /**
     * 构造
     *
     * @param pattern 被查找的正则{@link Pattern}
     */
    public PatternFinder(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public TextFinder setText(CharSequence text) {
        this.matcher = pattern.matcher(text);
        return super.setText(text);
    }

    @Override
    public int start(int from) {
        if (matcher.find(from)) {
            return matcher.start();
        }
        return -1;
    }

    @Override
    public int end(int start) {
        return matcher.end();
    }

    @Override
    public PatternFinder reset() {
        this.matcher.reset();
        return this;
    }

}
