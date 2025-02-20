/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org and other contributors.                      *
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
package org.aoju.bus.core.io.reader;

import org.aoju.bus.core.collection.ComputeIterator;
import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.core.toolkit.CharsKit;
import org.aoju.bus.core.toolkit.IoKit;
import org.aoju.bus.core.toolkit.StringKit;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * 行读取器，类似于BufferedInputStream，支持多行转义，规则如下：
 * <ul>
 *     <li>支持'\n'和'\r\n'两种换行符，不支持'\r'换行符</li>
 *     <li>如果想读取转义符，必须定义为'\\'</li>
 *     <li>多行转义后的换行符和空格都会被忽略</li>
 * </ul>
 * 读出后就是{@code a=12}
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class LineReader extends ReaderWrapper implements Iterable<String> {

    /**
     * 构造
     *
     * @param in      {@link InputStream}
     * @param charset 编码
     */
    public LineReader(final InputStream in, final Charset charset) {
        this(IoKit.getReader(in, charset));
    }

    /**
     * 构造
     *
     * @param reader {@link Reader}
     */
    public LineReader(final Reader reader) {
        super(IoKit.toBuffered(reader));
    }

    /**
     * 读取一行
     *
     * @return 内容
     * @throws IOException IO异常
     */
    public String readLine() throws IOException {
        StringBuilder str = null;
        // 换行符前是否为转义符
        boolean precedingBackslash = false;
        int c;
        while ((c = read()) > 0) {
            if (null == str) {
                // 只有有字符的情况下才初始化行，否则为行结束
                str = StringKit.builder(1024);
            }
            if (Symbol.C_BACKSLASH == c) {
                // 转义符转义，行尾需要使用'\'时，使用转义符转义，即`\\`
                if (false == precedingBackslash) {
                    // 转义符，添加标识，但是不加入字符
                    precedingBackslash = true;
                    continue;
                } else {
                    precedingBackslash = false;
                }
            } else {
                if (precedingBackslash) {
                    // 转义模式下，跳过转义符后的所有空白符
                    if (CharsKit.isBlankChar(c)) {
                        continue;
                    }
                    // 遇到普通字符，关闭转义
                    precedingBackslash = false;
                } else if (Symbol.C_LF == c) {
                    // 非转义状态下，表示行的结束
                    // 如果换行符是`\r\n`，删除末尾的`\r`
                    final int lastIndex = str.length() - 1;
                    if (lastIndex >= 0 && Symbol.C_CR == str.charAt(lastIndex)) {
                        str.deleteCharAt(lastIndex);
                    }
                    break;
                }
            }

            str.append((char) c);
        }

        return StringKit.toStringOrNull(str);
    }

    @Override
    public Iterator<String> iterator() {
        return new ComputeIterator<>() {
            @Override
            protected String computeNext() {
                try {
                    return readLine();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}

