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
package org.aoju.bus.core.date.calendar;

import lombok.Data;
import org.aoju.bus.core.toolkit.DateKit;

import java.io.Serializable;
import java.util.List;

/**
 * 年/Year
 *
 * @author Kimi Liu
 * @since Java 17+
 */
@Data
public class YearWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 年
     */
    private int year;

    /**
     * 当前年所有月
     */
    private List<MonthWrapper> months;

    /**
     * 是否闰月
     */
    private boolean isLeapYear;

    /**
     * 当前年包含的天数
     */
    private int length;

    public YearWrapper(int year, List<MonthWrapper> months) {
        super();
        this.year = year;
        this.months = months;
        this.isLeapYear = DateKit.isLeapYear(year);
        if (isLeapYear) {
            this.length = 366;
        } else {
            this.length = 365;
        }
    }

}
