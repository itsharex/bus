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
package org.aoju.bus.cron.pattern;

import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.core.lang.exception.InstrumentException;
import org.aoju.bus.core.toolkit.DateKit;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.cron.pattern.matcher.AlwaysTrueValueMatcher;
import org.aoju.bus.cron.pattern.matcher.DayOfMonthValueMatcher;
import org.aoju.bus.cron.pattern.matcher.ValueMatcher;
import org.aoju.bus.cron.pattern.matcher.ValueMatcherBuilder;
import org.aoju.bus.cron.pattern.parser.*;

import java.util.*;

/**
 * 定时任务表达式
 * 表达式类似于Linux的crontab表达式,表达式使用空格分成5个部分,按顺序依次为：
 * <ol>
 * <li><strong>分</strong>：范围：0~59</li>
 * <li><strong>时</strong>：范围：0~23</li>
 * <li><strong>日</strong>：范围：1~31,<strong>"L"</strong>表示月的最后一天</li>
 * <li><strong>月</strong>：范围：1~12,同时支持不区分大小写的别名："jan","feb", "mar", "apr", "may","jun", "jul", "aug", "sep","oct", "nov", "dec"</li>
 * <li><strong>周</strong>：范围：0 (Sunday)~6(Saturday),7也可以表示周日,同时支持不区分大小写的别名："sun","mon", "tue", "wed", "thu","fri", "sat",<strong>"L"</strong>表示周六</li>
 * </ol>
 * <p>
 * 为了兼容Quartz表达式,同时支持6位和7位表达式,其中：
 *
 * <pre>
 * 当为6位时,第一位表示<strong>秒</strong>,范围0~59,但是第一位不做匹配
 * 当为7位时,最后一位表示<strong>年</strong>,范围1970~2099,但是第7位不做解析,也不做匹配
 * </pre>
 * <p>
 * 当定时任务运行到的时间匹配这些表达式后,任务被启动
 * 注意：
 *
 * <pre>
 * 当isMatchSecond为<code>true</code>时才会匹配秒部分
 * 当isMatchYear为<code>true</code>时才会匹配年部分
 * 默认都是关闭的
 * </pre>
 * <p>
 * 对于每一个子表达式,同样支持以下形式：
 * <ul>
 * <li><strong>*</strong>：表示匹配这个位置所有的时间</li>
 * <li><strong>?</strong>：表示匹配这个位置任意的时间(与"*"作用一致)</li>
 * <li><strong>*&#47;2</strong>：表示间隔时间,例如在分上,表示每两分钟,同样*可以使用数字列表代替,逗号分隔</li>
 * <li><strong>2-8</strong>：表示连续区间,例如在分上,表示2,3,4,5,6,7,8分</li>
 * <li><strong>2,3,5,8</strong>：表示列表</li>
 * <li><strong>cronA | cronB</strong>：表示多个定时表达式</li>
 * </ul>
 * 注意：在每一个子表达式中优先级：
 *
 * <pre>
 * 间隔(/) &gt; 区间(-) &gt; 列表(,)
 * </pre>
 * <p>
 * 例如 2,3,6/3中,由于“/”优先级高,因此相当于2,3,(6/3),结果与 2,3,6等价
 *
 * <p>
 * 一些例子：
 * <ul>
 * <li><strong>5 * * * *</strong>：每个点钟的5分执行,00:05,01:05……</li>
 * <li><strong>* * * * *</strong>：每分钟执行</li>
 * <li><strong>* 2 * * * *</strong>：每两分钟执行</li>
 * <li><strong>* 12 * * *</strong>：12点的每分钟执行</li>
 * <li><strong>59 11 * * 1,2</strong>：每周一和周二的11:59执行</li>
 * <li><strong>3-18 5 * * * *</strong>：3~18分,每5分钟执行一次,既0:03, 0:08, 0:13, 0:18, 1:03, 1:08……</li>
 * </ul>
 *
 * @author Kimi Liu
 * @version 6.2.0
 * @since JDK 1.8+
 */
public class CronPattern {

