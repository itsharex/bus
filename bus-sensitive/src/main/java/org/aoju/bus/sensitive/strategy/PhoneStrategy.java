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
package org.aoju.bus.sensitive.strategy;

import org.aoju.bus.core.lang.Normal;
import org.aoju.bus.core.toolkit.ObjectKit;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.sensitive.Context;
import org.aoju.bus.sensitive.annotation.Shield;
import org.aoju.bus.sensitive.provider.AbstractProvider;

/**
 * 手机号脱敏
 * 脱敏规则：180****1120
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class PhoneStrategy extends AbstractProvider {

    /**
     * 脱敏电话号码
     *
     * @param phone 电话号码
     * @return 结果
     */
    private static String phone(final String phone, final String shadow) {
        final int prefixLength = 3;
        final String middle = StringKit.fill(4, shadow);
        return StringKit.build(phone, middle, prefixLength);
    }

    @Override
    public Object build(Object object, Context context) {
        if (ObjectKit.isEmpty(object)) {
            return null;
        }
        final Shield shield = context.getShield();
        return this.phone(ObjectKit.isNull(object) ? Normal.EMPTY : object.toString(), shield.shadow());
    }

}
