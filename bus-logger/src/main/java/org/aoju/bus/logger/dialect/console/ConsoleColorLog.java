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
package org.aoju.bus.logger.dialect.console;

import org.aoju.bus.core.lang.ansi.Ansi4BitColor;
import org.aoju.bus.core.lang.ansi.AnsiEncoder;
import org.aoju.bus.core.toolkit.ClassKit;
import org.aoju.bus.core.toolkit.DateKit;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.logger.level.Level;

import java.util.function.Function;

/**
 * 利用System.out.println()打印彩色日志
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class ConsoleColorLog extends ConsoleLog {

    /**
     * 控制台打印类名的颜色代码
     */
    private static final Ansi4BitColor COLOR_CLASSNAME = Ansi4BitColor.CYAN;

    /**
     * 控制台打印时间的颜色代码
     */
    private static final Ansi4BitColor COLOR_TIME = Ansi4BitColor.WHITE;

    /**
     * 控制台打印正常信息的颜色代码
     */
    private static final Ansi4BitColor COLOR_NONE = Ansi4BitColor.DEFAULT;

    private static Function<Level, Ansi4BitColor> colorFactory = (level -> {
        switch (level) {
            case DEBUG:
            case INFO:
                return Ansi4BitColor.GREEN;
            case WARN:
                return Ansi4BitColor.YELLOW;
            case ERROR:
                return Ansi4BitColor.RED;
            case TRACE:
                return Ansi4BitColor.MAGENTA;
            default:
                return COLOR_NONE;
        }
    });

    /**
     * 构造
     *
     * @param name 类名
     */
    public ConsoleColorLog(String name) {
        super(name);
    }

    /**
     * 构造
     *
     * @param clazz 类
     */
    public ConsoleColorLog(Class<?> clazz) {
        super(clazz);
    }

    /**
     * 设置颜色工厂，根据日志级别，定义不同的颜色
     *
     * @param colorFactory 颜色工厂函数
     */
    public static void setColorFactory(Function<Level, Ansi4BitColor> colorFactory) {
        ConsoleColorLog.colorFactory = colorFactory;
    }

    @Override
    public synchronized void log(String fqcn, Level level, Throwable t, String format, Object... arguments) {
        if (false == isEnabled(level)) {
            return;
        }

        final String template = AnsiEncoder.encode(COLOR_TIME, "[%s]", colorFactory.apply(level), "[%-5s]%s", COLOR_CLASSNAME, "%-30s: ", COLOR_NONE, "%s%n");
        System.out.format(template, DateKit.now(), level.name(), " - ", ClassKit.getShortClassName(getName()), StringKit.format(format, arguments));
    }

}
