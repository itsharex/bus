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
package org.aoju.bus.proxy.provider;

import org.aoju.bus.core.exception.InternalException;
import org.aoju.bus.proxy.Provider;

import java.lang.reflect.InvocationTargetException;

/**
 * 使用Class..getConstructor().newInstance()来实例化一个对象
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class BeanProvider implements Provider {

    private Class beanClass;

    public BeanProvider() {
    }

    public BeanProvider(Class beanClass) {
        this.beanClass = beanClass;
    }

    public Object getObject() {
        try {
            if (null == beanClass) {
                throw new InternalException("No bean class provided.");
            }
            return beanClass.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new InternalException("Class " + beanClass.getName() + " is not concrete.", e);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new InternalException("Constructor for class " + beanClass.getName() + " is not accessible.",
                    e);
        }
    }

    public void setBeanClass(Class beanClass) {
        this.beanClass = beanClass;
    }

}

