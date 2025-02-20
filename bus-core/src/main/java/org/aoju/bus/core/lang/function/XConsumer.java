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
package org.aoju.bus.core.lang.function;

import org.aoju.bus.core.exception.InternalException;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 表示接受单个输入参数且不返回结果的操作
 * 与大多数其他功能接口不同，消费者预计将通过副作用进行操作
 *
 * @param <T> 操作的输入类型
 * @author Kimi Liu
 * @since Java 17+
 */
@FunctionalInterface
public interface XConsumer<T> extends Consumer<T>, Serializable {

    /**
     * multi
     *
     * @param consumers lambda
     * @param <T>       type
     * @return lambda
     */
    @SafeVarargs
    static <T> XConsumer<T> multi(final XConsumer<T>... consumers) {
        return Stream.of(consumers).reduce(XConsumer::andThen).orElseGet(() -> o -> {
        });
    }

    /**
     * 不执行任何操作
     *
     * @param <T> 操作的输入类型
     * @return nothing
     */
    static <T> XConsumer<T> nothing() {
        return t -> {
        };
    }

    /**
     * 对给定参数执行此操作
     *
     * @param t 输入参数
     * @throws Exception 包装的检查异常
     */
    void accepting(T t) throws Exception;

    /**
     * 对给定参数执行此操作
     *
     * @param t 输入参数
     */
    @Override
    default void accept(final T t) {
        try {
            accepting(t);
        } catch (final Exception e) {
            throw new InternalException(e);
        }
    }

    /**
     * 返回一个组合的 {@code Consumer}，它按顺序执行此操作，然后是 {@code after} 操作
     * 如果执行任一操作引发异常，则将其转发给组合操作的调用者
     * 如果执行此操作引发异常，则不会执行 {@code after} 操作
     *
     * @param after 此操作后要执行的操作
     * @return 一个组合的 {@code Consumer} 按顺序执行此操作，然后是 {@code after} 操作
     * @throws NullPointerException 如果 {@code after} 为空
     */
    default XConsumer<T> andThen(final XConsumer<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> {
            accept(t);
            after.accept(t);
        };
    }

}
