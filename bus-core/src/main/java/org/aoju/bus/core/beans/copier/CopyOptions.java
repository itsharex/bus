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
package org.aoju.bus.core.beans.copier;

import org.aoju.bus.core.convert.Convert;
import org.aoju.bus.core.convert.Converter;
import org.aoju.bus.core.lang.function.XFunction;
import org.aoju.bus.core.lang.mutable.MutableEntry;
import org.aoju.bus.core.toolkit.ArrayKit;
import org.aoju.bus.core.toolkit.LambdaKit;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * 属性拷贝选项
 * 包括：
 * 1、限制的类或接口，必须为目标对象的实现接口或父类，用于限制拷贝的属性，例如一个类我只想复制其父类的一些属性，就可以将editable设置为父类
 * 2、是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
 * 3、忽略的属性列表，设置一个属性列表，不拷贝这些属性值
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class CopyOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 限制的类或接口，必须为目标对象的实现接口或父类，用于限制拷贝的属性，例如一个类我只想复制其父类的一些属性，就可以将editable设置为父类
     * 如果目标对象是Map，源对象是Bean，则作用于源对象上
     */
    protected Class<?> editable;
    /**
     * 是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
     */
    protected boolean ignoreNullValue;
    /**
     * 是否忽略字段注入错误
     */
    protected boolean ignoreError;
    /**
     * 是否忽略字段大小写
     */
    protected boolean ignoreCase;
    /**
     * 字段属性名和属性值编辑器，用于自定义属性转换规则（例如驼峰转下划线等），自定义属性值转换规则（例如null转""等）
     */
    protected UnaryOperator<MutableEntry<String, Object>> fieldEditor;
    /**
     * 是否支持transient关键字修饰和@Transient注解，如果支持，被修饰的字段或方法对应的字段将被忽略
     */
    protected boolean transientSupport = true;
    /**
     * 是否覆盖目标值，如果不覆盖，会先读取目标对象的值，为{@code null}则写，否则忽略。如果覆盖，则不判断直接写
     */
    protected boolean override = true;
    /**
     * 自定义类型转换器，默认使用全局万能转换器转换
     */
    protected Converter converter = (type, value) ->
            Convert.convertWithCheck(type, value, null, ignoreError);
    /**
     * 属性过滤器，断言通过的属性才会被复制
     * 断言参数中Field为源对象的字段对象,如果源对象为Map，使用目标对象，Object为源对象的对应值
     */
    private BiPredicate<Field, Object> propertiesFilter;

    /**
     * 构造拷贝选项
     */
    public CopyOptions() {

    }

    /**
     * 构造拷贝选项
     *
     * @param editable         限制的类或接口，必须为目标对象的实现接口或父类，用于限制拷贝的属性
     * @param ignoreNullValue  是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
     * @param ignoreProperties 忽略的目标对象中属性列表，设置一个属性列表，不拷贝这些属性值
     */
    public CopyOptions(final Class<?> editable, final boolean ignoreNullValue, final String... ignoreProperties) {
        this.propertiesFilter = (f, v) -> true;
        this.editable = editable;
        this.ignoreNullValue = ignoreNullValue;
        this.setIgnoreProperties(ignoreProperties);
    }

    /**
     * 创建拷贝选项
     *
     * @return 拷贝选项
     */
    public static CopyOptions of() {
        return new CopyOptions();
    }

    /**
     * 创建拷贝选项
     *
     * @param editable         限制的类或接口，必须为目标对象的实现接口或父类，用于限制拷贝的属性
     * @param ignoreNullValue  是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
     * @param ignoreProperties 忽略的属性列表，设置一个属性列表，不拷贝这些属性值
     * @return 拷贝选项
     */
    public static CopyOptions of(final Class<?> editable, final boolean ignoreNullValue, final String... ignoreProperties) {
        return new CopyOptions(editable, ignoreNullValue, ignoreProperties);
    }

    /**
     * 设置限制的类或接口，必须为目标对象的实现接口或父类，用于限制拷贝的属性
     *
     * @param editable 限制的类或接口
     * @return {@link CopyOptions}
     */
    public CopyOptions setEditable(final Class<?> editable) {
        this.editable = editable;
        return this;
    }

    /**
     * 设置是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
     *
     * @param ignoreNullVall 是否忽略空值，当源对象的值为null时，true: 忽略而不注入此值，false: 注入null
     * @return {@link CopyOptions}
     */
    public CopyOptions setIgnoreNullValue(final boolean ignoreNullVall) {
        this.ignoreNullValue = ignoreNullVall;
        return this;
    }

    /**
     * 设置忽略空值，当源对象的值为null时，忽略而不注入此值
     *
     * @return {@link CopyOptions}
     */
    public CopyOptions ignoreNullValue() {
        return setIgnoreNullValue(true);
    }

    /**
     * 属性过滤器，断言通过的属性才会被复制
     * {@link BiPredicate#test(Object, Object)}返回{@code true}则属性通过，{@code false}不通过，抛弃之
     *
     * @param propertiesFilter 属性过滤器
     * @return {@link CopyOptions}
     */
    public CopyOptions setPropertiesFilter(final BiPredicate<Field, Object> propertiesFilter) {
        this.propertiesFilter = propertiesFilter;
        return this;
    }

    /**
     * 设置忽略的目标对象中属性列表，设置一个属性列表，不拷贝这些属性值
     *
     * @param ignoreProperties 忽略的目标对象中属性列表，设置一个属性列表，不拷贝这些属性值
     * @return {@link CopyOptions}
     */
    public CopyOptions setIgnoreProperties(final String... ignoreProperties) {
        return setPropertiesFilter((field, o) -> !ArrayKit.contains(ignoreProperties, field.getName()));
    }

    /**
     * 设置忽略的目标对象中属性列表，设置一个属性列表，不拷贝这些属性值，Lambda方式
     *
     * @param <P>   参数类型
     * @param <R>   返回值类型
     * @param funcs 忽略的目标对象中属性列表，设置一个属性列表，不拷贝这些属性值
     * @return {@link CopyOptions}
     */
    public <P, R> CopyOptions setIgnoreProperties(final XFunction<P, R>... funcs) {
        final Set<String> ignoreProperties = ArrayKit.mapToSet(funcs, LambdaKit::getFieldName);
        return setPropertiesFilter((field, o) -> !ignoreProperties.contains(field.getName()));
    }

    /**
     * 设置是否忽略字段的注入错误
     *
     * @param ignoreError 是否忽略注入错误
     * @return {@link CopyOptions}
     */
    public CopyOptions setIgnoreError(final boolean ignoreError) {
        this.ignoreError = ignoreError;
        return this;
    }

    /**
     * 设置忽略字段的注入错误
     *
     * @return {@link CopyOptions}
     */
    public CopyOptions ignoreError() {
        return setIgnoreError(true);
    }

    /**
     * 设置是否忽略字段的大小写
     *
     * @param ignoreCase 是否忽略大小写
     * @return {@link CopyOptions}
     */
    public CopyOptions setIgnoreCase(final boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        return this;
    }

    /**
     * 设置忽略字段的大小写
     *
     * @return {@link CopyOptions}
     */
    public CopyOptions ignoreCase() {
        return setIgnoreCase(true);
    }

    /**
     * 设置拷贝属性的字段映射，用于不同的属性之前拷贝做对应表用
     * 需要注意的是，当使用ValueProvider作为数据提供者时，这个映射是相反的，即fieldMapping中key为目标Bean的名称，而value是提供者中的key
     *
     * @param fieldMapping 拷贝属性的字段映射，用于不同的属性之前拷贝做对应表用
     * @return {@link CopyOptions}
     */
    public CopyOptions setFieldMapping(final Map<String, String> fieldMapping) {
        return setFieldEditor(entry -> {
            final String key = entry.getKey();
            entry.setKey(fieldMapping.getOrDefault(key, key));
            return entry;
        });
    }

    /**
     * 设置字段属性编辑器，用于自定义属性转换规则，例如驼峰转下划线等
     * 此转换器只针对源端的字段做转换，请确认转换后与目标端字段一致
     * 当转换后的字段名为null时忽略这个字段
     * 需要注意的是，当使用ValueProvider作为数据提供者时，这个映射是相反的，即参数中key为目标Bean的名称，而返回值是提供者中的key，并且对值的修改无效
     *
     * @param editor 字段属性编辑器，用于自定义属性转换规则，例如驼峰转下划线等
     * @return {@link CopyOptions}
     */
    public CopyOptions setFieldEditor(final UnaryOperator<MutableEntry<String, Object>> editor) {
        this.fieldEditor = editor;
        return this;
    }

    /**
     * 编辑字段值
     *
     * @param fieldName  字段名
     * @param fieldValue 字段值
     * @return 编辑后的字段值
     */
    protected MutableEntry<String, Object> editField(final String fieldName, final Object fieldValue) {
        final MutableEntry<String, Object> entry = new MutableEntry<>(fieldName, fieldValue);
        return (null != this.fieldEditor) ?
                this.fieldEditor.apply(entry) : entry;
    }

    /**
     * 设置是否支持transient关键字修饰和@Transient注解，如果支持，被修饰的字段或方法对应的字段将被忽略。
     *
     * @param transientSupport 是否支持
     * @return this
     */
    public CopyOptions setTransientSupport(final boolean transientSupport) {
        this.transientSupport = transientSupport;
        return this;
    }

    /**
     * 设置是否覆盖目标值，如果不覆盖，会先读取目标对象的值，为{@code null}则写，否则忽略。如果覆盖，则不判断直接写
     *
     * @param override 是否覆盖目标值
     * @return this
     */
    public CopyOptions setOverride(final boolean override) {
        this.override = override;
        return this;
    }

    /**
     * 设置自定义类型转换器，默认使用全局万能转换器转换。
     *
     * @param converter 转换器
     * @return this
     */
    public CopyOptions setConverter(final Converter converter) {
        this.converter = converter;
        return this;
    }

    /**
     * 使用自定义转换器转换字段值
     * 如果自定义转换器为{@code null}，则返回原值。
     *
     * @param targetType 目标类型
     * @param fieldValue 字段值
     * @return 编辑后的字段值
     */
    protected Object convertField(final Type targetType, final Object fieldValue) {
        return (null != this.converter) ?
                this.converter.convert(targetType, fieldValue) : fieldValue;
    }

    /**
     * 测试是否保留字段，{@code true}保留，{@code false}不保留
     *
     * @param field 字段
     * @param value 值
     * @return 是否保留
     */
    protected boolean testPropertyFilter(final Field field, final Object value) {
        return null == this.propertiesFilter || this.propertiesFilter.test(field, value);
    }

}
