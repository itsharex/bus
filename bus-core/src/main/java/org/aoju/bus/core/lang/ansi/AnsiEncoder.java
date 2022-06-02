/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2022 aoju.org and other contributors.                      *
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
package org.aoju.bus.core.lang.ansi;

/**
 * 生成ANSI格式的编码输出
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public abstract class AnsiEncoder {

    private static final String ENCODE_JOIN = ";";
    private static final String ENCODE_START = "\033[";
    private static final String ENCODE_END = "m";
    private static final String RESET = "0;" + AnsiColor.DEFAULT;

    /**
     * 创建ANSI字符串，参数中的{@link AnsiElement}会被转换为编码形式。
     *
     * @param args 节点数组
     * @return ANSI字符串
     */
    public static String encode(Object... args) {
        final StringBuilder sb = new StringBuilder();
        buildEnabled(sb, args);
        return sb.toString();
    }

    /**
     * 追加需要需转义的节点
     *
     * @param sb   {@link StringBuilder}
     * @param args 节点列表
     */
    private static void buildEnabled(StringBuilder sb, Object[] args) {
        boolean writingAnsi = false;
        boolean containsEncoding = false;
        for (Object element : args) {
            if (null == element) {
                continue;
            }
            if (element instanceof AnsiElement) {
                containsEncoding = true;
                if (writingAnsi) {
                    sb.append(ENCODE_JOIN);
                } else {
                    sb.append(ENCODE_START);
                    writingAnsi = true;
                }
            } else {
                if (writingAnsi) {
                    sb.append(ENCODE_END);
                    writingAnsi = false;
                }
            }
            sb.append(element);
        }

        // 恢复默认
        if (containsEncoding) {
            sb.append(writingAnsi ? ENCODE_JOIN : ENCODE_START);
            sb.append(RESET);
            sb.append(ENCODE_END);
        }
    }

}