    private static final ValueParser SECOND_VALUE_PARSER = new SecondValueParser();
    private static final ValueParser MINUTE_VALUE_PARSER = new MinuteValueParser();
    private static final ValueParser HOUR_VALUE_PARSER = new HourValueParser();
    private static final ValueParser DAY_OF_MONTH_VALUE_PARSER = new DayOfMonthValueParser();
    private static final ValueParser MONTH_VALUE_PARSER = new MonthValueParser();
    private static final ValueParser DAY_OF_WEEK_VALUE_PARSER = new DayOfWeekValueParser();
    private static final ValueParser YEAR_VALUE_PARSER = new YearValueParser();

    private String pattern;

    /**
     * 秒字段匹配列表
     */
    private List<ValueMatcher> secondMatchers = new ArrayList<>();
    /**
     * 分字段匹配列表
     */
    private List<ValueMatcher> minuteMatchers = new ArrayList<>();
    /**
     * 时字段匹配列表
     */
    private List<ValueMatcher> hourMatchers = new ArrayList<>();
    /**
     * 每月几号字段匹配列表
     */
    private List<ValueMatcher> dayOfMonthMatchers = new ArrayList<>();
    /**
     * 月字段匹配列表
     */
    private List<ValueMatcher> monthMatchers = new ArrayList<>();
    /**
     * 星期字段匹配列表
     */
    private List<ValueMatcher> dayOfWeekMatchers = new ArrayList<>();
    /**
     * 年字段匹配列表
     */
    private List<ValueMatcher> yearMatchers = new ArrayList<>();
    /**
     * 匹配器个数,取决于复合任务表达式中的单一表达式个数
     */
    private int matcherSize;

    /**
     * 构造
     *
     * @param pattern 表达式
     * @see CronPattern
     */
    public CronPattern(String pattern) {
        this.pattern = pattern;
        parseGroupPattern(pattern);
    }

    /**
     * 是否匹配日(指定月份的第几天)
     *
     * @param matcher    {@link ValueMatcher}
     * @param dayOfMonth 日
     * @param month      月
     * @param isLeapYear 是否闰年
     * @return 是否匹配
     */
    private static boolean isMatchDayOfMonth(ValueMatcher matcher, int dayOfMonth, int month, boolean isLeapYear) {
        return ((matcher instanceof DayOfMonthValueMatcher)
                ? ((DayOfMonthValueMatcher) matcher).match(dayOfMonth, month, isLeapYear)
                : matcher.match(dayOfMonth));
    }

    /**
     * 是否匹配指定的日期时间位置
     *
     * @param matchers 匹配器列表
     * @param index    位置
     * @param value    被匹配的值
     * @return 是否匹配
     */
    private static boolean isMatch(List<ValueMatcher> matchers, int index, int value) {
        return (matchers.size() > index) ? matchers.get(index).match(value) : true;
    }

    /**
     * 给定时间是否匹配定时任务表达式
     *
     * @param millis        时间毫秒数
     * @param isMatchSecond 是否匹配秒
     * @return 如果匹配返回 <code>true</code>, 否则返回 <code>false</code>
     */
    public boolean match(long millis, boolean isMatchSecond) {
        return match(TimeZone.getDefault(), millis, isMatchSecond);
    }

    /**
     * 给定时间是否匹配定时任务表达式
     *
     * @param timezone      时区 {@link TimeZone}
     * @param millis        时间毫秒数
     * @param isMatchSecond 是否匹配秒
     * @return 如果匹配返回 <code>true</code>, 否则返回 <code>false</code>
     */
    public boolean match(TimeZone timezone, long millis, boolean isMatchSecond) {
        final GregorianCalendar calendar = new GregorianCalendar(timezone);
        calendar.setTimeInMillis(millis);
        return match(calendar, isMatchSecond);
    }

