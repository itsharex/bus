/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org mybatis.io and other contributors.           *
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
package org.aoju.bus.mapper.builder;

import jakarta.persistence.Entity;
import org.aoju.bus.core.exception.InternalException;
import org.aoju.bus.mapper.entity.EntityField;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;

/**
 * 类字段工具类
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class FieldBuilder {

    private static final IFieldHelper fieldHelper;

    static {
        String version = System.getProperty("java.version");
        if (version.contains("1.6.") || version.contains("1.7.")) {
            fieldHelper = new Jdk6_7FieldHelper();
        } else {
            fieldHelper = new Jdk8FieldHelper();
        }
    }

    /**
     * 获取全部的Field
     *
     * @param entityClass 实体Class对象
     * @return the list
     */
    public static List<EntityField> getFields(Class<?> entityClass) {
        return fieldHelper.getFields(entityClass);
    }

    /**
     * 获取全部的属性，通过方法名获取
     *
     * @param entityClass 实体Class对象
     * @return the list
     */
    public static List<EntityField> getProperties(Class<?> entityClass) {
        return fieldHelper.getProperties(entityClass);
    }

    /**
     * 获取全部的属性，包含字段和方法
     *
     * @param entityClass 实体Class对象
     * @return the list
     */
    public static List<EntityField> getAll(Class<?> entityClass) {
        List<EntityField> fields = fieldHelper.getFields(entityClass);
        List<EntityField> properties = fieldHelper.getProperties(entityClass);
        // 拼到一起，名字相同的合并
        List<EntityField> all = new ArrayList<>();
        Set<EntityField> usedSet = new HashSet<>();
        for (EntityField field : fields) {
            for (EntityField property : properties) {
                if (!usedSet.contains(property) && field.getName().equals(property.getName())) {
                    field.copyFromPropertyDescriptor(property);
                    usedSet.add(property);
                    break;
                }
            }
            all.add(field);
        }
        for (EntityField property : properties) {
            if (!usedSet.contains(property)) {
                all.add(property);
            }
        }
        return all;
    }

    /**
     * 判断是否已经包含同名的field
     *
     * @param fieldList 字段集合
     * @param filedName 字段名称
     * @return the boolean
     */
    private static boolean containFiled(List<EntityField> fieldList, String filedName) {
        for (EntityField field : fieldList) {
            if (field.getName().equals(filedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Field接口
     */
    interface IFieldHelper {
        /**
         * 获取全部的Field
         *
         * @param entityClass 实体Class对象
         * @return the list
         */
        List<EntityField> getFields(Class<?> entityClass);

        /**
         * 获取全部的属性，通过方法名获取
         *
         * @param entityClass 实体Class对象
         * @return the list
         */
        List<EntityField> getProperties(Class<?> entityClass);
    }

    /**
     * 支持jdk8
     */
    static class Jdk8FieldHelper implements IFieldHelper {
        /**
         * 获取全部的Field
         *
         * @param entityClass 实体Class对象
         * @return the list
         */
        @Override
        public List<EntityField> getFields(Class<?> entityClass) {
            List<EntityField> fields = _getFields(entityClass, null, null);
            fields = new ArrayList<>(new LinkedHashSet<>(fields));
            List<EntityField> properties = getProperties(entityClass);
            Set<EntityField> usedSet = new HashSet<>();
            for (EntityField field : fields) {
                for (EntityField property : properties) {
                    if (!usedSet.contains(property) && field.getName().equals(property.getName())) {
                        // 泛型的情况下通过属性可以得到实际的类型
                        field.setJavaType(property.getJavaType());
                        break;
                    }
                }
            }
            return fields;
        }

        /**
         * 获取全部的Field，仅仅通过Field获取
         *
         * @param entityClass 实体Class对象
         * @param fieldList   字段集合
         * @param level       级别
         * @return
         */
        private List<EntityField> _getFields(Class<?> entityClass, List<EntityField> fieldList, Integer level) {
            if (fieldList == null) {
                fieldList = new ArrayList<>();
            }
            if (level == null) {
                level = 0;
            }
            if (entityClass.equals(Object.class)) {
                return fieldList;
            }
            Field[] fields = entityClass.getDeclaredFields();
            int index = 0;
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                // 排除静态字段，解决bug#2
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    // 如果父类中包含与子类同名field，则跳过处理，允许子类进行覆盖
                    if (FieldBuilder.containFiled(fieldList, field.getName())) {
                        continue;
                    }
                    if (level.intValue() != 0) {
                        // 将父类的字段放在前面
                        fieldList.add(index, new EntityField(field, null));
                        index++;
                    } else {
                        fieldList.add(new EntityField(field, null));
                    }
                }
            }
            Class<?> superClass = entityClass.getSuperclass();
            if (superClass != null
                    && !superClass.equals(Object.class)
                    && (superClass.isAnnotationPresent(Entity.class)
                    || (!Map.class.isAssignableFrom(superClass)
                    && !Collection.class.isAssignableFrom(superClass)))) {
                return _getFields(entityClass.getSuperclass(), fieldList, ++level);
            }
            return fieldList;
        }

        /**
         * 通过方法获取属性
         *
         * @param entityClass 实体Class对象
         * @return the list
         */
        @Override
        public List<EntityField> getProperties(Class<?> entityClass) {
            List<EntityField> entityFields = new ArrayList<>();
            BeanInfo beanInfo;
            try {
                beanInfo = Introspector.getBeanInfo(entityClass);
            } catch (IntrospectionException e) {
                throw new InternalException(e);
            }
            PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor desc : descriptors) {
                if (!"class".equals(desc.getName())) {
                    entityFields.add(new EntityField(null, desc));
                }
            }
            return entityFields;
        }
    }

    /**
     * 支持jdk6,7
     */
    static class Jdk6_7FieldHelper implements IFieldHelper {

        @Override
        public List<EntityField> getFields(Class<?> entityClass) {
            List<EntityField> fieldList = new ArrayList<>();
            _getFields(entityClass, fieldList, _getGenericTypeMap(entityClass), null);
            return fieldList;
        }

        /**
         * 通过方法获取属性
         *
         * @param entityClass 实体Class对象
         * @return the list
         */
        @Override
        public List<EntityField> getProperties(Class<?> entityClass) {
            Map<String, Class<?>> genericMap = _getGenericTypeMap(entityClass);
            List<EntityField> entityFields = new ArrayList<>();
            BeanInfo beanInfo;
            try {
                beanInfo = Introspector.getBeanInfo(entityClass);
            } catch (IntrospectionException e) {
                throw new InternalException(e);
            }
            PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor desc : descriptors) {
                if (desc != null && !"class".equals(desc.getName())) {
                    EntityField entityField = new EntityField(null, desc);
                    if (desc.getReadMethod() != null
                            && desc.getReadMethod().getGenericReturnType() != null
                            && desc.getReadMethod().getGenericReturnType() instanceof TypeVariable) {
                        entityField.setJavaType(genericMap.get(((TypeVariable) desc.getReadMethod().getGenericReturnType()).getName()));
                    } else if (desc.getWriteMethod() != null
                            && desc.getWriteMethod().getGenericParameterTypes() != null
                            && desc.getWriteMethod().getGenericParameterTypes().length == 1
                            && desc.getWriteMethod().getGenericParameterTypes()[0] instanceof TypeVariable) {
                        entityField.setJavaType(genericMap.get(((TypeVariable) desc.getWriteMethod().getGenericParameterTypes()[0]).getName()));
                    }
                    entityFields.add(entityField);
                }
            }
            return entityFields;
        }

        /**
         * @param entityClass 实体Class对象
         * @param fieldList   字段集合
         * @param genericMap  类型
         * @param level       级别
         */
        private void _getFields(Class<?> entityClass, List<EntityField> fieldList, Map<String, Class<?>> genericMap, Integer level) {
            if (fieldList == null) {
                throw new NullPointerException("fieldList参数不能为空!");
            }
            if (level == null) {
                level = 0;
            }
            if (entityClass == Object.class) {
                return;
            }
            Field[] fields = entityClass.getDeclaredFields();
            int index = 0;
            for (Field field : fields) {
                // 忽略static和transient字段#106
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    EntityField entityField = new EntityField(field, null);
                    if (field.getGenericType() != null && field.getGenericType() instanceof TypeVariable) {
                        if (genericMap == null || !genericMap.containsKey(((TypeVariable) field.getGenericType()).getName())) {
                            throw new InternalException(entityClass + "字段" + field.getName() + "的泛型类型无法获取!");
                        } else {
                            entityField.setJavaType(genericMap.get(((TypeVariable) field.getGenericType()).getName()));
                        }
                    } else {
                        entityField.setJavaType(field.getType());
                    }
                    // 如果父类中包含与子类同名field，则跳过处理，允许子类进行覆盖
                    if (FieldBuilder.containFiled(fieldList, field.getName())) {
                        continue;
                    }
                    if (level.intValue() != 0) {
                        // 将父类的字段放在前面
                        fieldList.add(index, entityField);
                        index++;
                    } else {
                        fieldList.add(entityField);
                    }
                }
            }
            // 获取父类和泛型信息
            Class<?> superClass = entityClass.getSuperclass();
            // 判断superClass
            if (superClass != null
                    && !superClass.equals(Object.class)
                    && (superClass.isAnnotationPresent(Entity.class)
                    || (!Map.class.isAssignableFrom(superClass)
                    && !Collection.class.isAssignableFrom(superClass)))) {
                level++;
                _getFields(superClass, fieldList, genericMap, level);
            }
        }

        /**
         * 获取所有泛型类型映射
         *
         * @param entityClass 实体Class对象
         */
        private Map<String, Class<?>> _getGenericTypeMap(Class<?> entityClass) {
            Map<String, Class<?>> genericMap = new HashMap<>();
            if (entityClass == Object.class) {
                return genericMap;
            }
            // 获取父类和泛型信息
            Class<?> superClass = entityClass.getSuperclass();
            // 判断superClass
            if (superClass != null
                    && !superClass.equals(Object.class)
                    && (superClass.isAnnotationPresent(Entity.class)
                    || (!Map.class.isAssignableFrom(superClass)
                    && !Collection.class.isAssignableFrom(superClass)))) {
                if (entityClass.getGenericSuperclass() instanceof ParameterizedType) {
                    Type[] types = ((ParameterizedType) entityClass.getGenericSuperclass()).getActualTypeArguments();
                    TypeVariable[] typeVariables = superClass.getTypeParameters();
                    if (typeVariables.length > 0) {
                        for (int i = 0; i < typeVariables.length; i++) {
                            if (types[i] instanceof Class) {
                                genericMap.put(typeVariables[i].getName(), (Class<?>) types[i]);
                            }
                        }
                    }
                }
                genericMap.putAll(_getGenericTypeMap(superClass));
            }
            return genericMap;
        }
    }

}