    /**
     * 给定时间是否匹配定时任务表达式
     *
     * @param calendar      时间
     * @param isMatchSecond 是否匹配秒
     * @return 如果匹配返回 <code>true</code>, 否则返回 <code>false</code>
     */
    public boolean match(GregorianCalendar calendar, boolean isMatchSecond) {
        final int second = calendar.get(Calendar.SECOND);
        final int minute = calendar.get(Calendar.MINUTE);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        final int month = calendar.get(Calendar.MONTH) + 1;// 月份从1开始
        final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1; // 星期从0开始,0和7都表示周日
        final int year = calendar.get(Calendar.YEAR);

        boolean eval;
        for (int i = 0; i < matcherSize; i++) {
            eval = (isMatchSecond ? secondMatchers.get(i).match(second) : true) // 匹配秒(非秒匹配模式下始终返回true)
                    && minuteMatchers.get(i).match(minute)// 匹配分
                    && hourMatchers.get(i).match(hour)// 匹配时
                    && isMatchDayOfMonth(dayOfMonthMatchers.get(i), dayOfMonth, month, calendar.isLeapYear(year))// 匹配日
                    && monthMatchers.get(i).match(month) // 匹配月
                    && dayOfWeekMatchers.get(i).match(dayOfWeek)// 匹配周
                    && isMatch(yearMatchers, i, year);// 匹配年
            if (eval) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return this.pattern;
    }

    /**
     * 解析复合任务表达式
     *
     * @param groupPattern 复合表达式
     */
    private void parseGroupPattern(String groupPattern) {
        List<String> patternList = StringKit.split(groupPattern, Symbol.C_OR);
        for (String pattern : patternList) {
            parseSinglePattern(pattern);
        }
    }

    /**
     * 解析单一定时任务表达式
     *
     * @param pattern 表达式
     */
    private void parseSinglePattern(String pattern) {
        final String[] parts = pattern.split("\\s");

        int offset = 0;// 偏移量用于兼容Quartz表达式,当表达式有6或7项时,第一项为秒
        if (parts.length == 6 || parts.length == 7) {
            offset = 1;
        } else if (parts.length != 5) {
            throw new InstrumentException("RegEx [{}] is invalid, it must be 5-7 parts!", pattern);
        }

        // 秒
        if (1 == offset) {// 支持秒的表达式
            try {
                this.secondMatchers.add(ValueMatcherBuilder.build(parts[0], SECOND_VALUE_PARSER));
            } catch (Exception e) {
                throw new InstrumentException("Invalid pattern [{}], parsing 'second' field error!", pattern);
            }
        } else {// 不支持秒的表达式,则第一位按照表达式生成时间的秒数赋值,表示整分匹配
            this.secondMatchers.add(ValueMatcherBuilder.build(String.valueOf(DateKit.date().second()), SECOND_VALUE_PARSER));
        }
        // 分
        try {
            this.minuteMatchers.add(ValueMatcherBuilder.build(parts[0 + offset], MINUTE_VALUE_PARSER));
        } catch (Exception e) {
            throw new InstrumentException("Invalid pattern [{}], parsing 'minute' field error!", pattern);
        }
        // 小时
        try {
            this.hourMatchers.add(ValueMatcherBuilder.build(parts[1 + offset], HOUR_VALUE_PARSER));
        } catch (Exception e) {
            throw new InstrumentException("Invalid pattern [{}], parsing 'hour' field error!", pattern);
        }
        // 每月第几天
        try {
            this.dayOfMonthMatchers.add(ValueMatcherBuilder.build(parts[2 + offset], DAY_OF_MONTH_VALUE_PARSER));
        } catch (Exception e) {
            throw new InstrumentException("Invalid pattern [{}], parsing 'day of month' field error!", pattern);
        }
        // 月
        try {
            this.monthMatchers.add(ValueMatcherBuilder.build(parts[3 + offset], MONTH_VALUE_PARSER));
        } catch (Exception e) {
            throw new InstrumentException("Invalid pattern [{}], parsing 'month' field error!", pattern);
        }
        // 星期几
        try {
            this.dayOfWeekMatchers.add(ValueMatcherBuilder.build(parts[4 + offset], DAY_OF_WEEK_VALUE_PARSER));
        } catch (Exception e) {
            throw new InstrumentException("Invalid pattern [{}], parsing 'day of week' field error!", pattern);
        }
        // 年
        if (parts.length == 7) {// 支持年的表达式
            try {
                this.yearMatchers.add(ValueMatcherBuilder.build(parts[6], YEAR_VALUE_PARSER));
            } catch (Exception e) {
                throw new InstrumentException("Invalid pattern [{}], parsing 'year' field error!", pattern);
            }
        } else {// 不支持年的表达式,全部匹配
            this.secondMatchers.add(new AlwaysTrueValueMatcher());
        }
        matcherSize++;
    }

}
